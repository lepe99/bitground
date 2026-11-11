package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.market.Coin;
import bit.bitgroundspring.notification.NotificationRepository;
import bit.bitgroundspring.order.dto.CreateOrderRequest;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약 주문 흐름: 접수 → Redis 대기열 → 시세 매칭 → 체결 스트림 → 체결 → 알림,
 * 그리고 Redis 데이터가 사라지거나 처리 중 서버가 죽었을 때의 복구
 */
class ReservedOrderFlowTest extends IntegrationTest {

    private static final String BUY_KEY = "buy_orders:KRW-BTC";

    @Autowired
    OrderService orderService;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    ReservedOrderBook orderBook;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserAssetRepository userAssetRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Value("${upbit.order.execution.stream}")
    String stream;
    @Value("${upbit.order.execution.group}")
    String group;

    private User user;
    private Coin btc;
    private Season season;

    @BeforeEach
    void setUp() {
        season = fixtures.activeSeason();
        btc = fixtures.coin("KRW-BTC");
        user = fixtures.user(1_000_000);
    }

    private Order reserveBuy(double amount, float price) {
        return orderService.createReserveOrder(user.getId(), request(amount, price));
    }

    private static CreateOrderRequest request(double amount, float price) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setSymbol("KRW-BTC");
        req.setOrderType(OrderType.BUY);
        req.setAmount(amount);
        req.setReservePrice((double) price);
        return req;
    }

    private boolean inBook(Order order) {
        return redis.opsForZSet().score(BUY_KEY, String.valueOf(order.getId())) != null;
    }

    private Status orderStatus(Order order) {
        return orderRepository.findById(order.getId()).map(Order::getStatus).orElse(null);
    }

    @Test
    @DisplayName("예약가에 도달하면 체결되고, 현금·자산이 반영되고 알림이 저장된다 (SSE 미접속이어도)")
    void matchAndExecute() {
        Order order = reserveBuy(0.01, 50_000_000f);
        assertThat(inBook(order)).isTrue();

        // 현재가가 예약가 위에 있으면 체결되지 않음
        assertThat(orderBook.claimMatches("KRW-BTC", 60_000_000)).isZero();

        assertThat(orderBook.claimMatches("KRW-BTC", 49_000_000)).isEqualTo(1);
        assertThat(inBook(order)).isFalse();
        // 같은 시세가 동시에 다시 들어와도 두 번 큐에 넣지 않는다
        assertThat(orderBook.claimMatches("KRW-BTC", 49_000_000)).isZero();

        orderExecutionService.processExecutionQueue();

        await(() -> orderStatus(order) == Status.COMPLETED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCash()).isEqualTo(1_000_000 - 490_000 - 245); // 대금 + 수수료 0.05%
        assertThat(userAssetRepository.findAll()).singleElement().satisfies(a -> assertThat(a.getAmount()).isEqualByComparingTo("0.01"));
        await(() -> notificationRepository.count() == 1);
    }

    @Test
    @DisplayName("큐에 오래 머문 체결 요청도 버리지 않고 체결한다")
    void staleRequestIsStillExecuted() {
        Order order = reserveBuy(0.01, 50_000_000f);
        long tenMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(order.getId()));
        enqueue(order, 49_000_000, tenMinutesAgo);

        orderExecutionService.processExecutionQueue();

        await(() -> orderStatus(order) == Status.COMPLETED);
    }

    @Test
    @DisplayName("한 번에 큐를 비울 때까지 처리하고, 형식이 잘못된 메시지는 건너뛴다")
    void drainsWholeQueue() {
        Order a = reserveBuy(0.001, 50_000_000f);
        Order b = reserveBuy(0.001, 50_000_000f);
        Order c = reserveBuy(0.001, 50_000_000f);
        redis.opsForStream().add(stream, Map.of("garbage", "1:2:3")); // 형식이 다른 메시지

        orderBook.claimMatches("KRW-BTC", 49_000_000);
        orderExecutionService.processExecutionQueue();

        await(() -> orderStatus(a) == Status.COMPLETED && orderStatus(b) == Status.COMPLETED && orderStatus(c) == Status.COMPLETED);
        await(() -> redis.opsForStream().size(stream) == 0);
    }

    @Test
    @DisplayName("주문 저장이 롤백되면 Redis 대기열에도 남지 않는다")
    void rolledBackOrderIsNotInBook() {
        transactionTemplate.executeWithoutResult(tx -> {
            reserveBuy(0.01, 50_000_000f);
            tx.setRollbackOnly();
        });

        assertThat(orderRepository.count()).isZero();
        assertThat(redis.opsForZSet().size(BUY_KEY)).isZero();
    }

    @Test
    @DisplayName("Redis가 비워져도 DB의 PENDING 주문으로 대기열을 다시 만든다")
    void rebuildFromDatabase() {
        Order order = reserveBuy(0.01, 50_000_000f);
        redis.delete(BUY_KEY);
        redis.opsForValue().set("order:999", "legacy");

        assertThat(orderBook.rebuild()).isEqualTo(1);

        assertThat(inBook(order)).isTrue();
        assertThat(redis.opsForZSet().score(BUY_KEY, String.valueOf(order.getId()))).isEqualTo(50_000_000d);
        assertThat(redis.hasKey("order:999")).isFalse();
    }

    @Test
    @DisplayName("취소하면 DB와 대기열에서 모두 빠진다")
    void cancelRemovesFromBook() throws Exception {
        Order order = reserveBuy(0.01, 50_000_000f);
        Cookie cookie = fixtures.login(user);

        mvc.perform(delete("/orders/reserve/" + order.getId()).cookie(cookie)).andExpect(status().isNoContent());

        assertThat(orderRepository.findById(order.getId())).isEmpty();
        assertThat(inBook(order)).isFalse();
    }

    @Test
    @DisplayName("시즌 정산 알림을 받으면 지워진 이전 시즌 주문을 대기열에서 정리한다")
    void seasonUpdateRebuildsBook() throws Exception {
        Order order = reserveBuy(0.01, 50_000_000f);
        orderRepository.deleteById(order.getId()); // 스케줄러가 이전 시즌 미체결 주문을 DB에서 삭제한 상황
        assertThat(inBook(order)).isTrue();

        mvc.perform(post("/seasons/update").header("X-Scheduler-Key", "test-season-key").param("seasonFlag", "season"))
                .andExpect(status().isNoContent());

        assertThat(inBook(order)).isFalse();
    }

    @Test
    @DisplayName("Redis 데이터가 통째로 사라지면 DB의 PENDING 주문으로 대기열을 되살리고, 다음 시세에 체결한다")
    void recoversAfterRedisDataLoss() {
        Order waiting = reserveBuy(0.01, 50_000_000f);
        Order claimed = reserveBuy(0.01, 45_000_000f);
        orderBook.rebuild(); // 기동 시 상태 (표시 키 생성)
        assertThat(orderBook.rebuildIfReset()).isFalse();

        // 체결 대기 중이던 주문까지 함께 사라진 상황 (Redis 재시작, 백업 없음)
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(claimed.getId()));
        enqueue(claimed, 44_000_000, System.currentTimeMillis());
        flushRedis();

        assertThat(orderBook.rebuildIfReset()).isTrue();
        assertThat(inBook(waiting)).isTrue();
        assertThat(inBook(claimed)).isTrue();
        assertThat(orderBook.rebuildIfReset()).isFalse(); // 한 번만 다시 만든다

        orderBook.claimMatches("KRW-BTC", 44_000_000);
        orderExecutionService.processExecutionQueue();
        await(() -> orderStatus(waiting) == Status.COMPLETED && orderStatus(claimed) == Status.COMPLETED);
    }

    @Test
    @DisplayName("체결 요청을 가져간 서버가 확인 전에 죽으면, 잠시 뒤 다른 서버가 넘겨받아 체결한다")
    void reclaimsUnacknowledgedRequest() {
        Order order = reserveBuy(0.01, 50_000_000f);
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(order.getId()));
        orderExecutionService.ensureGroup();
        // 다른 서버가 읽기만 하고 죽음 (확인 없음).
        // 넣기와 읽기를 한 번에 해야 이 서버의 주기 소비자(100ms)가 먼저 가져가지 않는다
        assertThat(enqueueTakenBy("dead-server", order, 49_000_000)).isEqualTo(1);
        assertThat(orderStatus(order)).isEqualTo(Status.PENDING);

        // 넘겨받기 대기 시간(테스트 1초)이 지나면 넘겨받아 체결한다. 고정 대기 대신 될 때까지 다시 시도
        await(() -> {
            orderExecutionService.reclaimStale();
            return orderStatus(order) == Status.COMPLETED;
        });
        await(() -> redis.opsForStream().pending(stream, group).getTotalPendingMessages() == 0);
    }

    @Test
    @DisplayName("같은 체결 요청이 두 번 와도 한 번만 체결한다")
    void duplicateRequestExecutesOnce() {
        Order order = reserveBuy(0.01, 50_000_000f);
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(order.getId()));
        enqueue(order, 49_000_000, System.currentTimeMillis());
        enqueue(order, 48_000_000, System.currentTimeMillis());

        orderExecutionService.processExecutionQueue();

        await(() -> redis.opsForStream().size(stream) == 0);
        assertThat(orderStatus(order)).isEqualTo(Status.COMPLETED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCash()).isEqualTo(1_000_000 - 490_000 - 245); // 대금 + 수수료 0.05%
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getTradePrice()).isEqualByComparingTo("49000000");
    }

    @Test
    @DisplayName("대조: DB엔 PENDING인데 Redis에 없는 주문은 채우고, 끝난 주문은 대기열에서 지운다")
    void reconcileFixesDrift() {
        Order missing = reserveBuy(0.01, 50_000_000f);
        Order inFlight = reserveBuy(0.01, 40_000_000f);
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(missing.getId()));
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(inFlight.getId()));
        enqueue(inFlight, 39_000_000, System.currentTimeMillis()); // 체결 대기 중: 대기열에 없어도 정상
        redis.opsForZSet().add(BUY_KEY, "999999", 1); // DB에 없는 주문

        ReservedOrderBook.ReconcileResult result = orderBook.reconcile();

        assertThat(result.restored()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(inBook(missing)).isTrue();
        assertThat(inBook(inFlight)).isFalse();
        assertThat(redis.opsForZSet().score(BUY_KEY, "999999")).isNull();
    }

    private void enqueue(Order order, double price, long requestedAt) {
        redis.opsForStream().add(stream, Map.of("orderId", String.valueOf(order.getId()),
                "price", String.valueOf(price), "requestedAt", String.valueOf(requestedAt)));
    }

    /** 체결 요청을 넣고 곧바로 consumer가 가져간 상태로 만든다 (원자적). 가져간 개수를 돌려준다 */
    private long enqueueTakenBy(String consumer, Order order, double price) {
        String script = """
                redis.call('XADD', KEYS[1], '*', 'orderId', ARGV[1], 'price', ARGV[2], 'requestedAt', ARGV[3])
                local read = redis.call('XREADGROUP', 'GROUP', ARGV[4], ARGV[5], 'COUNT', 10, 'STREAMS', KEYS[1], '>')
                return #read[1][2]
                """;
        return redis.execute(new DefaultRedisScript<>(script, Long.class), List.of(stream),
                String.valueOf(order.getId()), String.valueOf(price), String.valueOf(System.currentTimeMillis()), group, consumer);
    }

    private void flushRedis() {
        redis.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000; // 전체 테스트 중 부하로 비동기 처리(아웃박스·스트림)가 늦을 수 있다
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("조건이 10초 안에 충족되지 않음");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
