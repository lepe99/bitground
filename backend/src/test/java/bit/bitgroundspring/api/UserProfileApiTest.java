package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.UserService;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 정보 수정·탈퇴 */
class UserProfileApiTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    private User user;
    private Cookie cookie;

    @BeforeEach
    void setUp() {
        user = fixtures.user(1_000_000);
        cookie = fixtures.login(user);
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    @DisplayName("닉네임·이메일을 앞뒤 공백 없이 저장하고, 처음 가입 확인(onboarded)을 마친 것으로 표시한다")
    void updateProfile() throws Exception {
        mvc.perform(put("/users/me").cookie(cookie).param("name", "  새이름  ").param("email", " me@example.com "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"));

        User saved = reload();
        assertThat(saved.getName()).isEqualTo("새이름");
        assertThat(saved.getEmail()).isEqualTo("me@example.com");
        assertThat(saved.getIsOnboarded()).isTrue();
    }

    @Test
    @DisplayName("이메일을 비우면 이메일 없음으로 저장한다 (카카오 로그인 등)")
    void blankEmailClearsEmail() throws Exception {
        user.setEmail("old@example.com");
        userRepository.save(user);

        mvc.perform(put("/users/me").cookie(cookie).param("name", "이름").param("email", ""))
                .andExpect(status().isOk());

        assertThat(reload().getEmail()).isNull();
    }

    @Test
    @DisplayName("닉네임은 2~8자, 이메일은 형식이 맞아야 하고, 틀리면 아무것도 바꾸지 않는다")
    void rejectsInvalidInput() throws Exception {
        String before = reload().getName();
        for (String name : new String[]{"a", "  b  ", "아홉글자짜리닉네임"}) {
            mvc.perform(put("/users/me").cookie(cookie).param("name", name))
                    .andExpect(problem(ErrorCode.INVALID_INPUT));
        }
        mvc.perform(put("/users/me").cookie(cookie).param("name", "이름").param("email", "not-an-email"))
                .andExpect(problem(ErrorCode.INVALID_INPUT));

        assertThat(reload().getName()).isEqualTo(before);
    }

    @Test
    @DisplayName("기본 프로필 사진으로 바꾸면 사진 주소를 지운다")
    void removeImage() throws Exception {
        user.setProfileImage("https://phinf.pstatic.net/my-photo.jpg");
        userRepository.save(user);

        mvc.perform(put("/users/me").cookie(cookie).param("name", "이름").param("removeImage", "true"))
                .andExpect(status().isOk());

        assertThat(reload().getProfileImage()).isNull();
    }

    @Test
    @DisplayName("탈퇴하면 개인정보를 지우고 로그인 쿠키를 삭제한다. 거래 기록이 남도록 행은 지우지 않는다")
    void withdrawAnonymizes() throws Exception {
        user.setEmail("me@example.com");
        user.setProfileImage("https://phinf.pstatic.net/my-photo.jpg");
        userRepository.save(user);

        MvcResult result = mvc.perform(delete("/users/me").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("jwt_token=;") && c.contains("Max-Age=0"))
                .anyMatch(c -> c.startsWith("refresh_token=;") && c.contains("Max-Age=0"));
        User withdrawn = reload();
        assertThat(withdrawn.getIsDeleted()).isTrue();
        assertThat(withdrawn.getName()).isEqualTo(UserService.WITHDRAWN_NAME);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getProviderId()).isNull();
        assertThat(withdrawn.getProfileImage()).isNull();
        assertThat(withdrawn.getCash()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 정보를 조회·수정할 수 없다")
    void withdrawnUserCannotUseProfile() throws Exception {
        user.setIsDeleted(true);
        userRepository.save(user);

        mvc.perform(get("/users/me").cookie(cookie)).andExpect(problem(ErrorCode.USER_NOT_FOUND));
        mvc.perform(put("/users/me").cookie(cookie).param("name", "되살리기")).andExpect(problem(ErrorCode.USER_NOT_FOUND));
    }
}
