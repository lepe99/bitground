package bit.bitgroundspring.api;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.market.Coin;
import bit.bitgroundspring.order.Order;
import bit.bitgroundspring.order.OrderRepository;
import bit.bitgroundspring.order.OrderType;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시즌 리포트의 거래 요약·상세: 체결된 주문만 센다 (미체결 예약 주문은 체결가가 없다) */
class TradeSummaryApiTest extends IntegrationTest {

    @Autowired
    OrderRepository orderRepository;

    private User user;
    private Cookie cookie;
    private Season season;
    private Coin btc;

    @BeforeEach
    void setUp() {
        season = fixtures.activeSeason();
        btc = fixtures.coin("KRW-BTC");
        user = fixtures.user(1_000_000);
        cookie = fixtures.login(user);
    }

    private void order(OrderType type, Status status, String amount, String tradePrice, String reservePrice) {
        orderRepository.save(Order.builder().user(user).coin(btc).season(season).orderType(type).status(status)
                .amount(new BigDecimal(amount))
                .tradePrice(tradePrice == null ? null : new BigDecimal(tradePrice))
                .reservePrice(reservePrice == null ? null : new BigDecimal(reservePrice))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void 미체결_예약_주문이_있어도_체결된_주문만_합산한다() throws Exception {
        order(OrderType.BUY, Status.COMPLETED, "0.01", "100000000", null); // 100만 원
        order(OrderType.BUY, Status.PENDING, "0.5", null, "90000000");     // 체결가 없음

        mvc.perform(get("/users/trade-summary").cookie(cookie).param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].buyAmount").value(1_000_000.0));
        mvc.perform(get("/users/trade-details").cookie(cookie).param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void 수익률은_숫자_날짜는_ISO로_내려주고_표시_형식은_화면이_정한다() throws Exception {
        order(OrderType.BUY, Status.COMPLETED, "0.01", "100000000", null);  // 100만 원 매수
        order(OrderType.SELL, Status.COMPLETED, "0.01", "115200000", null); // 115.2만 원 매도

        mvc.perform(get("/users/trade-summary").cookie(cookie).param("seasonId", season.getId().toString()))
                .andExpect(jsonPath("$[0].returnRate").value(closeTo(15.2, 1e-9)))
                .andExpect(jsonPath("$[0].profit").value(152_000.0))
                .andExpect(jsonPath("$[0].firstBuyAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
        mvc.perform(get("/users/trade-details").cookie(cookie).param("seasonId", season.getId().toString()))
                .andExpect(jsonPath("$[*].orderType", containsInAnyOrder("BUY", "SELL")))
                .andExpect(jsonPath("$[0].symbol").value("KRW-BTC"))
                .andExpect(jsonPath("$[0].amount").value(0.01))
                .andExpect(jsonPath("$[0].createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
    }
}
