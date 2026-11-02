package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.OrderRequestDto;
import bit.bitgroundspring.dto.response.OrderResponseDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.event.OrderCreatedEvent;
import bit.bitgroundspring.repository.*;
import jakarta.transaction.Transactional;
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
    public OrderResponseDto placeOrder(Integer userId, OrderRequestDto req) {
        double qty;
        // 1) 사용자 & 코인 엔티티 로드 (락 모드로 동시성 방어)
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Coin coin = coinRepository.findBySymbol(req.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Coin not found"));

        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("진행 중인 시즌이 없습니다."));
        
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
                throw new IllegalArgumentException("지정가가 시장가에서 너무 벗어났습니다.");
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
            if (user.getCash() < rawTotalPrice) {
                throw new IllegalArgumentException("잔액이 부족합니다.");
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
            if (qty <= 0) {
                throw new IllegalArgumentException("주문 수량은 0보다 커야 합니다.");
            }
            double rawCost = qty * execPrice;
            int cost = (int) Math.floor(rawCost);
            final float EPS = 0.00000001f;
            double currentAmt = asset.getAmount();
            if (currentAmt + EPS < qty) throw new IllegalArgumentException("보유 수량이 부족합니다.");

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
        eventPublisher.publishEvent(new OrderCreatedEvent(this, savedOrder));

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
}
