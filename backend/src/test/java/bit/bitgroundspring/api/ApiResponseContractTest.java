package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API 응답 규약(docs/API.md) 검증
 * - 성공: DTO 그대로 + 상태 코드(200/201/204), 페이지는 PageResponse
 * - 실패: RFC 9457 Problem Details + code
 */
class ApiResponseContractTest extends IntegrationTest {

    private User user;
    private Cookie userCookie;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        user = fixtures.user(1_000_000);
        userCookie = fixtures.login(user);
        adminCookie = fixtures.login(fixtures.admin());
    }

    @Nested
    @DisplayName("에러 응답은 Problem Details 형식이다")
    class ErrorResponses {

        @Test
        void 인증이_없으면_401_UNAUTHORIZED() throws Exception {
            mvc.perform(get("/users/me")).andExpect(problem(ErrorCode.UNAUTHORIZED));
        }

        @Test
        void 위조된_토큰은_401_UNAUTHORIZED() throws Exception {
            mvc.perform(get("/users/me").cookie(new Cookie("jwt_token", "forged.token.value")))
                    .andExpect(problem(ErrorCode.UNAUTHORIZED));
        }

        @Test
        void 권한이_없으면_403_FORBIDDEN() throws Exception {
            mvc.perform(post("/notices").cookie(userCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"t\",\"content\":\"c\"}"))
                    .andExpect(problem(ErrorCode.FORBIDDEN));
        }

        @Test
        void 없는_경로는_404_RESOURCE_NOT_FOUND() throws Exception {
            mvc.perform(get("/no-such-api").cookie(userCookie)).andExpect(problem(ErrorCode.RESOURCE_NOT_FOUND));
        }

        @Test
        void 지원하지_않는_메서드는_405_METHOD_NOT_ALLOWED() throws Exception {
            mvc.perform(put("/coins").cookie(userCookie)).andExpect(problem(ErrorCode.METHOD_NOT_ALLOWED));
        }

        @Test
        void 요청_본문_검증_실패는_400과_필드별_errors() throws Exception {
            mvc.perform(post("/posts/form").cookie(userCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\",\"content\":\"\",\"category\":\"CHAT\"}"))
                    .andExpect(problem(ErrorCode.INVALID_INPUT))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "content")));
        }

        @Test
        void 깨진_JSON은_400_INVALID_INPUT() throws Exception {
            mvc.perform(post("/posts/form").cookie(userCookie).contentType(MediaType.APPLICATION_JSON).content("{bad"))
                    .andExpect(problem(ErrorCode.INVALID_INPUT));
        }

        @Test
        void 경로_변수_타입이_틀리면_400_INVALID_INPUT() throws Exception {
            mvc.perform(get("/posts/abc").cookie(userCookie)).andExpect(problem(ErrorCode.INVALID_INPUT));
        }

        @Test
        void 없는_리소스는_도메인_에러_코드() throws Exception {
            mvc.perform(get("/posts/999999").cookie(userCookie)).andExpect(problem(ErrorCode.POST_NOT_FOUND));
            mvc.perform(delete("/orders/999999").cookie(userCookie)).andExpect(problem(ErrorCode.ORDER_NOT_FOUND));
            mvc.perform(get("/coins/KRW-NONE/insight")).andExpect(problem(ErrorCode.AI_INSIGHT_NOT_FOUND));
        }

        @Test
        void 리프레시_토큰이_없거나_위조되면_401() throws Exception {
            mvc.perform(post("/auth/refresh")).andExpect(problem(ErrorCode.REFRESH_TOKEN_MISSING));
            mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "forged")))
                    .andExpect(problem(ErrorCode.REFRESH_TOKEN_INVALID));
        }

        @Test
        void 스케줄러_공유_키가_틀리면_403_INVALID_SECRET_KEY() throws Exception {
            mvc.perform(post("/seasons/update").param("secretKey", "wrong").param("seasonFlag", "season"))
                    .andExpect(problem(ErrorCode.INVALID_SECRET_KEY));
        }

        @Test
        void 이미지가_아닌_파일은_415_UNSUPPORTED_FILE_TYPE() throws Exception {
            mvc.perform(multipart("/uploads/images").file(new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes()))
                            .cookie(userCookie))
                    .andExpect(problem(ErrorCode.UNSUPPORTED_FILE_TYPE));
        }
    }

    @Nested
    @DisplayName("성공 응답은 래퍼 없이 DTO와 상태 코드로 표현한다")
    class SuccessResponses {

        @Test
        void 내_정보는_UserDto를_그대로_반환() throws Exception {
            mvc.perform(get("/users/me").cookie(userCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId()))
                    .andExpect(jsonPath("$.success").doesNotExist())
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        void 생성은_201과_Location_헤더_삭제는_204() throws Exception {
            String location = mvc.perform(post("/posts/form").cookie(userCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"제목\",\"content\":\"<p>본문</p>\",\"category\":\"CHAT\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("/posts/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andReturn().getResponse().getHeader("Location");

            mvc.perform(delete(location).cookie(userCookie))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        void 목록은_PageResponse_형식() throws Exception {
            mvc.perform(get("/posts/list").cookie(userCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").isNumber())
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.totalPages").isNumber())
                    .andExpect(jsonPath("$.last").isBoolean());

            mvc.perform(get("/notices").cookie(userCookie))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.number").doesNotExist());

            mvc.perform(get("/notifications").cookie(userCookie))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.pageable").doesNotExist());
        }

        @Test
        void 데이터가_없는_목록은_200과_빈_배열() throws Exception {
            mvc.perform(get("/ai-insights/today-symbols"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        void 공지_등록은_관리자만_201() throws Exception {
            mvc.perform(post("/notices").cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"공지\",\"content\":\"내용\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber());
        }

        @Test
        void 로그아웃은_204() throws Exception {
            mvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
        }
    }
}
