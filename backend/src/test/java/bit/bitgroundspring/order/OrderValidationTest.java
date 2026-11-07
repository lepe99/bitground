package bit.bitgroundspring.order;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.OrderExecutionService;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시장가·예약 주문의 금액/수량 검증과 체결 시점 재검증
 */
class OrderValidationTest extends IntegrationTest {

    private static final double PRICE = 100_000_000; // BTC 시세 (Mock)
    private static final int CASH = 1_000_000;

    @Autowired
    UserRepository userRepository;
    @Autowired
    UserAssetRepository userAssetRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    TransactionTemplate transactionTemplate;

    private User user;
    private Cookie cookie;
    private Coin btc;
    private Season season;

    @BeforeEach
    void setUp() {
        season = fixtures.activeSeason();
        btc = fixtures.coin("KRW-BTC");
        user = fixtures.user(CASH);
        cookie = fixtures.login(user);
        // Upbit 시세 조회 Mock
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", PRICE))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    private ResultActions marketOrder(String json) throws Exception {
        return mvc.perform(post("/trade").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions reserveOrder(String type, double amount, double price) throws Exception {
        return mvc.perform(post("/orders/reserve").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"KRW-BTC\",\"orderType\":\"%s\",\"amount\":%s,\"reservePrice\":%s}".formatted(type, amount, price)));
    }

    private long cash() {
        return userRepository.findById(user.getId()).orElseThrow().getCash();
    }

    private double btcAmount() {
        return userAssetRepository.findAll().stream()
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .mapToDouble(a -> a.getAmount().doubleValue()).sum();
    }

    @Nested
    @DisplayName("시장가 주문")
    class MarketOrder {

        @Test
        void 지정가를_붙인_시장가_주문은_거부된다_시세보다_싸게_즉시_체결되지_않게() throws Exception {
            // 기존: 시세와 0.5% 안쪽 차이면 그 가격으로 즉시 체결돼 싸게 사고 비싸게 팔기를 반복할 수 있었다
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":1000000,\"reservePrice\":%s}".formatted(PRICE * 0.996))
                    .andExpect(problem(ErrorCode.INVALID_ORDER));
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":1000000}")
                    .andExpect(status().isCreated());
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"SELL\",\"amount\":0.001,\"reservePrice\":%s}".formatted(PRICE * 1.004))
                    .andExpect(problem(ErrorCode.INVALID_ORDER));
            assertThat(orderRepository.findAll()).hasSize(1)
                    .allSatisfy(o -> assertThat(o.getTradePrice()).isEqualByComparingTo(String.valueOf(PRICE)));
        }

        @Test
        void 음수_금액_매수는_거부되고_현금이_늘지_않는다() throws Exception {
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":-1000000}")
                    .andExpect(problem(ErrorCode.INVALID_ORDER));
            assertThat(cash()).isEqualTo(CASH);
        }

        @Test
        void 금액_없는_매수와_음수_수량_매도는_거부된다() throws Exception {
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\"}").andExpect(problem(ErrorCode.INVALID_ORDER));
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"SELL\",\"amount\":-1}").andExpect(problem(ErrorCode.INVALID_ORDER));
        }

        @Test
        void 잔액을_넘는_매수는_422_INSUFFICIENT_CASH() throws Exception {
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":%d}".formatted(CASH + 1))
                    .andExpect(problem(ErrorCode.INSUFFICIENT_CASH));
        }

        @Test
        void 정상_매수는_201이고_현금과_보유수량이_반영된다() throws Exception {
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":500000}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderType").value("BUY"));

            assertThat(cash()).isEqualTo(CASH - 500_000);
            assertThat(btcAmount()).isCloseTo(0.005, within(1e-12));
        }

        @Test
        void 예약에_묶인_현금과_수량은_시장가_주문에_쓸_수_없다() throws Exception {
            reserveOrder("BUY", 0.006, PRICE * 0.5).andExpect(status().isCreated()); // 300,000원 예약
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":800000}")
                    .andExpect(problem(ErrorCode.INSUFFICIENT_CASH));

            fixtures.asset(user, btc, 1.0, PRICE);
            reserveOrder("SELL", 1.0, PRICE * 2).andExpect(status().isCreated());
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"SELL\",\"amount\":1.0}")
                    .andExpect(problem(ErrorCode.INSUFFICIENT_ASSET));
        }
    }

    @Nested
    @DisplayName("예약 주문")
    class ReserveOrder {

        @Test
        void 첫_예약_매수도_잔액을_넘으면_거부된다() throws Exception {
            // 기존 예약이 없으면 합계가 null 이라 잔액 확인을 건너뛰던 버그
            reserveOrder("BUY", 1.0, PRICE * 0.5).andExpect(problem(ErrorCode.INSUFFICIENT_CASH));
        }

        @Test
        void 같은_수량을_두_번_예약_매도할_수_없다() throws Exception {
            fixtures.asset(user, btc, 1.0, PRICE);
            reserveOrder("SELL", 1.0, PRICE * 2).andExpect(status().isCreated());
            reserveOrder("SELL", 1.0, PRICE * 2).andExpect(problem(ErrorCode.INSUFFICIENT_ASSET));
        }

        @Test
        void 접수_응답은_201이고_사용자_정보를_노출하지_않는다() throws Exception {
            reserveOrder("BUY", 0.001, PRICE * 0.5)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").isNumber())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        void 필수_필드가_없으면_400_INVALID_INPUT() throws Exception {
            mvc.perform(post("/orders/reserve").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"KRW-BTC\"}"))
                    .andExpect(problem(ErrorCode.INVALID_INPUT));
        }
    }

    @Nested
    @DisplayName("체결 시점 재검증")
    class Execution {

        private Order pendingOrder(OrderType type, double amount) {
            return orderRepository.save(Order.builder()
                    .user(user).coin(btc).season(season)
                    .orderType(type).amount(BigDecimal.valueOf(amount)).reservePrice(BigDecimal.ONE)
                    .status(Status.PENDING).build());
        }

        private void execute(Order order) {
            transactionTemplate.executeWithoutResult(s -> orderExecutionService.executeOrder(order.getId(), BigDecimal.valueOf(PRICE)));
        }

        @Test
        void 보유_자산이_없는_예약_매도는_취소되고_현금이_지급되지_않는다() {
            Order order = pendingOrder(OrderType.SELL, 0.5);
            execute(order);

            assertThat(orderRepository.findById(order.getId())).isEmpty();
            assertThat(cash()).isEqualTo(CASH);
        }

        @Test
        void 현금이_부족해진_예약_매수는_취소된다() {
            Order order = pendingOrder(OrderType.BUY, 1.0); // 1억 원 필요
            execute(order);

            assertThat(orderRepository.findById(order.getId())).isEmpty();
            assertThat(cash()).isEqualTo(CASH);
        }

        @Test
        void 조건을_만족하면_체결된다() {
            Order order = pendingOrder(OrderType.BUY, 0.001); // 10만 원
            execute(order);

            assertThat(orderRepository.findById(order.getId())).get()
                    .extracting(Order::getStatus).isEqualTo(Status.COMPLETED);
            assertThat(cash()).isEqualTo(CASH - 100_000);
            assertThat(btcAmount()).isCloseTo(0.001, within(1e-12));
        }
    }
}
