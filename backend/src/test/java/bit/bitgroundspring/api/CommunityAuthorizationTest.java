package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.community.CommentRepository;
import bit.bitgroundspring.community.Post;
import bit.bitgroundspring.community.PostRepository;
import bit.bitgroundspring.community.ReactionRepository;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import static bit.bitgroundspring.support.ProblemMatchers.problem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티·공지 권한: 작성자는 요청 본문이 아니라 JWT로 결정되고, 남의 글은 지울 수 없다
 */
class CommunityAuthorizationTest extends IntegrationTest {

    @Autowired
    PostRepository postRepository;
    @Autowired
    CommentRepository commentRepository;
    @Autowired
    ReactionRepository reactionRepository;

    private User author;
    private User other;
    private Cookie authorCookie;
    private Cookie otherCookie;

    @BeforeEach
    void setUp() {
        author = fixtures.user(0);
        other = fixtures.user(0);
        authorCookie = fixtures.login(author);
        otherCookie = fixtures.login(other);
    }

    private int createPost(Cookie cookie, String body) throws Exception {
        String json = mvc.perform(post("/posts/form").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void 게시글_작성자와_카운터는_클라이언트가_조작할_수_없다() throws Exception {
        int postId = createPost(authorCookie, """
                {"title":"t","content":"c","category":"CHAT",
                 "user":{"id":%d},"likes":999,"views":999}""".formatted(other.getId()));

        Post post = postRepository.findById(postId).orElseThrow();
        assertThat(post.getUser().getId()).isEqualTo(author.getId());
        assertThat(post.getLikes()).isZero();
        assertThat(post.getViews()).isZero();
    }

    @Test
    void 남의_게시글은_삭제할_수_없고_관리자는_가능하다() throws Exception {
        int postId = createPost(authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");

        mvc.perform(delete("/posts/" + postId).cookie(otherCookie)).andExpect(problem(ErrorCode.NOT_AUTHOR));
        mvc.perform(delete("/posts/" + postId).cookie(fixtures.login(fixtures.admin()))).andExpect(status().isNoContent());
        assertThat(postRepository.findById(postId).orElseThrow().getIsDeleted()).isTrue();
    }

    @Test
    void 댓글_작성자는_토큰_기준이고_남의_댓글은_삭제할_수_없다() throws Exception {
        int postId = createPost(authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        mvc.perform(post("/comments").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":%d,\"userId\":%d,\"content\":\"댓글\"}".formatted(postId, other.getId())))
                .andExpect(status().isCreated());

        var comment = commentRepository.findAll().get(0);
        assertThat(comment.getUser().getId()).isEqualTo(author.getId());

        // 예전 API처럼 userId 파라미터를 위조해도 무시된다
        mvc.perform(delete("/comments/" + comment.getId()).param("userId", String.valueOf(author.getId())).cookie(otherCookie))
                .andExpect(problem(ErrorCode.NOT_AUTHOR));
        mvc.perform(delete("/comments/" + comment.getId()).cookie(authorCookie)).andExpect(status().isNoContent());
    }

    @Test
    void 좋아요는_토큰_사용자로_기록된다() throws Exception {
        int postId = createPost(authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        mvc.perform(post("/reactions").cookie(otherCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"targetType\":\"POST\",\"targetId\":%d,\"liked\":true}".formatted(author.getId(), postId)))
                .andExpect(status().isNoContent());

        assertThat(reactionRepository.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getUser().getId()).isEqualTo(other.getId()));
        assertThat(postRepository.findById(postId).orElseThrow().getLikes()).isEqualTo(1);
    }

    @Test
    void 쓰지_않던_좋아요_API는_제거되었다() throws Exception {
        int postId = createPost(authorCookie, "{\"title\":\"t\",\"content\":\"c\",\"category\":\"CHAT\"}");
        mvc.perform(post("/posts/%d/like".formatted(postId)).cookie(otherCookie))
                .andExpect(status().is4xxClientError());
    }
}
