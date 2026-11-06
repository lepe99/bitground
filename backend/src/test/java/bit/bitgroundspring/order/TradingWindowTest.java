package bit.bitgroundspring.order;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.service.OrderExecutionService;
import bit.bitgroundspring.service.TradingWindow;
import bit.bitgroundspring.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시즌 종료 시각부터 정산이 끝날 때까지 거래 마감: 주문을 받지 않고 예약 주문도 체결하지 않으며,
 * 새 시즌이 시작되면(스케줄러 알림) 다시 열린다
 */
class TradingWindowTest extends IntegrationTest {

    private static final double PRICE = 100_000_000;

    @Autowired
    SeasonRepository seasonRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    TradingWindow tradingWindow;
    @Autowired
    OrderExecutionService orderExecutionService;
    @Autowired
    StringRedisTemplate redis;
    @Value("${upbit.order.execution.stream}")
    String stream;

    private Season season;
    private Cookie cookie;

    @BeforeEach
    void setUp() {
        season = fixtures.activeSeason();
        fixtures.coin("KRW-BTC");
        cookie = fixtures.login(fixtures.user(10_000_000));
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", PRICE))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        endsAt(LocalDateTime.now().plusHours(1));
    }

    private void endsAt(LocalDateTime at) {
        season.setEndsAt(at);
        season = seasonRepository.save(season);
        tradingWindow.refresh();
    }

    private ResultActions marketBuy() throws Exception {
        return mvc.perform(post("/trade").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"totalPrice\":100000}"));
    }

    private ResultActions reserveBuy() throws Exception {
        return mvc.perform(post("/orders/reserve").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"KRW-BTC\",\"orderType\":\"BUY\",\"amount\":0.001,\"reservePrice\":%s}".formatted(PRICE * 0.5)));
    }

    @Test
    void 종료_시각이_지나면_시장가와_예약_주문을_받지_않는다() throws Exception {
        marketBuy().andExpect(status().isCreated());

        endsAt(LocalDateTime.now().minusSeconds(1));

        marketBuy().andExpect(problem(ErrorCode.SEASON_SETTLING));
        reserveBuy().andExpect(problem(ErrorCode.SEASON_SETTLING));
    }

    @Test
    void 종료_시각이_지나면_가격에_닿은_예약_주문도_체결하지_않는다() throws Exception {
        String body = reserveBuy().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(body, "$.orderId");

        endsAt(LocalDateTime.now().minusSeconds(1));
        redis.opsForStream().add(stream, Map.of("orderId", String.valueOf(orderId), "price", "49000000",
                "requestedAt", String.valueOf(System.currentTimeMillis())));
        orderExecutionService.processExecutionQueue();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void 새_시즌이_시작되면_스케줄러_알림으로_바로_다시_열린다() throws Exception {
        endsAt(LocalDateTime.now().minusSeconds(1));
        marketBuy().andExpect(problem(ErrorCode.SEASON_SETTLING));

        // 스케줄러 정산: 이전 시즌 종료, 새 시즌 생성 (종료 시각은 미래)
        season.setStatus(Status.COMPLETED);
        seasonRepository.save(season);
        seasonRepository.save(Season.builder().name("다음 시즌").startAt(LocalDate.now()).endAt(LocalDate.now().plusDays(14))
                .endsAt(LocalDateTime.now().plusDays(15)).status(Status.PENDING).build());
        mvc.perform(post("/seasons/update").header("X-Scheduler-Key", "test-season-key").param("seasonFlag", "season"))
                .andExpect(status().isNoContent());

        marketBuy().andExpect(status().isCreated());
    }
}
