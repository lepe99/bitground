package bit.bitgroundspring.market;

import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화면이 업비트 REST를 직접 부르지 않도록 서버가 주는 시세 정보: 코인 목록(DB, 매시 갱신)과 1시간봉(업비트를 대신 불러 캐시)
 */
class CoinMarketCacheTest extends IntegrationTest {

    private static final String SCHEDULER_KEY = "test-season-key"; // IntegrationTest의 season.update.key

    @Autowired
    JdbcTemplate jdbc;

    private void coin(String symbol, String name, long tradePrice, boolean warning, boolean caution) {
        jdbc.update("INSERT INTO coins (symbol, korean_name, trade_price_24h, change_rate, is_warning, is_caution) VALUES (?, ?, ?, 0.01, ?, ?)",
                symbol, name, tradePrice, warning, caution);
    }

    @Test
    void 코인_목록은_거래대금_순으로_유의_주의를_담아_주고_매시_갱신_알림까지_캐시한다() throws Exception {
        coin("KRW-BTC", "비트코인", 500, false, false);
        coin("KRW-XRP", "리플", 900, true, false);
        coin("KRW-OLD", "상폐", 10_000, false, false);
        jdbc.update("UPDATE coins SET is_deleted = true WHERE symbol = 'KRW-OLD'");

        mvc.perform(get("/coins/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].symbol", contains("KRW-XRP", "KRW-BTC")))
                .andExpect(jsonPath("$[0].koreanName").value("리플"))
                .andExpect(jsonPath("$[0].warning").value(true))
                .andExpect(jsonPath("$[0].caution").value(false));

        // 정각 사이에는 DB를 다시 읽지 않는다
        coin("KRW-ETH", "이더리움", 1_000, false, true);
        mvc.perform(get("/coins/markets")).andExpect(jsonPath("$", hasSize(2)));

        // 스케줄러의 매시 갱신 알림에 비운다
        mvc.perform(post("/rank/refreshed").header("X-Scheduler-Key", SCHEDULER_KEY)).andExpect(status().isNoContent());
        mvc.perform(get("/coins/markets"))
                .andExpect(jsonPath("$[*].symbol", contains("KRW-ETH", "KRW-XRP", "KRW-BTC")))
                .andExpect(jsonPath("$[0].caution").value(true));
    }

    @Test
    void 추이_그래프는_서버가_업비트를_대신_불러_종목별로_캐시한다() throws Exception {
        coin("KRW-BTC", "비트코인", 500, false, false);
        // 업비트는 최신 봉이 앞에 온다
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", 3), Map.of("trade_price", 2), Map.of("trade_price", 1))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/coins/KRW-BTC/hourly-closes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", contains(1.0, 2.0, 3.0))); // 오래된 것부터
        }
        verify(restTemplate, times(1))
                .exchange(ArgumentMatchers.contains("market=KRW-BTC&count=24"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void 원화_마켓에_없는_종목은_업비트를_부르지_않는다() throws Exception {
        mvc.perform(get("/coins/KRW-NOPE/hourly-closes")).andExpect(status().isNotFound());
        verify(restTemplate, never())
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void 업비트_조회가_실패하면_캐시하지_않는다() throws Exception {
        coin("KRW-BTC", "비트코인", 500, false, false);
        doThrow(new RestClientException("down"))
                .doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", 7))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        mvc.perform(get("/coins/KRW-BTC/hourly-closes")).andExpect(status().isBadGateway());
        mvc.perform(get("/coins/KRW-BTC/hourly-closes")).andExpect(jsonPath("$", contains(7.0)));
    }
}
