package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.community.Post;
import bit.bitgroundspring.community.PostRepository;
import bit.bitgroundspring.notification.NotificationRepository;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import static bit.bitgroundspring.support.ProblemMatchers.problem;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 페이지 API: 관리자만 쓸 수 있고, 정지·삭제·공지 작업은 작업 기록이 남는다
 */
class AdminApiTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PostRepository postRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    JdbcTemplate jdbc;

    private User admin;
    private User member;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        admin = fixtures.admin();
        member = fixtures.user(1000);
        member.setEmail("member@example.com");
        userRepository.save(member);
        adminCookie = fixtures.login(admin);
        fixtures.activeSeason();
    }

    private long auditCount(String action) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action = ?", Long.class, action);
        return n == null ? 0 : n;
    }

    @Test
    void 관리자가_아니면_관리자_API를_쓸_수_없다() throws Exception {
        mvc.perform(get("/admin/overview").cookie(fixtures.login(member))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void 운영_현황을_집계한다() throws Exception {
        mvc.perform(get("/admin/overview").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(2))
                .andExpect(jsonPath("$.users.joinedToday").value(2))
                .andExpect(jsonPath("$.season.name").value("테스트 시즌"))
                .andExpect(jsonPath("$.ai.insightsToday").value(0));
    }

    @Test
    void 사용자_목록은_이름으로_찾고_이메일은_가린다() throws Exception {
        mvc.perform(get("/admin/users").param("q", member.getName().substring(0, 8)).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("me***@example.com"));
        // 닉네임은 겹칠 수 있어 번호(#id)와 이메일 전체로도 정확히 찾는다
        mvc.perform(get("/admin/users").param("q", "#" + member.getId()).cookie(adminCookie))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(member.getId()));
        mvc.perform(get("/admin/users").param("q", "MEMBER@example.com").cookie(adminCookie))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("me***@example.com"));
        mvc.perform(get("/admin/users").param("q", "ember@example.com").cookie(adminCookie))
                .andExpect(jsonPath("$.totalElements").value(0)); // 이메일은 일부만으로 찾지 않는다
        mvc.perform(get("/admin/users/{id}", member.getId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(1000))
                .andExpect(jsonPath("$.recentOrders").isArray());
    }

    @Test
    void 정지하면_세션이_끊기고_API를_못_쓰며_해제하면_다시_쓸_수_있다() throws Exception {
        Cookie memberCookie = fixtures.login(member);
        mvc.perform(post("/admin/users/{id}/suspend", member.getId()).cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"도배\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(member.getId()).orElseThrow().isSuspended()).isTrue();
        // 정지 전에 받은 토큰은 바로 막힌다
        mvc.perform(get("/users/me").cookie(memberCookie)).andExpect(status().is4xxClientError());
        // 새로 받은 토큰이어도 정지 상태면 쓸 수 없다 (무효화와 같은 초에 발급된 토큰도 무효로 보므로 다음 초에 발급)
        Thread.sleep(1100);
        mvc.perform(get("/users/me").cookie(fixtures.login(member)))
                .andExpect(problem(ErrorCode.USER_SUSPENDED));
        assertThat(auditCount("USER_SUSPEND")).isEqualTo(1);

        mvc.perform(post("/admin/users/{id}/unsuspend", member.getId()).cookie(adminCookie)).andExpect(status().isNoContent());
        mvc.perform(get("/users/me").cookie(fixtures.login(member))).andExpect(status().isOk());
        assertThat(auditCount("USER_UNSUSPEND")).isEqualTo(1);
    }

    @Test
    void 관리자_계정은_정지할_수_없고_사유가_필요하다() throws Exception {
        User otherAdmin = fixtures.admin();
        mvc.perform(post("/admin/users/{id}/suspend", otherAdmin.getId()).cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(problem(ErrorCode.CANNOT_SUSPEND_ADMIN));
        mvc.perform(post("/admin/users/{id}/suspend", member.getId()).cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(problem(ErrorCode.INVALID_INPUT));
        assertThat(auditCount("USER_SUSPEND")).isZero();
    }

    @Test
    void 게시글을_지우면_작업_기록이_남는다() throws Exception {
        Post post = postRepository.save(Post.builder().user(member).tier(0).title("광고글")
                .content("c").category(bit.bitgroundspring.community.Category.CHAT).build());

        mvc.perform(get("/admin/posts").cookie(adminCookie))
                .andExpect(jsonPath("$.content[0].title").value("광고글"));
        mvc.perform(delete("/admin/posts/{id}", post.getId()).cookie(adminCookie)).andExpect(status().isNoContent());

        assertThat(postRepository.findById(post.getId()).orElseThrow().getIsDeleted()).isTrue();
        mvc.perform(get("/admin/audit-logs").cookie(adminCookie))
                .andExpect(jsonPath("$.content[0].action").value("POST_DELETE"))
                .andExpect(jsonPath("$.content[0].adminName").value(admin.getName()));
    }

    @Test
    void 공지는_알림_없이_등록하고_수정할_수_있다() throws Exception {
        mvc.perform(post("/notices").cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"점검\",\"content\":\"<p>내용</p>\",\"notify\":false}"))
                .andExpect(status().isCreated());
        assertThat(notificationRepository.count()).isZero();

        Integer id = jdbc.queryForObject("SELECT id FROM notices LIMIT 1", Integer.class);
        mvc.perform(put("/notices/{id}", id).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"점검 (시간 변경)\",\"content\":\"<p>새 내용</p>\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT title FROM notices WHERE id = ?", String.class, id)).isEqualTo("점검 (시간 변경)");
        mvc.perform(put("/notices/{id}", id).cookie(fixtures.login(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden());
        assertThat(auditCount("NOTICE_CREATE")).isEqualTo(1);
        assertThat(auditCount("NOTICE_UPDATE")).isEqualTo(1);
    }
}
