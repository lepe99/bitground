package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.community.Category;
import bit.bitgroundspring.community.Post;
import bit.bitgroundspring.community.PostRepository;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시글 수정: 작성자 본인만, 본문은 등록과 같이 정제, 문의↔일반 글 전환과 답변 달린 문의 수정은 막는다
 */
class PostUpdateTest extends IntegrationTest {

    @Autowired
    PostRepository postRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Cookie authorCookie;
    private Cookie otherCookie;

    @BeforeEach
    void setUp() {
        User author = fixtures.user(0);
        authorCookie = fixtures.login(author);
        otherCookie = fixtures.login(fixtures.user(0));
    }

    private int create(String body) throws Exception {
        String json = mvc.perform(post("/posts/form").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions update(int id, Cookie cookie, String body) throws Exception {
        return mvc.perform(put("/posts/" + id).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 작성자는_제목_본문_분류를_고칠_수_있고_수정_시각이_남는다() throws Exception {
        int id = create("{\"title\":\"처음\",\"content\":\"<p>본문</p>\",\"category\":\"CHAT\"}");
        mvc.perform(get("/posts/" + id).cookie(authorCookie)).andExpect(jsonPath("$.editedAt").doesNotExist());

        update(id, authorCookie, """
                {"title":"고친 제목","content":"<p>고친 본문</p><img src=\\"https://www.bitground.kr/images/a.png\\"><script>alert(1)</script>","category":"INFO"}""")
                .andExpect(status().isNoContent());

        Post post = postRepository.findById(id).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("고친 제목");
        assertThat(post.getCategory()).isEqualTo(Category.INFO);
        assertThat(post.getContent()).contains("고친 본문").doesNotContain("<script");
        assertThat(post.getHasImage()).isTrue();
        mvc.perform(get("/posts/" + id).cookie(authorCookie)).andExpect(jsonPath("$.editedAt").exists());
    }

    @Test
    void 남의_글은_관리자라도_고칠_수_없다() throws Exception {
        int id = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        String body = "{\"title\":\"x\",\"content\":\"x\",\"category\":\"CHAT\"}";

        update(id, otherCookie, body).andExpect(problem(ErrorCode.NOT_AUTHOR));
        update(id, fixtures.login(fixtures.admin()), body).andExpect(problem(ErrorCode.NOT_AUTHOR));
        assertThat(postRepository.findById(id).orElseThrow().getTitle()).isEqualTo("t");
    }

    @Test
    void 지운_글은_없는_글로_응답한다() throws Exception {
        int id = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        mvc.perform(delete("/posts/" + id).cookie(authorCookie)).andExpect(status().isNoContent());

        update(id, authorCookie, "{\"title\":\"x\",\"content\":\"x\",\"category\":\"CHAT\"}")
                .andExpect(problem(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 문의와_일반_글은_서로_바꿀_수_없다() throws Exception {
        int chat = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        int inquiry = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"INQUIRY\",\"inquiryType\":\"BUG\"}");

        update(chat, authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"INQUIRY\",\"inquiryType\":\"BUG\"}")
                .andExpect(problem(ErrorCode.INVALID_INPUT));
        update(inquiry, authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}")
                .andExpect(problem(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 답변_전_문의는_유형까지_고칠_수_있고_답변이_달리면_막힌다() throws Exception {
        int id = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"INQUIRY\",\"inquiryType\":\"BUG\"}");

        update(id, authorCookie, "{\"title\":\"t2\",\"content\":\"c2\",\"category\":\"INQUIRY\",\"inquiryType\":\"ACCOUNT\"}")
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(id).orElseThrow().getInquiryType().name()).isEqualTo("ACCOUNT");

        jdbc.update("UPDATE posts SET answered_at = NOW() WHERE id = ?", id);
        update(id, authorCookie, "{\"title\":\"t3\",\"content\":\"c3\",\"category\":\"INQUIRY\",\"inquiryType\":\"ACCOUNT\"}")
                .andExpect(problem(ErrorCode.INQUIRY_ANSWERED));
    }

    @Test
    void 빈_제목은_거절한다() throws Exception {
        int id = create("{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        update(id, authorCookie, "{\"title\":\" \",\"content\":\"c\",\"category\":\"CHAT\"}")
                .andExpect(status().isBadRequest());
    }
}
