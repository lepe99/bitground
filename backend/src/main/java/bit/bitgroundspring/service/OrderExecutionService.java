package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.logging.TraceIdFilter;
import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.event.OrderExecutedEvent;
import bit.bitgroundspring.event.OutboxEvents;
import bit.bitgroundspring.event.RedisStreamGroup;
import bit.bitgroundspring.common.tx.AfterCommit;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 예약 주문 체결
 *
 * 가격 조건을 만족한 주문은 {@link ReservedOrderBook}이 체결 스트림(Redis Stream)에 넣고,
 * 이 서비스가 구독 그룹으로 읽어 주문마다 별도 트랜잭션으로 체결한 뒤 확인(ack)한다.
 * <ul>
 *   <li>체결 커밋 뒤에 확인하므로, 처리 중 서버가 죽어도 요청이 남아 다른 서버나 재시작한 서버가 다시 처리한다</li>
 *   <li>같은 요청이 두 번 와도 체결 시 주문 행을 잠그고 PENDING인지 확인하므로 한 번만 체결된다</li>
 *   <li>체결에 실패하면 주문을 대기열에 되돌려 다음 시세에서 다시 체결한다. 오래된 요청도 버리지 않는다</li>
 * </ul>
 */
@Service
@Slf4j
public class OrderExecutionService {

    /** 한 번 깨어날 때 처리하는 최대 건수 (다른 스케줄 작업이 밀리지 않도록) */
    private static final int MAX_BATCH = 500;
    /** 이보다 오래 큐에 머문 요청은 체결 지연으로 기록 */
    private static final long LAG_WARN_MILLIS = 5_000;
    /** 한 번에 읽는 요청 수 */
    private static final int READ_COUNT = 100;
    /** 이만큼 넘겨받고도 처리하지 못한 요청은 버린다 (주문은 DB에 남아 대조 작업이 되돌린다) */
    private static final long MAX_DELIVERIES = 5;
    /** 체결 큐 메시지 (가격은 시세 원문 그대로 BigDecimal로 읽음) */
    public record ExecutionRequest(int orderId, BigDecimal price, long requestedAt) {
    }

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;
    private final ReservedOrderBook orderBook;
    private final TransactionTemplate transactionTemplate;
    private final OutboxEvents outbox;
    private final RedisStreamGroup requests;

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final TradingWindow tradingWindow;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingService notificationSettingService;
    private final NotificationService notificationService;

