package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 횟수 제한 (RateLimitFilter)
 */
class RateLimitTest extends IntegrationTest {

    private static final MockMultipartFile TEXT_FILE = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

    @Test
    @DisplayName("업로드는 사용자당 1분에 10회, 초과하면 429와 Retry-After")
    void uploadLimitPerUser() throws Exception {
        Cookie user = fixtures.login(fixtures.user(0));
        for (int i = 0; i < 10; i++) {
            mvc.perform(multipart("/uploads/images").file(TEXT_FILE).cookie(user))
                    .andExpect(problem(ErrorCode.UNSUPPORTED_FILE_TYPE)); // 제한 안에서는 정상 처리
        }

        mvc.perform(multipart("/uploads/images").file(TEXT_FILE).cookie(user))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().exists("Retry-After"));

        // 다른 사용자는 영향받지 않음
        mvc.perform(multipart("/uploads/images").file(TEXT_FILE).cookie(fixtures.login(fixtures.user(0))))
                .andExpect(problem(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    @DisplayName("글쓰기는 사용자당 1분에 5회 (스크립트로 글을 쏟아내지 못하게)")
    void postLimitPerUser() throws Exception {
        Cookie user = fixtures.login(fixtures.user(0));
        String post = "{\"title\":\"제목\",\"content\":\"내용\",\"category\":\"CHAT\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/posts/form").cookie(user).contentType(MediaType.APPLICATION_JSON).content(post))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/posts/form").cookie(user).contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("토큰 재발급은 비로그인 요청이라 IP 기준으로 1분에 30회")
    void refreshLimitPerIp() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/auth/refresh")).andExpect(problem(ErrorCode.REFRESH_TOKEN_MISSING));
        }
        mvc.perform(post("/auth/refresh")).andExpect(status().isTooManyRequests());
    }
}
