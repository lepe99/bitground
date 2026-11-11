package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.market.MarketPriceService;
import bit.bitgroundspring.order.dto.CreateOrderRequest;
import bit.bitgroundspring.order.dto.OrderRequestDto;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * 서로 다른 사용자가 같은 코인을 동시에 처음 살 때 (부하 테스트에서 시장가 매수가 데드락으로 실패하던 문제)
 *
 * <p>보유 자산 행이 아직 없으면 잠금 조회(SELECT … FOR UPDATE)가 행 대신 유니크 인덱스의 빈 구간을 잠근다(갭 락).
 * 갭 락은 여러 트랜잭션이 함께 가질 수 있어서, 같은 구간을 잠근 두 사용자가 각자 행을 넣으려 하면 서로를 기다리게 된다.
 * 사용자 행 잠금은 같은 사용자끼리만 막으므로 이 경우를 막지 못한다.
 */
class TradeConcurrencyTest extends IntegrationTest {

    private static final int USERS = 16;

    @Autowired
    TradeService tradeService;
    @Autowired
    MarketPriceService marketPriceService;
    @Autowired
    UserAssetRepository userAssetRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderService orderService;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    OrderTransactions orderTransactions;

    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures.activeSeason();
        fixtures.coin("KRW-BTC");
        for (int i = 0; i < USERS; i++) {
            users.add(fixtures.user(1_000_000));
        }
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", 100_000_000))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        marketPriceService.currentPrice("KRW-BTC"); // 시세 조회는 이 테스트의 관심사가 아니므로 미리 받아 둔다
    }

    @Test
    void 여러_사용자가_같은_코인을_동시에_처음_사도_모두_체결된다() throws Exception {
        List<Runnable> actions = new ArrayList<>();
        for (User user : users) {
            actions.add(() -> tradeService.placeOrder(user.getId(), buy(10_000)));
        }

        assertThat(runTogether(actions)).isEmpty();
        assertThat(userAssetRepository.count()).isEqualTo(USERS);
    }

    @Test
    void 시장가_매수와_예약_매수_체결이_동시에_일어나도_모두_체결된다() throws Exception {
        // 절반은 예약 매수를 걸어 두고 체결, 나머지 절반은 같은 순간 시장가 매수
        List<Integer> reserved = new ArrayList<>();
        for (User user : users.subList(0, USERS / 2)) {
            reserved.add(orderService.createReserveOrder(user.getId(),
                    new CreateOrderRequest("KRW-BTC", OrderType.BUY, 0.0001, 100_000_000.0)).getId());
        }
        List<Runnable> actions = new ArrayList<>();
        for (Integer orderId : reserved) {
            // 운영의 체결 처리와 같은 트랜잭션으로 실행
            actions.add(() -> orderTransactions.run(() -> orderExecutionService.executeOrder(orderId, new BigDecimal("100000000"))));
        }
        for (User user : users.subList(USERS / 2, USERS)) {
            actions.add(() -> tradeService.placeOrder(user.getId(), buy(10_000)));
        }

        assertThat(runTogether(actions)).isEmpty();
        assertThat(userAssetRepository.count()).isEqualTo(USERS);
        assertThat(orderRepository.findAll()).allMatch(o -> o.getStatus() == Status.COMPLETED);
    }

    /** 모든 작업을 같은 순간에 시작시키고, 실패한 예외를 모은다 */
    private static List<Throwable> runTogether(List<Runnable> actions) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            for (Runnable action : actions) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.run();
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                });
            }
            ready.await();
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        return failures;
    }

    private static OrderRequestDto buy(long totalPrice) {
        OrderRequestDto req = new OrderRequestDto();
        req.setSymbol("KRW-BTC");
        req.setOrderType(OrderType.BUY);
        req.setTotalPrice(totalPrice);
        return req;
    }
}
