package bit.bitgroundspring.common;

import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조회 결과 캐시 (Redis): 랭킹은 스케줄러 알림·프로필 변경 때 비우고, 뉴스는 10분간 외부 API 호출을 재사용한다
 */
class CacheTest extends IntegrationTest {

    private static final String SCHEDULER_KEY = "test-season-key"; // IntegrationTest의 season.update.key

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    UserService userService;
    @Autowired
    MeterRegistry meterRegistry;

    private User user;
    private Season season;
    private Cookie login;

    @BeforeEach
    void setUp() {
        season = fixtures.activeSeason();
        user = fixtures.user(0);
        login = fixtures.login(user);
        jdbc.update("INSERT INTO user_rankings (season_id, user_id, total_value, ranks, tier) VALUES (?, ?, 100, 1, 7)",
                season.getId(), user.getId());
    }

    private void changeTotalValue(int value) {
        jdbc.update("UPDATE user_rankings SET total_value = ? WHERE user_id = ?", value, user.getId());
    }

    @Test
    void 현재_시즌_랭킹은_캐시되고_스케줄러가_갱신을_알리면_비운다() throws Exception {
        mvc.perform(get("/rank/current").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].totalValue").value(100))
                // 캐시 값으로 바꿔도 응답 형태는 프로젝션 때와 같다
                .andExpect(jsonPath("$[0].userId").value(user.getId()))
                .andExpect(jsonPath("$[0].seasonId").value(season.getId()))
                .andExpect(jsonPath("$[0].updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));

        // 정각 사이에는 DB를 다시 읽지 않는다
        changeTotalValue(200);
        mvc.perform(get("/rank/current").cookie(login)).andExpect(jsonPath("$[0].totalValue").value(100));
        assertThat(redis.getExpire("cache:rankings::current", TimeUnit.SECONDS)).isBetween(1L, 3600L);

        // 키가 틀리면 비우지 않는다
        mvc.perform(post("/rank/refreshed").header("X-Scheduler-Key", "wrong")).andExpect(status().is4xxClientError());
        mvc.perform(get("/rank/current").cookie(login)).andExpect(jsonPath("$[0].totalValue").value(100));

        mvc.perform(post("/rank/refreshed").header("X-Scheduler-Key", SCHEDULER_KEY)).andExpect(status().isNoContent());
        mvc.perform(get("/rank/current").cookie(login)).andExpect(jsonPath("$[0].totalValue").value(200));
    }

    @Test
    void 시즌별_랭킹과_공개_미리보기도_같은_캐시를_쓴다() throws Exception {
        mvc.perform(get("/rank/" + season.getId()).cookie(login)).andExpect(jsonPath("$[0].totalValue").value(100));
        mvc.perform(get("/public/ranking")).andExpect(jsonPath("$.rankings[0].totalValue").value(100));

        changeTotalValue(300);
        mvc.perform(get("/rank/" + season.getId()).cookie(login)).andExpect(jsonPath("$[0].totalValue").value(100));
        mvc.perform(get("/public/ranking")).andExpect(jsonPath("$.rankings[0].totalValue").value(100));

        // 시즌 정산·스플릿 알림에도 비운다
        mvc.perform(post("/seasons/update").param("seasonFlag", "split").header("X-Scheduler-Key", SCHEDULER_KEY))
                .andExpect(status().isNoContent());
        mvc.perform(get("/rank/" + season.getId()).cookie(login)).andExpect(jsonPath("$[0].totalValue").value(300));
        mvc.perform(get("/public/ranking")).andExpect(jsonPath("$.rankings[0].totalValue").value(300));
    }

    @Test
    void 닉네임을_바꾸면_랭킹에_바로_보인다() throws Exception {
        mvc.perform(get("/rank/current").cookie(login)).andExpect(jsonPath("$[0].name").value(user.getName()));

        userService.updateUser(user.getId(), "새닉네임", null, null, false);

        mvc.perform(get("/rank/current").cookie(login)).andExpect(jsonPath("$[0].name").value("새닉네임"));
    }

    @Test
    void 탈퇴하면_랭킹에서_이름이_바로_가려진다() throws Exception {
        Cookie viewer = fixtures.login(fixtures.user(0)); // 탈퇴하면 그 사용자의 토큰은 무효가 되므로 다른 사용자가 본다
        mvc.perform(get("/rank/current").cookie(viewer)).andExpect(jsonPath("$[0].name").value(user.getName()));

        userService.softDeleteUser(user.getId());

        mvc.perform(get("/rank/current").cookie(viewer)).andExpect(jsonPath("$[0].name").value(UserService.WITHDRAWN_NAME));
    }

    private double cacheGets(String cache, String result) {
        return meterRegistry.get("cache.gets").tag("cache", cache).tag("result", result).functionCounter().count();
    }

    private static final String NEWS_BODY = """
            {"items":[{"title":"<b>비트코인</b> 상승","originallink":"https://a","link":"https://b",
                       "description":"설명","pubDate":"Mon, 09 Nov 2026 10:00:00 +0900"}]}""";

    @Test
    void 같은_뉴스_검색은_외부_API를_다시_부르지_않는다() throws Exception {
        when(restTemplate.exchange(any(RequestEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(NEWS_BODY));
        double hitsBefore = cacheGets("news", "hit"), missesBefore = cacheGets("news", "miss"); // 지표는 테스트 사이에 이어진다

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/news").param("keyword", "비트코인"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("<b>비트코인</b> 상승"));
        }
        verify(restTemplate, times(1)).exchange(any(RequestEntity.class), eq(String.class));
        // 적중·누락이 지표로 남는다 (Grafana 캐시 적중률 패널: cache_gets_total{cache, result})
        assertThat(cacheGets("news", "hit") - hitsBefore).isEqualTo(2);
        assertThat(cacheGets("news", "miss") - missesBefore).isEqualTo(1);

        // 검색어나 페이지가 다르면 따로 조회한다
        mvc.perform(get("/news").param("keyword", "이더리움")).andExpect(status().isOk());
        mvc.perform(get("/news").param("keyword", "비트코인").param("start", "11")).andExpect(status().isOk());
        verify(restTemplate, times(3)).exchange(any(RequestEntity.class), eq(String.class));
    }

    @Test
    void 뉴스_조회가_실패하면_캐시하지_않는다() throws Exception {
        when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("down"))
                .thenReturn(ResponseEntity.ok(NEWS_BODY));

        mvc.perform(get("/news").param("keyword", "비트코인"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500));
        mvc.perform(get("/news").param("keyword", "비트코인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