    public OrderExecutionService(OrderRepository orderRepository, UserRepository userRepository,
                                 UserAssetRepository userAssetRepository, ReservedOrderBook orderBook,
                                 StringRedisTemplate redis, TransactionTemplate transactionTemplate,
                                 OutboxEvents outbox, TradingWindow tradingWindow,
                                 NotificationRepository notificationRepository,
                                 NotificationSettingService notificationSettingService,
                                 NotificationService notificationService,
                                 @Value("${upbit.order.execution.stream}") String executionStream,
                                 @Value("${upbit.order.execution.group}") String group,
                                 @Value("${upbit.order.execution.reclaim-idle:30s}") Duration reclaimIdle) {
        this.tradingWindow = tradingWindow;
        this.notificationRepository = notificationRepository;
        this.notificationSettingService = notificationSettingService;
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userAssetRepository = userAssetRepository;
        this.orderBook = orderBook;
        this.transactionTemplate = transactionTemplate;
        this.outbox = outbox;
        // 넘겨받고도 계속 처리하지 못하는 요청은 버린다. 주문은 DB에 PENDING으로 남아 대조 작업이 대기열에 되돌린다
        this.requests = new RedisStreamGroup(redis, executionStream, group, reclaimIdle, MAX_DELIVERIES);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createGroupOnStartup() {
        ensureGroup();
    }

    public void ensureGroup() {
        requests.ensureGroup();
    }

    @PreDestroy
    public void shutdown() {
        isShuttingDown.set(true);
        log.info("OrderExecutionService shutting down...");
    }

    @Scheduled(fixedDelay = 100)
    public void processExecutionQueue() {
        int handled = 0;
        while (handled < MAX_BATCH && !isShuttingDown.get()) {
            List<MapRecord<String, Object, Object>> records = requests.readNew(READ_COUNT);
            if (records.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                if (handle(record)) {
                    requests.finish(record.getId());
                }
            }
            handled += records.size();
        }
    }

    /**
     * 다른 서버(또는 재시작 전의 이 서버)가 가져갔지만 확인하지 못한 체결 요청을 넘겨받아 처리한다.
     * 체결 중 서버가 죽으면 요청이 확인되지 않은 채 남으므로, 일정 시간이 지나면 다시 처리한다
     * (이미 체결됐으면 PENDING이 아니어서 건너뛴다)
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void reclaimStale() {
        if (!isShuttingDown.get()) {
            requests.reclaimStale(MAX_BATCH, this::handle);
        }
    }

    /** 요청 하나를 체결한다. 체결(또는 실패 처리)이 끝나 확인해도 되면 true */
    private boolean handle(MapRecord<String, Object, Object> record) {
        ExecutionRequest request = parse(record.getValue());
        return request == null || process(request);
    }

    /** 체결 요청을 처리한다. 확인(ack)해도 되면 true, 다시 처리해야 하면 false (대기열 복구 실패) */
    private boolean process(ExecutionRequest request) {
        // 요청이 아닌 스케줄러 작업이므로 주문 단위로 traceId를 붙여 체결 로그를 묶는다
        MDC.put(TraceIdFilter.TRACE_ID, "exec-" + request.orderId());
        try {
            // 시즌 종료 시각이 지나면 체결하지 않는다. 남은 예약 주문은 정산이 이전 시즌 미체결 주문으로 지운다
            if (tradingWindow.isClosed()) {
                log.info("Order {} not executed: season settling", request.orderId());
                return true;
            }
            long lag = System.currentTimeMillis() - request.requestedAt();
            if (lag > LAG_WARN_MILLIS) {
                log.warn("Order {} execution delayed {} ms in queue", request.orderId(), lag);
            }
            transactionTemplate.executeWithoutResult(status -> executeOrder(request.orderId(), request.price()));
            return true;
        } catch (RuntimeException e) {
            // 롤백된 주문은 PENDING 그대로이므로 대기열에 되돌려 다음 시세에서 다시 체결
            log.error("Failed to execute order {}, returned to order book", request.orderId(), e);
            try {
                orderBook.restore(request.orderId());
                return true;
            } catch (RuntimeException restoreError) {
                // 확인하지 않고 남겨 두면 잠시 뒤 다시 처리한다 (대조 작업도 대기열에 되돌린다)
                log.error("Failed to restore order {}, request kept for retry", request.orderId(), restoreError);
                return false;
            }
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    /** 체결 순간 잔액·수량이 모자라 취소된 예약 주문을 알린다 (같은 트랜잭션에 저장, 커밋 뒤 실시간 전송) */
    private void notifyCancelled(User user, Order order) {
        if (notificationSettingService.receivers(Message.ORDER_EXECUTION, List.of(user.getId())).isEmpty()) {
            return; // 체결 알림을 끈 사용자
        }
        String message = NotificationMessages.orderCancelled(order.getOrderType(), order.getCoin().getKoreanName(),
                order.getAmount(), order.getReservePrice());
        Notification saved = notificationRepository.save(Notification.builder()
                .user(user)
                .messageType(Message.ORDER_EXECUTION)
                .message(message)
                .build());
        AfterCommit.run(() -> notificationService.push(saved));
    }

    private ExecutionRequest parse(Map<Object, Object> fields) {
        try {
            return new ExecutionRequest(Integer.parseInt(String.valueOf(fields.get("orderId"))),
                    new BigDecimal(String.valueOf(fields.get("price"))),
                    Long.parseLong(String.valueOf(fields.get("requestedAt"))));
        } catch (RuntimeException e) {
            // 형식이 다른 메시지. 해당 주문은 DB에 PENDING으로 남아 대조 작업이 대기열에 되돌린다
            log.error("Invalid execution message skipped: {}", fields);
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
            notifyCancelled(user, order); // 기존: 로그만 남아 사용자는 예약 주문이 사라진 이유를 알 수 없었다
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

        // 체결과 같은 트랜잭션에 이벤트를 남긴다: 커밋되면 반드시 전달된다 (체결 내역 방송, 체결 알림)
        outbox.append(OrderExecutedEvent.of(order, true));

        log.info("Order executed: {} at price {} for user {}", orderId, price.toPlainString(), userId);
    }
}
