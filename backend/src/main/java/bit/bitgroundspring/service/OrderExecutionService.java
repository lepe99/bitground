package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.logging.TraceIdFilter;
import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.event.ReserveOrderExecutedEvent;
import bit.bitgroundspring.event.TradeExecutedEvent;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 예약 주문 체결
 *
 * 가격 조건을 만족한 주문은 {@link ReservedOrderBook}이 체결 큐(Redis LIST)에 넣고,
 * 이 서비스가 큐를 비울 때까지 꺼내 주문마다 별도 트랜잭션으로 체결한다.
 * 체결에 실패하면 주문을 대기열에 되돌려 다음 시세에서 다시 체결한다. 오래된 요청도 버리지 않는다.
 */
@Service
@Slf4j
public class OrderExecutionService {

    /** 한 번 깨어날 때 처리하는 최대 건수 (다른 스케줄 작업이 밀리지 않도록) */
    private static final int MAX_BATCH = 500;
    /** 이보다 오래 큐에 머문 요청은 체결 지연으로 기록 */
    private static final long LAG_WARN_MILLIS = 5_000;
    /** 체결 큐 메시지 (가격은 시세 원문 그대로 BigDecimal로 읽음) */
    public record ExecutionRequest(int orderId, BigDecimal price, long requestedAt) {
    }

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;
    private final ReservedOrderBook orderBook;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final String executionQueue;

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public OrderExecutionService(OrderRepository orderRepository, UserRepository userRepository,
                                 UserAssetRepository userAssetRepository, ReservedOrderBook orderBook,
                                 StringRedisTemplate redis, ObjectMapper objectMapper,
                                 TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
                                 @Value("${upbit.order.execution.queue}") String executionQueue) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userAssetRepository = userAssetRepository;
        this.orderBook = orderBook;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.executionQueue = executionQueue;
    }

    @PreDestroy
    public void shutdown() {
        isShuttingDown.set(true);
        log.info("OrderExecutionService shutting down...");
    }

    @Scheduled(fixedDelay = 100)
    public void processExecutionQueue() {
        for (int i = 0; i < MAX_BATCH && !isShuttingDown.get(); i++) {
            String message = redis.opsForList().rightPop(executionQueue);
            if (message == null) {
                return;
            }
            ExecutionRequest request = parse(message);
            if (request != null) {
                process(request);
            }
        }
    }

    private void process(ExecutionRequest request) {
        // 요청이 아닌 스케줄러 작업이므로 주문 단위로 traceId를 붙여 체결 로그를 묶는다
        MDC.put(TraceIdFilter.TRACE_ID, "exec-" + request.orderId());
        try {
            long lag = System.currentTimeMillis() - request.requestedAt();
            if (lag > LAG_WARN_MILLIS) {
                log.warn("Order {} execution delayed {} ms in queue", request.orderId(), lag);
            }
            transactionTemplate.executeWithoutResult(status -> executeOrder(request.orderId(), request.price()));
        } catch (RuntimeException e) {
            // 롤백된 주문은 PENDING 그대로이므로 대기열에 되돌려 다음 시세에서 다시 체결
            log.error("Failed to execute order {}, returned to order book", request.orderId(), e);
            try {
                orderBook.restore(request.orderId());
            } catch (RuntimeException restoreError) {
                log.error("Failed to restore order {} (rebuilt on next startup)", request.orderId(), restoreError);
            }
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    private ExecutionRequest parse(String message) {
        try {
            return objectMapper.readValue(message, ExecutionRequest.class);
        } catch (JsonProcessingException e) {
            // 형식이 다른 메시지(이전 버전 "id:price:ts" 등). 해당 주문은 기동 시 DB에서 대기열로 다시 적재된다
            log.error("Invalid execution message skipped: {}", message);
            return null;
        }
    }

    /**
     * 주문 하나를 체결한다. 호출자의 트랜잭션 안에서 실행되며, 예외가 나면 전부 롤백된다.
     * 주문 행 → 사용자 행 순서로 잠가 취소·시장가 주문과 동시에 처리되지 않게 한다.
     */
    public void executeOrder(int orderId, BigDecimal executionPrice) {
        Optional<Order> orderOpt = orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getStatus() == Status.PENDING);
        if (orderOpt.isEmpty()) {
            log.debug("Order not found or not pending: {}", orderId);
            return;
        }

        Order order = orderOpt.get();
        int userId = order.getUser().getId();
        OrderType orderType = order.getOrderType();
        BigDecimal amount = order.getAmount();
        BigDecimal price = Money.price(executionPrice);
        // 내는 돈은 올림, 받는 돈은 내림 (원 단위)
        long tradeAmount = orderType == OrderType.BUY ? Money.krwCeil(price, amount) : Money.krwFloor(price, amount);

        // 체결 시점에 잔액·보유 수량을 다시 확인
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Optional<UserAsset> userAssetOptional = userAssetRepository.findByUserAndCoinWithLock(user, order.getCoin());
        boolean insufficient = orderType == OrderType.BUY
                ? user.getCash() < tradeAmount
                : userAssetOptional.map(a -> a.getAmount().compareTo(amount) < 0).orElse(true);
        if (insufficient) {
            orderRepository.delete(order);
            log.warn("Order {} cancelled at execution: insufficient {} for user {}",
                    orderId, orderType == OrderType.BUY ? "cash" : "asset", userId);
            return;
        }

        order.setStatus(Status.COMPLETED);
        order.setTradePrice(price);
        order.setUpdatedAt(LocalDateTime.now());

        user.setCash(orderType == OrderType.BUY ? user.getCash() - tradeAmount : user.getCash() + tradeAmount);

        if (userAssetOptional.isPresent()) {
            UserAsset userAsset = userAssetOptional.get();
            if (orderType == OrderType.BUY) {
                userAsset.setAvgPrice(Money.averagePrice(userAsset.getAmount(), userAsset.getAvgPrice(), amount, price));
                userAsset.setAmount(userAsset.getAmount().add(amount));
            } else {
                BigDecimal remainingAmount = userAsset.getAmount().subtract(amount);
                if (remainingAmount.signum() == 0) {
                    userAssetRepository.delete(userAsset);
                } else {
                    userAsset.setAmount(remainingAmount);
                }
            }
        } else if (orderType == OrderType.BUY) {
            userAssetRepository.save(UserAsset.builder()
                    .user(user)
                    .coin(order.getCoin())
                    .amount(amount)
                    .avgPrice(price)
                    .build());
        }

        // 커밋 후에만 리스너가 실행된다 (체결 내역 방송, 사용자 알림)
        Coin coin = order.getCoin();
        eventPublisher.publishEvent(new TradeExecutedEvent(new TradeDto(coin.getSymbol(), coin.getKoreanName(),
                orderType, amount, price, order.getCreatedAt(), order.getUpdatedAt())));
        eventPublisher.publishEvent(new ReserveOrderExecutedEvent(orderId, userId, coin.getSymbol(), orderType,
                amount, price));

        log.info("Order executed: {} at price {} for user {}", orderId, price.toPlainString(), userId);
    }
}
