package bit.bitgroundspring.order;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.event.OrderExecutedEvent;
import bit.bitgroundspring.event.OutboxEvents;
import bit.bitgroundspring.market.Coin;
import bit.bitgroundspring.market.CoinRepository;
import bit.bitgroundspring.market.MarketPriceService;
import bit.bitgroundspring.order.dto.OrderRequestDto;
import bit.bitgroundspring.order.dto.OrderResponseDto;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.season.SeasonRepository;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final UserRepository userRepository;
    private final CoinRepository coinRepository;
    private final UserAssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final SeasonRepository seasonRepository;
    private final TradingWindow tradingWindow;
    private final OutboxEvents outbox;
    private final MarketPriceService marketPriceService;
    private final OrderTransactions orderTransactions;

    /**
     * 시장가 주문. 현재가는 트랜잭션·잠금 밖에서 먼저 구한다
     * (기존: 사용자 행을 잠근 채 조회해, 업비트 REST로 넘어가면 응답을 기다리는 동안 그 사용자의 다른 주문·예약 체결이 밀렸다)
     */
    @Loggable("시장가 주문")
    public OrderResponseDto placeOrder(Integer userId, OrderRequestDto req) {
        validateRequest(req);
        tradingWindow.ensureOpen(); // 시즌 종료 시각부터 정산이 끝날 때까지 거래 마감
        ensureTradable(req.getSymbol());
        BigDecimal marketPrice = marketPriceService.currentPrice(req.getSymbol());
        return orderTransactions.execute(() -> execute(userId, req, marketPrice));
    }

    /** 없는 종목이나 상장 폐지된 종목은 사고팔 수 없다 (실제 거래소도 거래지원이 끝나면 매수·매도 모두 막힌다) */
    private void ensureTradable(String symbol) {
        Coin coin = coinRepository.findBySymbol(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));
        if (Boolean.TRUE.equals(coin.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COIN_DELISTED);
        }
    }

    private OrderResponseDto execute(Integer userId, OrderRequestDto req, BigDecimal execPrice) {
        BigDecimal qty;
        // 사용자 행을 잠가 같은 사용자의 주문·예약 체결과 동시에 처리되지 않게 한다
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coin coin = coinRepository.findBySymbol(req.getSymbol())
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));

        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));

        LocalDateTime now = LocalDateTime.now();

        boolean isBuy = req.getOrderType() == OrderType.BUY;

        UserAsset asset = assetRepository.findByUserAndCoinWithLock(user, coin)
                .orElseGet(() -> UserAsset.builder()
                        .user(user)
                        .coin(coin)
                        .amount(BigDecimal.ZERO)
                        .avgPrice(BigDecimal.ZERO)
                        .build());

        if (isBuy) {
            // 주문 금액(원)으로 살 수 있는 수량을 버림으로 계산하고, 평균 매입가에는 실제 낸 금액을 반영
            long totalPrice = req.getTotalPrice();
            // 예약 매수에 묶인 금액을 제외한 가용 현금 기준으로 확인
            if (user.getCash() - orderRepository.reservedBuyCash(userId) < totalPrice) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH);
            }
            qty = Money.quantityFor(totalPrice, execPrice);
            if (qty.signum() == 0) {
                throw new BusinessException(ErrorCode.INVALID_ORDER, "주문 금액이 너무 적습니다.");
            }
            user.setCash(user.getCash() - totalPrice);

            BigDecimal newQty = asset.getAmount().add(qty);
            BigDecimal newCost = asset.getAmount().multiply(asset.getAvgPrice()).add(BigDecimal.valueOf(totalPrice));
            asset.setAmount(newQty);
            asset.setAvgPrice(newCost.divide(newQty, Money.PRICE_SCALE, RoundingMode.HALF_UP));
            assetRepository.save(asset);

        } else {  // SELL
            qty = Money.quantity(Money.of(req.getAmount()));
            BigDecimal currentAmt = asset.getAmount();
            // 예약 매도에 묶인 수량을 제외한 가용 수량 기준으로 확인
            BigDecimal reservedAmt = asset.getId() == null ? BigDecimal.ZERO
                    : orderRepository.sumPendingSellAmount(userId, coin.getId());
            if (qty.signum() == 0 || currentAmt.subtract(reservedAmt).compareTo(qty) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_ASSET);
            }

            // 받는 돈은 원 단위 내림
            user.setCash(user.getCash() + Money.krwFloor(execPrice, qty));

            BigDecimal remaining = currentAmt.subtract(qty);
            if (remaining.signum() == 0) {
                assetRepository.delete(asset);
            } else {
                asset.setAmount(remaining);
            }
        }

        Order order = Order.builder()
                .user(user)
                .coin(coin)
                .season(season)
                .orderType(req.getOrderType())
                .status(Status.COMPLETED)
                .tradePrice(execPrice)
                .amount(qty)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Order savedOrder = orderRepository.save(order);
        // 커밋 후 실시간 체결 내역으로 방송
        outbox.append(OrderExecutedEvent.of(savedOrder, false));

        return new OrderResponseDto(
                order.getId(),
                req.getSymbol(),
                coin.getKoreanName(),
                order.getOrderType(),
                order.getAmount(),
                order.getTradePrice(),
                order.getCreatedAt()
        );
    }

    // 클라이언트 입력값 검증 (음수 금액으로 현금이 늘어나는 등의 조작 방지)
    private void validateRequest(OrderRequestDto req) {
        if (req.getSymbol() == null || req.getOrderType() == null) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "종목과 주문 종류는 필수입니다.");
        }
        // 기존: 지정가를 함께 보내면 시세와 0.5% 안쪽 차이인 그 가격으로 즉시 체결돼,
        // 싸게 사고 비싸게 팔기를 반복해 자산을 늘릴 수 있었다. 지정가 주문은 예약 주문(/orders/reserve)으로만 받는다
        if (req.getReservePrice() != null) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "지정가 주문은 예약 주문으로 넣어 주세요.");
        }
        if (req.getOrderType() == OrderType.BUY) {
            if (req.getTotalPrice() == null || req.getTotalPrice() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ORDER, "주문 금액은 0보다 커야 합니다.");
            }
        } else if (req.getAmount() == null || !(req.getAmount() > 0)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "주문 수량은 0보다 커야 합니다.");
        }
    }
}
