package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관심 코인 등록·해제·목록 */
class FavoriteCoinApiTest extends IntegrationTest {

    private Cookie cookie;

    @BeforeEach
    void setUp() {
        fixtures.coin("KRW-BTC");
        fixtures.coin("KRW-ETH");
        cookie = fixtures.login(fixtures.user(0));
    }

    @Test
    @DisplayName("등록한 코인만 내 목록에 보이고, 해제하면 빠진다")
    void addListRemove() throws Exception {
        mvc.perform(post("/favorites").cookie(cookie).param("symbol", "KRW-BTC")).andExpect(status().isCreated());
        mvc.perform(post("/favorites").cookie(cookie).param("symbol", "KRW-ETH")).andExpect(status().isCreated());
        mvc.perform(get("/favorites").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder("KRW-BTC", "KRW-ETH")));

        mvc.perform(delete("/favorites/KRW-BTC").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/favorites").cookie(cookie)).andExpect(jsonPath("$", containsInAnyOrder("KRW-ETH")));
    }

    @Test
    @DisplayName("다른 사용자의 관심 코인은 보이지 않는다")
    void listIsPerUser() throws Exception {
        User other = fixtures.user(0);
        mvc.perform(post("/favorites").cookie(fixtures.login(other)).param("symbol", "KRW-BTC")).andExpect(status().isCreated());

        mvc.perform(get("/favorites").cookie(cookie)).andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("이미 등록한 코인은 409, 없는 코인은 404, 등록하지 않은 코인 해제는 404")
    void errors() throws Exception {
        mvc.perform(post("/favorites").cookie(cookie).param("symbol", "KRW-BTC")).andExpect(status().isCreated());
        mvc.perform(post("/favorites").cookie(cookie).param("symbol", "KRW-BTC")).andExpect(problem(ErrorCode.FAVORITE_ALREADY_EXISTS));
        mvc.perform(post("/favorites").cookie(cookie).param("symbol", "KRW-NONE")).andExpect(problem(ErrorCode.COIN_NOT_FOUND));
        mvc.perform(delete("/favorites/KRW-ETH").cookie(cookie)).andExpect(problem(ErrorCode.FAVORITE_NOT_FOUND));
    }

    @Test
    @DisplayName("로그인하지 않으면 쓸 수 없다")
    void requiresLogin() throws Exception {
        mvc.perform(get("/favorites")).andExpect(problem(ErrorCode.UNAUTHORIZED));
        mvc.perform(post("/favorites").param("symbol", "KRW-BTC")).andExpect(problem(ErrorCode.UNAUTHORIZED));
    }
}
