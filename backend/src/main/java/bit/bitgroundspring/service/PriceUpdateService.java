package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.OrderType;
import io.lettuce.core.RedisException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceUpdateService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderExecutionService orderExecutionService;
    
    // 변경: ConcurrentHashMap을 사용하여 심볼별 최신 가격만 저장
    private final ConcurrentMap<String, Double> priceUpdateMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService;
    
    @PostConstruct
    private void init() {
        // 주기적으로 Redis에 데이터를 쓰는 스케줄러 초기화
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(this::flushPricesToRedis, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    @PreDestroy
    private void shutdown() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
    }
    
    // 변경: 큐 대신 Map에 바로 최신 가격을 덮어씀
    @Async("priceUpdateTaskExecutor")
    public CompletableFuture<Void> updatePrice(String symbol, double currentPrice) {
        priceUpdateMap.put(symbol, currentPrice);
        return checkAndExecuteOrdersAsync(symbol, currentPrice);
    }
    
    // 스케줄러에 의해 주기적으로 실행될 메서드
    private void flushPricesToRedis() {
        if (priceUpdateMap.isEmpty()) {
            return;
        }
        
        // 현재 맵의 스냅샷을 만듦
        Map<String, Double> pricesToFlush = new HashMap<>(priceUpdateMap);
        priceUpdateMap.clear(); // 맵을 비워서 다음 데이터를 받을 준비
        
        Map<String, String> redisData = new HashMap<>();
        for (Map.Entry<String, Double> entry : pricesToFlush.entrySet()) {
            redisData.put("price:" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        try {
            // MSET을 사용하여 Redis에 일괄 저장
            redisTemplate.opsForValue().multiSet(redisData);
            log.debug("Flushed {} price updates to Redis.", redisData.size());
        } catch (RedisException e) {
            log.error("Failed to flush prices to Redis", e);
        }
    }
    
    
    @Async("priceUpdateTaskExecutor")
    public CompletableFuture<Void> checkAndExecuteOrdersAsync(String symbol, double currentPrice) {
        try {
            processOrdersForType(symbol, currentPrice, OrderType.BUY);
            processOrdersForType(symbol, currentPrice, OrderType.SELL);
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Error processing orders for symbol: {}", symbol, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    private void processOrdersForType(String symbol, double currentPrice, OrderType orderType) {
        String orderTypeKey = (orderType == OrderType.BUY ? "buy_orders:" : "sell_orders:") + symbol;
        Set<Object> orderIds;
        
        if (orderType == OrderType.SELL) {
            orderIds = redisTemplate.opsForZSet().rangeByScore(orderTypeKey, 0, currentPrice);
        } else {
            orderIds = redisTemplate.opsForZSet().rangeByScore(orderTypeKey, currentPrice, Double.MAX_VALUE);
        }
        
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        
        for (Object orderIdObj : orderIds) {
            try {
                String orderId = orderIdObj.toString();
                
                // 주문 정보가 존재하는지 확인 (객체로 저장된 경우)
                Object orderObj = redisTemplate.opsForValue().get("order:" + orderId);
                if (orderObj != null) {
                    orderExecutionService.queueOrderExecution(Integer.parseInt(orderId), currentPrice);
                    
                    // Redis에서 주문 제거
                    redisTemplate.execute(new SessionCallback<Object>() {
                        @Override
                        public Object execute(RedisOperations operations) throws DataAccessException {
                            operations.multi();
                            operations.opsForZSet().remove(orderTypeKey, orderId);
                            operations.delete("order:" + orderId);
                            return operations.exec();
                        }
                    });
                }
            } catch (NumberFormatException e) {
                log.error("Invalid order ID format: {}", orderIdObj, e);
            } catch (Exception e) {
                log.error("Error processing order {}", orderIdObj, e);
            }
        }
    }
}


