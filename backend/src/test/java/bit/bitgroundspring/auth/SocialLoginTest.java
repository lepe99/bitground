package bit.bitgroundspring.auth;

import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.oauth2.CustomOidcUserService;
import bit.bitgroundspring.security.oauth2.OidcAuthenticationSuccessHandler;
import bit.bitgroundspring.service.InitialCashPolicy;
import bit.bitgroundspring.service.UserService;
import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 소셜 로그인: 첫 로그인은 가입, 다시 로그인하면 같은 계정, 탈퇴 후 다시 로그인하면 새 계정.
 * 네이버 경로를 쓴다 (사용자 정보를 RestTemplate으로 가져오므로 외부 호출만 Mock으로 바꾸면 가입 코드를 그대로 탄다)
 */
class SocialLoginTest extends IntegrationTest {

    private static final String NAVER_ME = "https://openapi.naver.com/v1/nid/me";
    private static final String NAVER_DEFAULT_IMAGE = "https://ssl.pstatic.net/static/pwe/address/img_profile.png";

    @Autowired
    CustomOidcUserService oidcUserService;
    @Autowired
    OidcAuthenticationSuccessHandler successHandler;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserService userService;
    @Autowired
    InitialCashPolicy initialCashPolicy;
    @Value("${react.host}")
    String reactHost;

    private final ClientRegistration naver = ClientRegistration.withRegistrationId("naver")
            .clientId("test")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/naver")
            .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
            .tokenUri("https://nid.naver.com/oauth2.0/token")
            .build();

    @BeforeEach
    void naverProfile() {
        naverReturns("네이버친구", "friend@example.com", NAVER_DEFAULT_IMAGE);
    }

    private void naverReturns(String nickname, String email, String image) {
        Map<String, Object> response = new HashMap<>();
        response.put("nickname", nickname);
        response.put("email", email);
        response.put("profile_image", image);
        when(restTemplate.exchange(eq(NAVER_ME), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("response", response)));
    }

    private OidcUser login(String subject) {
        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "naver-access", now, now.plusSeconds(60));
        OidcIdToken idToken = OidcIdToken.withTokenValue("naver-id-token").subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        return oidcUserService.loadUser(new OidcUserRequest(naver, accessToken, idToken));
    }

    private User userOf(String subject) {
        return userRepository.findByProviderAndProviderId("naver", subject).orElseThrow();
    }

    private MockHttpServletResponse completeLogin(OidcUser oidcUser) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/naver");
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(oidcUser, null));
        return response;
    }

    @Test
    @DisplayName("첫 로그인은 시작 자금을 받고 가입되며, 소셜 서비스의 기본 프로필 사진은 저장하지 않는다")
    void firstLoginSignsUp() {
        login("naver-1");

        User user = userOf("naver-1");
        assertThat(user.getName()).isEqualTo("네이버친구");
        assertThat(user.getEmail()).isEqualTo("friend@example.com");
        assertThat(user.getCash()).isEqualTo((long) initialCashPolicy.getInitialCash());
        assertThat(user.getProfileImage()).isNull(); // 기본 사진 → 우리 기본 프로필 사진
    }

    @Test
    @DisplayName("직접 올린 프로필 사진은 가입 때 그대로 저장한다")
    void customProfileImageIsKept() {
        naverReturns("사진있음", "photo@example.com", "https://phinf.pstatic.net/my-photo.jpg");
        login("naver-photo");

        assertThat(userOf("naver-photo").getProfileImage()).isEqualTo("https://phinf.pstatic.net/my-photo.jpg");
    }

    @Test
    @DisplayName("다시 로그인하면 같은 계정이고, 소셜 쪽 정보가 바뀌어도 우리 쪽에서 고친 닉네임·자금은 그대로다")
    void loginAgainKeepsAccount() {
        login("naver-1");
        User user = userOf("naver-1");
        userService.updateUser(user.getId(), "새닉네임", null, null, false);
        user = userOf("naver-1");
        user.setCash(123L);
        userRepository.save(user);

        naverReturns("바뀐이름", "changed@example.com", NAVER_DEFAULT_IMAGE);
        login("naver-1");

        assertThat(userRepository.count()).isEqualTo(1);
        User again = userOf("naver-1");
        assertThat(again.getId()).isEqualTo(user.getId());
        assertThat(again.getName()).isEqualTo("새닉네임");
        assertThat(again.getCash()).isEqualTo(123L);
    }

    @Test
    @DisplayName("탈퇴 후 같은 소셜 계정으로 로그인하면 새 계정으로 가입되고, 이전 계정은 익명으로 남는다")
    void rejoinAfterWithdrawal() {
        login("naver-1");
        User before = userOf("naver-1");
        before.setCash(1L);
        userRepository.save(before);
        userService.softDeleteUser(before.getId());

        login("naver-1");

        User rejoined = userOf("naver-1");
        assertThat(rejoined.getId()).isNotEqualTo(before.getId());
        assertThat(rejoined.getCash()).isEqualTo((long) initialCashPolicy.getInitialCash()); // 처음부터 다시
        User old = userRepository.findById(before.getId()).orElseThrow();
        assertThat(old.getIsDeleted()).isTrue();
        assertThat(old.getName()).isEqualTo(UserService.WITHDRAWN_NAME);
        assertThat(old.getEmail()).isNull();
        assertThat(old.getProviderId()).isNull();
    }

    @Test
    @DisplayName("로그인을 마치면 액세스·리프레시 토큰 쿠키를 받고 프론트 콜백으로 이동한다")
    void successIssuesCookies() throws Exception {
        MockHttpServletResponse response = completeLogin(login("naver-1"));

        assertThat(response.getRedirectedUrl()).isEqualTo(reactHost + "/auth/callback");
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("jwt_token=") && c.contains("HttpOnly"))
                .anyMatch(c -> c.startsWith("refresh_token=") && c.contains("HttpOnly"));
    }

    @Test
    @DisplayName("이용 정지된 계정은 토큰 없이 로그인 화면으로 돌려보낸다")
    void suspendedUserGetsNoToken() throws Exception {
        OidcUser oidcUser = login("naver-1");
        User user = userOf("naver-1");
        user.setSuspendedAt(LocalDateTime.now());
        userRepository.save(user);

        MockHttpServletResponse response = completeLogin(oidcUser);

        assertThat(response.getRedirectedUrl()).isEqualTo(reactHost + "/login?error=SUSPENDED");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }
}
