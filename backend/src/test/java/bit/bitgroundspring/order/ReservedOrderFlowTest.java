package bit.bitgroundspring.order;

import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.OrderExecutionService;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.ReservedOrderBook;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.dto.request.CreateOrderRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약 주문 흐름: 접수 → Redis 대기열 → 시세 매칭 → 체결 큐 → 체결 → 알림
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
    @Value("${upbit.order.execution.queue}")
    String queue;

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
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCash()).isEqualTo(1_000_000 - 490_000);
        assertThat(userAssetRepository.findAll()).singleElement().satisfies(a -> assertThat(a.getAmount()).isEqualByComparingTo("0.01"));
        await(() -> notificationRepository.count() == 1);
    }

    @Test
    @DisplayName("큐에 오래 머문 체결 요청도 버리지 않고 체결한다")
    void staleRequestIsStillExecuted() {
        Order order = reserveBuy(0.01, 50_000_000f);
        long tenMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        redis.opsForZSet().remove(BUY_KEY, String.valueOf(order.getId()));
        redis.opsForList().leftPush(queue,
                "{\"orderId\":%d,\"price\":49000000,\"requestedAt\":%d}".formatted(order.getId(), tenMinutesAgo));

        orderExecutionService.processExecutionQueue();

        await(() -> orderStatus(order) == Status.COMPLETED);
    }

    @Test
    @DisplayName("한 번에 큐를 비울 때까지 처리하고, 형식이 잘못된 메시지는 건너뛴다")
    void drainsWholeQueue() {
        Order a = reserveBuy(0.001, 50_000_000f);
        Order b = reserveBuy(0.001, 50_000_000f);
        Order c = reserveBuy(0.001, 50_000_000f);
        redis.opsForList().leftPush(queue, "1:2:3"); // 이전 버전 형식

        orderBook.claimMatches("KRW-BTC", 49_000_000);
        orderExecutionService.processExecutionQueue();

        await(() -> orderStatus(a) == Status.COMPLETED && orderStatus(b) == Status.COMPLETED && orderStatus(c) == Status.COMPLETED);
        assertThat(redis.opsForList().size(queue)).isZero();
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

        mvc.perform(delete("/orders/" + order.getId()).cookie(cookie)).andExpect(status().isNoContent());

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

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("조건이 5초 안에 충족되지 않음");
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
