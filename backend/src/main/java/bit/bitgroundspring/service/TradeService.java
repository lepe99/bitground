package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.OrderRequestDto;
import bit.bitgroundspring.dto.response.OrderResponseDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.dto.TradeDto;
import bit.bitgroundspring.event.TradeExecutedEvent;
import bit.bitgroundspring.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final UserRepository userRepository;
    private final CoinRepository coinRepository;
    private final UserAssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    /** 슬리피지 허용 범위: 0.5% */
    private static final double SLIPPAGE_TOLERANCE = 0.005;

    @Value("${upbit.api.ticker-url}")
    private String tickerUrl;

    @Transactional
    @Loggable("시장가 주문")
    public OrderResponseDto placeOrder(Integer userId, OrderRequestDto req) {
        validateRequest(req);
        double qty;
        // 1) 사용자 & 코인 엔티티 로드 (락 모드로 동시성 방어)
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coin coin = coinRepository.findBySymbol(req.getSymbol())
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));

        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
        
        // 2) 현재 시장가 조회
        String url = tickerUrl.replace("{symbol}", req.getSymbol());
        ResponseEntity<List<Map<String,Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() {}
        );
        float marketPrice = ((Number)resp.getBody().get(0).get("trade_price")).floatValue();

        // 3) 주문 종류 분기
        boolean isLimitOrder = req.getReservePrice() != null;
        double execPrice = marketPrice;
        LocalDateTime now = LocalDateTime.now();

        if (isLimitOrder) {
            double limitPrice = req.getReservePrice();
            double diffRatio = Math.abs(limitPrice - marketPrice) / marketPrice;
            if (diffRatio > SLIPPAGE_TOLERANCE) {
                throw new BusinessException(ErrorCode.PRICE_OUT_OF_RANGE);
            }
            execPrice = limitPrice;
        }

        boolean isBuy = req.getOrderType() == OrderType.BUY;

        UserAsset asset = assetRepository.findByUserAndCoinWithLock(user, coin)
                .orElseGet(() -> UserAsset.builder()
                        .user(user)
                        .coin(coin)
                        .amount(0d)
                        .avgPrice(0d)
                        .build());

        if (isBuy) {
            int rawTotalPrice = req.getTotalPrice();
            // 예약 매수에 묶인 금액을 제외한 가용 현금 기준으로 확인
            int reservedCash = Optional.ofNullable(
                    orderRepository.calculateTotalReservePriceForBuyOrdersByUserId(userId)).orElse(0);
            if (user.getCash() - reservedCash < rawTotalPrice) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH);
            }
            user.setCash(user.getCash() - rawTotalPrice);
            execPrice = isLimitOrder ? req.getReservePrice() : marketPrice;
            qty = rawTotalPrice / execPrice;
            qty = Math.floor(qty * 1e10) / 1e10;

            double newQty   = asset.getAmount() + qty;
            double newTotal = asset.getAmount() * asset.getAvgPrice() + rawTotalPrice;
            double newAvg   = newTotal / newQty;
            newAvg = Math.floor(newAvg * 1e10) / 1e10;

            asset.setAmount(newQty);
            asset.setAvgPrice(newAvg);

            assetRepository.save(asset);
            userRepository.save(user);

        } else {  // SELL
            qty = req.getAmount();
            double rawCost = qty * execPrice;
            int cost = (int) Math.floor(rawCost);
            final float EPS = 0.00000001f;
            double currentAmt = asset.getAmount();
            // 예약 매도에 묶인 수량을 제외한 가용 수량 기준으로 확인
            double reservedAmt = asset.getId() == null ? 0d
                    : orderRepository.sumPendingSellAmount(userId, coin.getId());
            if (currentAmt - reservedAmt + EPS < qty) throw new BusinessException(ErrorCode.INSUFFICIENT_ASSET);

            user.setCash(user.getCash() + cost);

            double remaining = currentAmt - qty;
            if (remaining < EPS) {
                assetRepository.delete(asset);
            } else {
                asset.setAmount(remaining);
                assetRepository.save(asset);
            }
            userRepository.save(user);
        }

        Order order = Order.builder()
                .user(user)
                .coin(coin)
                .season(season)
                .orderType(req.getOrderType())
                .status(Status.COMPLETED)
                .tradePrice(execPrice)
                .amount(Math.abs(qty))
                .createdAt(now)
                .updatedAt(now)
                .build();
        Order savedOrder = orderRepository.save(order);
        // 커밋 후 실시간 체결 내역으로 방송
        eventPublisher.publishEvent(new TradeExecutedEvent(new TradeDto(coin.getSymbol(), coin.getKoreanName(),
                savedOrder.getOrderType(), savedOrder.getAmount(), savedOrder.getTradePrice(),
                savedOrder.getCreatedAt(), savedOrder.getUpdatedAt())));

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
