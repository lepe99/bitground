package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.event.OutboxRelay;
import bit.bitgroundspring.notification.NotificationRepository;
import bit.bitgroundspring.order.dto.CreateOrderRequest;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이벤트 흐름: 체결과 같은 트랜잭션의 아웃박스 → 도메인 이벤트 스트림 → 처리(알림 저장) → 실시간 스트림.
 * 롤백되면 이벤트가 없고, 같은 이벤트가 다시 와도 알림은 한 번만, Redis 데이터가 사라져도 아웃박스에서 다시 보낸다
 */
class EventPipelineTest extends IntegrationTest {

    @Autowired
    OrderService orderService;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    ReservedOrderBook orderBook;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Value("${events.domain.stream}")
    String domainStream;
    @Value("${events.realtime.stream}")
    String realtimeStream;

    private User user;

    @BeforeEach
    void setUp() {
        fixtures.activeSeason();
        fixtures.coin("KRW-BTC");
        user = fixtures.user(1_000_000);
    }

    private Order reserveAndExecute() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setSymbol("KRW-BTC");
        req.setOrderType(OrderType.BUY);
        req.setAmount(0.01);
        req.setReservePrice(50_000_000d);
        Order order = orderService.createReserveOrder(user.getId(), req);
        orderBook.claimMatches("KRW-BTC", 49_000_000);
        orderExecutionService.processExecutionQueue();
        await(() -> orderRepository.findById(order.getId()).map(Order::getStatus).orElse(null) == Status.COMPLETED);
        return order;
    }

    private long outboxCount(String condition) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE " + condition, Long.class);
        return n == null ? 0 : n;
    }

    private List<MapRecord<String, Object, Object>> realtime(String kind) {
        List<MapRecord<String, Object, Object>> all = redis.opsForStream().range(realtimeStream, Range.unbounded());
        return all == null ? List.of() : all.stream().filter(r -> kind.equals(r.getValue().get("kind"))).toList();
    }

    @Test
    @DisplayName("예약 주문이 체결되면 이벤트가 같은 트랜잭션에 남고, 전달·처리되어 알림이 저장되고 실시간 전송된다")
    void reserveExecutionFlowsThroughOutbox() {
        reserveAndExecute();

        assertThat(outboxCount("event_type = 'ORDER_EXECUTED'")).isEqualTo(1);
        await(() -> outboxCount("published_at IS NOT NULL") == 1);
        await(() -> notificationRepository.count() == 1);
        assertThat(jdbc.queryForObject("SELECT event_id FROM notifications", Long.class)).isNotNull();
        await(() -> realtime("USER").size() == 1 && realtime("TRADE").size() == 1);
        assertThat(realtime("USER").get(0).getValue().get("userId")).isEqualTo(String.valueOf(user.getId()));
        // 토스트 문구는 저장한 알림 문구와 같다 (프론트가 따로 조립하지 않는다)
        String saved = jdbc.queryForObject("SELECT message FROM notifications", String.class);
        assertThat(saved).startsWith("지정가 매수 주문이 체결되었습니다.").endsWith("수수료 245원 · 490,245원 사용");
        String payload = String.valueOf(realtime("USER").get(0).getValue().get("payload"));
        assertThat(payload).contains(saved.replace("\n", "\\n")).contains("\"cash\"").contains("\"id\"");
        await(() -> redis.opsForStream().size(domainStream) == 0); // 처리 후 확인·삭제
    }

    @Test
    @DisplayName("체결 알림을 끈 사용자는 예약 주문이 체결돼도 알림이 저장·전송되지 않는다")
    void mutedOrderExecutionStoresNothing() {
        jdbc.update("INSERT INTO notification_settings (user_id, order_execution) VALUES (?, 0)", user.getId());

        reserveAndExecute();

        await(() -> outboxCount("published_at IS NOT NULL") == 1);
        await(() -> redis.opsForStream().size(domainStream) == 0); // 처리 끝
        assertThat(notificationRepository.count()).isZero();
        assertThat(realtime("USER")).isEmpty();
    }

    @Test
    @DisplayName("시장가 체결도 이벤트로 남아 체결 내역이 방송되고, 알림은 만들지 않는다")
    void marketTradeIsBroadcast() throws Exception {
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", 100_000_000))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        mvc.perform(post("/trade").cookie(fixtures.login(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":100000}"))
                .andExpect(status().isCreated());

        await(() -> realtime("TRADE").size() == 1);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("체결이 롤백되면 이벤트도 남지 않는다")
    void rolledBackExecutionLeavesNoEvent() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setSymbol("KRW-BTC");
        req.setOrderType(OrderType.BUY);
        req.setAmount(0.01);
        req.setReservePrice(50_000_000d);
        Order order = orderService.createReserveOrder(user.getId(), req);

        transactionTemplate.executeWithoutResult(tx -> {
            orderExecutionService.executeOrder(order.getId(), new java.math.BigDecimal("49000000"));
            tx.setRollbackOnly();
        });

        assertThat(outboxCount("1 = 1")).isZero();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    @DisplayName("같은 이벤트가 다시 전달돼도 알림은 한 번만 저장된다")
    void duplicateEventStoresNotificationOnce() {
        reserveAndExecute();
        await(() -> notificationRepository.count() == 1);

        // 전달기가 스트림에 넣은 뒤 표시 전에 죽은 상황: 같은 이벤트를 다시 보냄
        jdbc.update("UPDATE outbox_events SET published_at = NULL");
        outboxRelay.publishPending();

        await(() -> redis.opsForStream().size(domainStream) == 0);
        sleep(300);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 데이터가 사라져 처리되지 못한 이벤트도 아웃박스에서 다시 보내 알림이 저장된다")
    void republishesAfterRedisDataLoss() {
        reserveAndExecute();
        await(() -> notificationRepository.count() == 1);
        // 보냈다고 표시됐지만 처리되기 전에 Redis와 함께 사라진 상황
        jdbc.update("DELETE FROM notifications");
        redis.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });

        assertThat(outboxRelay.republishSince(Duration.ofMinutes(10))).isEqualTo(1);

        await(() -> notificationRepository.count() == 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000; // 전체 테스트 중 부하로 비동기 처리(아웃박스·스트림)가 늦을 수 있다
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("조건이 10초 안에 충족되지 않음");
            }
            sleep(50);
        }
    }
}
