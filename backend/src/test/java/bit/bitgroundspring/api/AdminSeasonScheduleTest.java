package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 시즌 일정 변경: DB 일정만 바꾸면 스케줄러가 다음 정각에 반영하므로, 정각 단위·미래 시각만 받고
 * 이미 지급한 스플릿이나 정산을 기다리는 시즌은 바꾸지 않는다
 */
class AdminSeasonScheduleTest extends IntegrationTest {

    @Autowired
    SeasonRepository seasonRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Season season;
    private Cookie adminCookie;
    private LocalDateTime hour;

    @BeforeEach
    void setUp() {
        adminCookie = fixtures.login(fixtures.admin());
        season = fixtures.activeSeason();
        hour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        // 시즌 시작을 일주일 전으로: "2시간 전 스플릿" 같은 과거 시각이 0~2시에도 시즌 시작보다 앞서지 않게
        // (기존: 시작일이 오늘이라 0~2시에 실행하면 실패)
        season.setStartAt(hour.toLocalDate().minusDays(7));
        schedule(hour.plusDays(3), hour.plusDays(10));
    }

    private void schedule(LocalDateTime split, LocalDateTime ends) {
        season.setSplitStartsAt(split);
        season.setEndsAt(ends);
        season = seasonRepository.save(season);
    }

    private ResultActions update(Cookie cookie, LocalDateTime split, LocalDateTime ends) throws Exception {
        return mvc.perform(put("/admin/season/schedule").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"splitStartsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(split, ends)));
    }

    @Test
    void 일정을_바꾸면_DB에_저장하고_작업_기록을_남긴다() throws Exception {
        LocalDateTime split = hour.plusDays(2);
        LocalDateTime ends = hour.plusDays(5).plusHours(2);

        update(adminCookie, split, ends)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitStartsAt").value(split.toString() + ":00"))
                .andExpect(jsonPath("$.split").value(1));

        Season saved = seasonRepository.findById(season.getId()).orElseThrow();
        assertThat(saved.getSplitStartsAt()).isEqualTo(split);
        assertThat(saved.getEndsAt()).isEqualTo(ends);
        assertThat(saved.getEndAt()).isEqualTo(ends.minusSeconds(1).toLocalDate());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'SEASON_SCHEDULE'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void 관리자만_바꿀_수_있다() throws Exception {
        update(fixtures.login(fixtures.user(1000)), hour.plusDays(2), hour.plusDays(5)).andExpect(status().isForbidden());
    }

    @Test
    void 정각이_아니거나_순서가_틀리거나_지난_시각은_받지_않는다() throws Exception {
        update(adminCookie, hour.plusDays(2).plusMinutes(30), hour.plusDays(5)).andExpect(problem(ErrorCode.INVALID_SEASON_SCHEDULE));
        update(adminCookie, hour.plusDays(5), hour.plusDays(2)).andExpect(problem(ErrorCode.INVALID_SEASON_SCHEDULE));
        update(adminCookie, hour.minusHours(1), hour.plusDays(5)).andExpect(problem(ErrorCode.INVALID_SEASON_SCHEDULE));
        update(adminCookie, hour.plusDays(3), hour.minusHours(1)).andExpect(problem(ErrorCode.INVALID_SEASON_SCHEDULE));
    }

    @Test
    void 이미_시작된_스플릿은_옮길_수_없고_종료만_바꿀_수_있다() throws Exception {
        LocalDateTime pastSplit = hour.minusHours(2);
        schedule(pastSplit, hour.plusDays(5));

        update(adminCookie, hour.plusDays(1), hour.plusDays(5)).andExpect(problem(ErrorCode.INVALID_SEASON_SCHEDULE));
        update(adminCookie, pastSplit, hour.plusDays(7)).andExpect(status().isOk()).andExpect(jsonPath("$.split").value(2));
    }

    @Test
    void 종료_시각이_지나_정산을_기다리는_시즌은_바꿀_수_없다() throws Exception {
        schedule(hour.minusDays(3), hour.minusHours(1));

        update(adminCookie, hour.minusDays(3), hour.plusDays(1)).andExpect(problem(ErrorCode.SEASON_SETTLING));
    }
}
