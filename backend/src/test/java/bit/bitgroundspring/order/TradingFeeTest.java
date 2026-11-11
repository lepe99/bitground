package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.money.TradingFee;
import bit.bitgroundspring.market.Coin;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.season.SeasonRepository;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 수수료 0.05%: 매수는 대금 + 수수료를 내고, 매도는 대금 − 수수료를 받는다 (원 단위 올림).
 * 낸 수수료는 주문에 남아 거래내역·이번 시즌 합계로 보인다
 */
class TradingFeeTest extends IntegrationTest {

    private static final double PRICE = 100_000_000; // BTC 시세 (Mock)
    private static final int CASH = 1_000_000;

    @Autowired
    UserRepository userRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    SeasonRepository seasonRepository;

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

    @Test
    void 수수료는_대금의_0_05퍼센트를_원_단위로_올린다() {
        assertThat(TradingFee.of(100_000)).isEqualTo(50);
        assertThat(TradingFee.of(100_001)).isEqualTo(51); // 50.0005 → 51
        assertThat(TradingFee.of(1)).isEqualTo(1);
        assertThat(TradingFee.of(0)).isZero();
        assertThat(TradingFee.buyCost(999_500)).isEqualTo(1_000_000);
        assertThat(TradingFee.sellProceeds(100_000)).isEqualTo(99_950);
    }

    @Nested
    class 시장가 {

        @Test
        void 매수는_주문_금액에_수수료를_더해_내고_주문에_수수료가_남는다() throws Exception {
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":500000}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fee").value(250));

            assertThat(cash()).isEqualTo(CASH - 500_000 - 250);
            assertThat(orderRepository.findAll()).singleElement()
                    .satisfies(o -> assertThat(o.getFee()).isEqualTo(250L));
        }

        @Test
        void 현금_전부로_사려면_수수료를_뺀_금액까지만_된다() throws Exception {
            // 999,501원이면 수수료 500원(499.75 올림)까지 1,000,001원이 필요해 1원 모자란다
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":999501}")
                    .andExpect(problem(ErrorCode.INSUFFICIENT_CASH));
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":999500}")
                    .andExpect(status().isCreated());
            assertThat(cash()).isZero();
        }

        @Test
        void 매도는_받는_대금에서_수수료를_뺀다() throws Exception {
            fixtures.asset(user, btc, 0.01, PRICE);
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"SELL\",\"amount\":0.01}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fee").value(500));

            assertThat(cash()).isEqualTo(CASH + 1_000_000 - 500);
        }
    }

    @Nested
    class 지정가 {

        @Test
        void 매수_접수는_수수료까지_낼_수_있어야_하고_그만큼_묶인다() throws Exception {
            // 0.02 BTC × 5,000만 원 = 100만 원: 현금과 같지만 수수료 500원이 모자란다
            reserveOrder("BUY", 0.02, PRICE * 0.5).andExpect(problem(ErrorCode.INSUFFICIENT_CASH));

            reserveOrder("BUY", 0.01, PRICE * 0.5).andExpect(status().isCreated()); // 50만 원 + 수수료 250원 묶임
            mvc.perform(get("/assets/owned").cookie(cookie))
                    .andExpect(jsonPath("$.availableCash").value(CASH - 500_000 - 250));
            // 남은 499,750원으로는 499,750원어치를 수수료 때문에 살 수 없다
            marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":499750}")
                    .andExpect(problem(ErrorCode.INSUFFICIENT_CASH));
        }

        @Test
        void 체결되면_매수는_대금_더하기_수수료_매도는_대금_빼기_수수료() {
            fixtures.asset(user, btc, 0.01, PRICE);
            Order buy = pending(OrderType.BUY, 0.001);
            Order sell = pending(OrderType.SELL, 0.01);
            execute(buy);
            execute(sell);

            // 매수 10만 원 + 50원, 매도 100만 원 − 500원
            assertThat(cash()).isEqualTo(CASH - 100_000 - 50 + 1_000_000 - 500);
            assertThat(orderRepository.findById(buy.getId()).orElseThrow().getFee()).isEqualTo(50L);
            assertThat(orderRepository.findById(sell.getId()).orElseThrow().getFee()).isEqualTo(500L);
        }

        @Test
        void 체결_때_수수료까지_낼_돈이_없으면_취소된다() {
            // 수수료 도입 전에 접수돼 대금만 묶어 둔 주문: 대금 100만 원은 있지만 수수료 500원이 없다
            Order order = pending(OrderType.BUY, 0.01);
            execute(order);

            assertThat(orderRepository.findById(order.getId())).isEmpty();
            assertThat(cash()).isEqualTo(CASH);
        }

        private Order pending(OrderType type, double amount) {
            return orderRepository.save(Order.builder()
                    .user(user).coin(btc).season(season)
                    .orderType(type).amount(BigDecimal.valueOf(amount)).reservePrice(BigDecimal.valueOf(PRICE))
                    .status(Status.PENDING).build());
        }

        private void execute(Order order) {
            transactionTemplate.executeWithoutResult(s -> orderExecutionService.executeOrder(order.getId(), BigDecimal.valueOf(PRICE)));
        }
    }

    @Test
    void 낸_수수료는_거래내역과_이번_시즌_합계로_보인다() throws Exception {
        fixtures.asset(user, btc, 0.01, PRICE);
        marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":100000}").andExpect(status().isCreated());
        marketOrder("{\"symbol\":\"KRW-BTC\",\"orderType\":\"SELL\",\"amount\":0.005}").andExpect(status().isCreated());

        mvc.perform(get("/orders").param("seasonId", String.valueOf(season.getId())).cookie(cookie))
                .andExpect(jsonPath("$[?(@.orderType == 'BUY')].fee").value(50))
                .andExpect(jsonPath("$[?(@.orderType == 'SELL')].fee").value(250));
        mvc.perform(get("/assets/owned").cookie(cookie))
                .andExpect(jsonPath("$.seasonFee").value(300));
    }

    @Test
    void 지난_시즌_수수료는_이번_시즌_합계에_넣지_않는다() throws Exception {
        orderRepository.save(Order.builder()
                .user(user).coin(btc).season(seasonRepository.save(Season.builder().name("지난 시즌")
                        .startAt(LocalDate.now().minusDays(30)).endAt(LocalDate.now().minusDays(16)).status(Status.COMPLETED).build()))
                .orderType(OrderType.BUY).amount(BigDecimal.ONE).tradePrice(BigDecimal.ONE).fee(999L)
                .status(Status.COMPLETED).build());
        mvc.perform(get("/assets/owned").cookie(cookie)).andExpect(jsonPath("$.seasonFee").value(0));
    }
}
