package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.*;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.dto.response.Message;
import bit.bitgroundspring.dto.response.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.*;
import bit.bitgroundspring.util.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final SeasonRepository seasonRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CoinRepository coinRepository;
    private final UserSseEmitters userSseEmitters;
    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;

    public List<OrderProjection> getOrdersBySeason(Integer seasonId, Integer userId) {
        return orderRepository.findBySeasonIdAndUserId(seasonId, userId);
    }

    public List<TradeDto> getRecentTrades(String symbol) {
        return orderRepository.findTop30ByCoinSymbolAndStatus(symbol, Status.COMPLETED);
    }

    public List<TradeSummaryDto> getTradeSummary(User user, Season season) {
        List<Order> orders = orderRepository.findByUserAndSeason(user, season);

        // 코인 심볼 기준 그룹
        Map<String, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCoin().getSymbol()));

        List<TradeSummaryDto> summaries = new ArrayList<>();

        for (Map.Entry<String, List<Order>> entry : grouped.entrySet()) {
            String coin = entry.getKey();
            List<Order> coinOrders = entry.getValue();

            // 매수/매도 분리
            List<Order> buys = coinOrders.stream()
                    .filter(o -> o.getOrderType().name().equals("BUY"))
                    .toList();
            List<Order> sells = coinOrders.stream()
                    .filter(o -> o.getOrderType().name().equals("SELL"))
                    .toList();

            double buyTotal = buys.stream().mapToDouble(o -> o.getTradePrice() * o.getAmount()).sum();
            double sellTotal = sells.stream().mapToDouble(o -> o.getTradePrice() * o.getAmount()).sum();

            double buyQty = buys.stream().mapToDouble(Order::getAmount).sum();
            double sellQty = sells.stream().mapToDouble(Order::getAmount).sum();

            double avgBuy = buyQty == 0 ? 0 : buyTotal / buyQty;
            double avgSell = sellQty == 0 ? 0 : sellTotal / sellQty;
            double profit = sellTotal - buyTotal;
            String returnRate = buyTotal == 0 ? "0%" :
                    String.format("%+.2f%%", (profit / buyTotal) * 100);

            String buyDate = buys.stream()
                    .map(Order::getCreatedAt)
                    .min(LocalDateTime::compareTo)
                    .map(dt -> String.format("%02d-%02d", dt.getMonthValue(), dt.getDayOfMonth()))
                    .orElse("N/A");

            String koreanName = coinOrders.get(0).getCoin().getKoreanName();

            summaries.add(TradeSummaryDto.builder()
                    .coin(coin)
                    .koreanName(koreanName)
                    .buyDate(buyDate)
                    .buyAmount(buyTotal)
                    .sellAmount(sellTotal)
                    .avgBuy(avgBuy)
                    .avgSell(avgSell)
                    .profit(profit)
                    .returnRate(returnRate)
                    .build());
        }

        return summaries;
    }
    public List<TradeDetailDto> getTradeDetails(User user, Season season) {
        List<Order> orders = orderRepository.findByUserAndSeason(user, season);

        return orders.stream().map(order -> {
            String date = String.format("%02d-%02d",
                    order.getCreatedAt().getMonthValue(),
                    order.getCreatedAt().getDayOfMonth());

            String type = order.getOrderType().name().equals("BUY") ? "매수" : "매도";

            String coin = order.getCoin().getSymbol().replace("KRW-", "");
            String qty = order.getAmount() + " " + coin;
            String koreanName = order.getCoin().getKoreanName();

            double price = order.getTradePrice();
            double total = price * order.getAmount();

            return TradeDetailDto.builder()
                    .date(date)
                    .type(type)
                    .qty(qty)
                    .price(price)
                    .total(total)
                    .koreanName(koreanName)
                    .build();
        }).toList();
    }
    
    
    // 예약 주문 생성 메서드
    @Transactional
    public Order createReserveOrder(CreateOrderRequest request) {
        validateOrderRequest(request);
        
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Coin coin = coinRepository.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Invalid symbol ID"));
        
        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("진행 중인 시즌이 없습니다."));
        
        if (Boolean.TRUE.equals(coin.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot trade deleted symbol");
        }
        
        Order order = Order.builder()
                .user(user)
                .season(season)
                .coin(coin)
                .orderType(request.getOrderType())
                .amount(request.getAmount())
                .reservePrice(request.getReservePrice())
                .status(Status.PENDING)
                .build();
        
        // 주문 유효성 검사
        if (request.getOrderType() == OrderType.BUY) {
            Integer totalReservePrice = orderRepository.calculateTotalReservePriceForBuyOrdersByUserId(user.getId());
            if (totalReservePrice != null && totalReservePrice + request.getAmount() * request.getReservePrice() > user.getCash()) {
                throw new IllegalArgumentException("예약 주문 금액이 잔액을 초과합니다.");
            }
        } else if (request.getOrderType() == OrderType.SELL) {
            Optional<UserAsset> userAsset = userAssetRepository.findByUserAndCoinWithLock(user, coin);
            if (userAsset.isEmpty()) {
                throw new IllegalArgumentException("매도 가능한 자산이 없습니다.");
            }
            if (userAsset.get().getAmount() < request.getAmount()) {
                throw new IllegalArgumentException("매도 가능한 자산이 부족합니다.");
            }
        }
        
        Order savedOrder = orderRepository.save(order);
        saveOrderToRedis(savedOrder, coin.getSymbol());
        
        log.info("Created reserve order: {} for user: {}", savedOrder.getId(), savedOrder.getUser().getId());
        return savedOrder;
    }
    
    private void validateOrderRequest(CreateOrderRequest request) {
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getReservePrice() == null || request.getReservePrice() <= 0) {
            throw new IllegalArgumentException("Reserve price must be positive");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getSymbol() == null) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (request.getOrderType() == null) {
            throw new IllegalArgumentException("Order type is required");
        }
    }
    
    private void saveOrderToRedis(Order order, String symbol) {
        try {
            String orderId = String.valueOf(order.getId());

            OrderRedisDto orderDto = OrderRedisDto.builder()
                    .id(order.getId())
                    .userId(order.getUser().getId())
                    .symbolId(order.getCoin().getId())
                    .symbol(symbol)
                    .orderType(order.getOrderType())
                    .amount(order.getAmount())
                    .reservePrice(order.getReservePrice())
                    .status(order.getStatus())
                    .createdAt(order.getCreatedAt())
                    .build();
            
            // 수정: 파이프라인 사용으로 성능 개선
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForValue().set("order:" + orderId, orderDto, Duration.ofDays(30));

                    String orderTypeKey = order.getOrderType() == OrderType.BUY ?
                            "buy_orders:" + symbol : "sell_orders:" + symbol;
                    operations.opsForZSet().add(orderTypeKey, orderId, order.getReservePrice());
                    operations.expire(orderTypeKey, Duration.ofDays(30));
                    
                    return null;
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to save order to Redis: {}", order.getId(), e);
            // 수정: 예외 발생해도 서비스 계속 진행
        }
    }
    
    public void cancelOrder(Integer orderId, Integer userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        if (!order.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized to cancel this order");
        }
        
        if (order.getStatus() != Status.PENDING) {
            throw new IllegalStateException("Cannot cancel non-pending order");
        }
        
        orderRepository.delete(order);
        
        removeOrderFromRedis(order);
        
        log.info("Cancelled order: {} by user: {}", orderId, userId);
    }
    
    private void removeOrderFromRedis(Order order) {
        try {
            String orderId = String.valueOf(order.getId());
            Integer symbolId = order.getCoin().getId();
            String symbol = coinRepository.findById(symbolId)
                    .map(Coin::getSymbol)
                    .orElseThrow(() -> new IllegalArgumentException("Symbol not found: " + symbolId));
            String orderTypeKey = order.getOrderType() == OrderType.BUY ?
                    "buy_orders:" + symbol : "sell_orders:" + symbol;
            
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    operations.opsForZSet().remove(orderTypeKey, orderId);
                    operations.delete("order:" + orderId);
                    return operations.exec();
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to remove order from Redis: {}", order.getId(), e);
        }
    }

    public void seasonUpdate(String seasonFlag) {
        // 대부분의 로직은 go serverless로 처리, 나머지 것들을 수행하자

        if (seasonFlag.equals("season")) {
            // redis에 저장된 예약 주문 목록 제거
            List<Order> pendingOrders = orderRepository.findByStatus(Status.PENDING);
            if (pendingOrders.isEmpty()) {
                log.info("No pending orders to process for season update");
            }
            for (Order order : pendingOrders) {
                try {
                    removeOrderFromRedis(order);
                    log.info("Removed pending order {} from Redis", order.getId());
                } catch (Exception e) {
                    log.error("Failed to remove pending order {} from Redis: {}", order.getId(), e.getMessage());
                }
            }
        }

        // 사용자에게 시즌 종료 / 스플릿 업데이트 알림 전송
        Season currentSeason = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("진행 중인 시즌이 없습니다."));
        String seasonName = currentSeason.getName();

        Map<String, Object> data = Map.of(
                "seasonName", seasonName,
                "seasonFlag", seasonFlag
        );
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.SEASON_UPDATE)
                .data(data)
                .build();
        userSseEmitters.sendToAll(notificationResponse);
    }

    //미체결
    public List<OrderDto> getPendingOrders(Integer userId, Integer seasonId) {
        List<Order> orders = orderRepository.findPendingOrdersByUserAndSeason(userId, seasonId);
        return orders.stream().map(o ->
                new OrderDto(
                        o.getId(),
                        o.getCoin().getSymbol(),
                        o.getCoin().getKoreanName(),
                        o.getAmount(),
                        o.getTradePrice(),
                        o.getReservePrice(),
                        o.getCreatedAt(),
                        o.getUpdatedAt(),
                        o.getOrderType().name()
                )
        ).toList();
    }

    // 현재 시즌 ID 반환
    public Integer getCurrentSeasonId() {
        return seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("진행 중인 시즌이 없습니다."))
                .getId();
    }


}
