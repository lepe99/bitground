package bit.bitgroundspring.api;

import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
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
}
