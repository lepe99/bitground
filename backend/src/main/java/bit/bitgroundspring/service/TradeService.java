package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.dto.request.OrderRequestDto;
import bit.bitgroundspring.dto.response.OrderResponseDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.event.OrderExecutedEvent;
import bit.bitgroundspring.event.OutboxEvents;
import bit.bitgroundspring.repository.*;
import org.springframework.transaction.annotation.Transactional;
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

    /** 슬리피지 허용 범위: 0.5% */
    private static final double SLIPPAGE_TOLERANCE = 0.005;

    @Transactional
    @Loggable("시장가 주문")
    public OrderResponseDto placeOrder(Integer userId, OrderRequestDto req) {
        validateRequest(req);
        tradingWindow.ensureOpen(); // 시즌 종료 시각부터 정산이 끝날 때까지 거래 마감
        BigDecimal qty;
        // 1) 사용자 & 코인 엔티티 로드 (락 모드로 동시성 방어)
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coin coin = coinRepository.findBySymbol(req.getSymbol())
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));

        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
        
        // 2) 현재 시장가 (실시간 시세로 받아 둔 값, 없으면 업비트 REST)
        BigDecimal marketPrice = marketPriceService.currentPrice(req.getSymbol());

        // 3) 주문 종류 분기
        BigDecimal execPrice = marketPrice;
        LocalDateTime now = LocalDateTime.now();

        if (req.getReservePrice() != null) {
            BigDecimal limitPrice = Money.price(Money.of(req.getReservePrice()));
            BigDecimal diffRatio = limitPrice.subtract(marketPrice).abs()
                    .divide(marketPrice, Money.PRICE_SCALE, RoundingMode.HALF_UP);
            if (diffRatio.compareTo(BigDecimal.valueOf(SLIPPAGE_TOLERANCE)) > 0) {
                throw new BusinessException(ErrorCode.PRICE_OUT_OF_RANGE);
            }
            execPrice = limitPrice;
        }

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
        if (req.getReservePrice() != null && !(req.getReservePrice() > 0)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "지정가는 0보다 커야 합니다.");
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
