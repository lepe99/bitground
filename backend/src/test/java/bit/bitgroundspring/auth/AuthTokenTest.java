package bit.bitgroundspring.auth;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.security.token.AuthTokenService;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.Role;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import static bit.bitgroundspring.support.ProblemMatchers.problem;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 세션 (액세스 JWT + 리프레시 토큰 RTR)
 */
class AuthTokenTest extends IntegrationTest {

    @Autowired
    AuthTokenService authTokenService;
    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    StringRedisTemplate redis;

    private User user;
    private AuthTokenService.Tokens session;

    @BeforeEach
    void setUp() {
        user = fixtures.user(1_000_000);
        session = authTokenService.issue(user);
    }

    @Test
    @DisplayName("재발급하면 새 토큰 쌍을 받고, 새 리프레시 토큰으로 다시 재발급할 수 있다")
    void rotation() throws Exception {
        String next = refresh(session.refreshToken());
        assertThat(next).isNotBlank().isNotEqualTo(session.refreshToken());

        assertThat(refresh(next)).isNotBlank();
    }

    @Test
    @DisplayName("교체된 리프레시 토큰을 다시 쓰면 세션 전체가 폐기된다 (재사용 탐지)")
    void reuseRevokesFamily() throws Exception {
        String next = refresh(session.refreshToken());
        expireGrace(session.refreshToken());

        // 탈취된 이전 토큰으로 재발급 시도 → 401
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
        // 정상 사용자가 가진 최신 토큰도 함께 폐기됨
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", next)))
                .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("교체 직후 같은 토큰이 다시 오면(동시 요청) 세션을 유지하고 새 쿠키 없이 204")
    void concurrentRefreshWithinGrace() throws Exception {
        String next = refresh(session.refreshToken());

        MockHttpServletResponse res = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(res.getHeaders("Set-Cookie")).isEmpty();

        assertThat(refresh(next)).isNotBlank();
    }

    @Test
    @DisplayName("탈퇴하면 이미 발급된 액세스 토큰과 리프레시 토큰이 즉시 무효가 된다")
    void withdrawalRevokesEverything() throws Exception {
        Cookie jwt = new Cookie("jwt_token", session.accessToken());
        mvc.perform(get("/users/me").cookie(jwt)).andExpect(status().isOk());

        mvc.perform(delete("/users/me").cookie(jwt)).andExpect(status().isNoContent());

        mvc.perform(get("/users/me").cookie(jwt)).andExpect(problem(ErrorCode.UNAUTHORIZED));
        mvc.perform(post("/trade").cookie(jwt)).andExpect(problem(ErrorCode.UNAUTHORIZED));
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("탈퇴 처리된 사용자는 재발급받을 수 없다")
    void deletedUserCannotRefresh() throws Exception {
        user.setIsDeleted(true);
        userRepository.save(user);

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("재발급한 액세스 토큰에는 현재 DB의 권한이 반영된다")
    void refreshReflectsCurrentRole() throws Exception {
        user.setRole(Role.ROLE_ADMIN);
        userRepository.save(user);

        MockHttpServletResponse res = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        String jwt = cookieValue(res, "jwt_token");
        assertThat(jwtTokenProvider.parse(jwt).orElseThrow().get("auth")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("로그아웃한 세션의 리프레시 토큰은 쓸 수 없고, 다른 기기 세션은 유지된다")
    void logoutRevokesOnlyThatSession() throws Exception {
        AuthTokenService.Tokens otherDevice = authTokenService.issue(user);

        mvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
                .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
        assertThat(refresh(otherDevice.refreshToken())).isNotBlank();
    }

    @Test
    @DisplayName("리프레시 토큰 키에는 설정된 만료 시간이 걸린다")
    void refreshTokenHasTtl() {
        Long ttl = redis.getExpire("refresh:token:" + session.refreshToken());
        assertThat(ttl).isPositive();
    }

    // 재발급 후 새 refresh_token 쿠키 값을 반환
    private String refresh(String refreshToken) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        return cookieValue(res, "refresh_token");
    }

    private static String cookieValue(MockHttpServletResponse res, String name) {
        return res.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.substring(name.length() + 1, h.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    // 교체 시각을 과거로 돌려 grace 시간을 지난 것처럼 만든다
    private void expireGrace(String rotatedToken) {
        redis.opsForHash().put("refresh:token:" + rotatedToken, "rotatedAt", "0");
    }
}
