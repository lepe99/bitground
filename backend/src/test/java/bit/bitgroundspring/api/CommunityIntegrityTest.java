package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static bit.bitgroundspring.support.ProblemMatchers.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 데이터 무결성: 카운터 동시성, 조회수 중복 방지, 목록·댓글 조회, 본문 정제
 */
class CommunityIntegrityTest extends IntegrationTest {

    @Autowired
    BoardRepository boardRepository;
    @Autowired
    ObjectMapper objectMapper;

    private Cookie authorCookie;

    @BeforeEach
    void setUp() {
        authorCookie = fixtures.login(fixtures.user(0));
    }

    private int createPost(String content) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("title", "제목", "content", content, "category", "CHAT"));
        String json = mvc.perform(post("/posts/form").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asInt();
    }

    private void like(Cookie cookie, int postId) throws Exception {
        mvc.perform(post("/reactions").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":%d,\"liked\":true}".formatted(postId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("여러 사용자가 동시에 좋아요를 눌러도 개수가 유실되지 않는다")
    void concurrentLikes() throws Exception {
        int postId = createPost("<p>본문</p>");
        List<Cookie> cookies = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            cookies.add(fixtures.login(fixtures.user(0)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (Cookie cookie : cookies) {
                results.add(pool.submit((Callable<Object>) () -> {
                    like(cookie, postId);
                    return null;
                }));
            }
            for (Future<Object> f : results) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(boardRepository.findById(postId).orElseThrow().getLikes()).isEqualTo(20);
    }

    @Test
    @DisplayName("조회수는 같은 사용자가 여러 번 열어도 1만 오르고, forceViewCount로 올릴 수 없다")
    void viewCountIsDeduplicated() throws Exception {
        int postId = createPost("<p>본문</p>");
        Cookie reader = fixtures.login(fixtures.user(0));

        mvc.perform(get("/posts/" + postId).cookie(reader)).andExpect(jsonPath("$.views").value(1));
        mvc.perform(get("/posts/" + postId).cookie(reader)).andExpect(jsonPath("$.views").value(1));
        mvc.perform(get("/posts/" + postId).param("forceViewCount", "true").cookie(reader))
                .andExpect(jsonPath("$.views").value(1));

        mvc.perform(get("/posts/" + postId).cookie(fixtures.login(fixtures.user(0)))).andExpect(jsonPath("$.views").value(2));
    }

    @Test
    @DisplayName("목록은 본문 없이 이미지 포함 여부·댓글 수만 내려준다")
    void listOmitsContent() throws Exception {
        createPost("<p>사진</p><img src=\"https://cdn.example.com/a.png\">");
        createPost("<p>글만</p>");

        JsonNode content = objectMapper.readTree(mvc.perform(get("/posts/list").cookie(authorCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("content");

        assertThat(content).hasSize(2);
        assertThat(content.get(0).get("content").isNull()).isTrue();
        assertThat(content.get(0).get("hasImage").asBoolean()).isFalse(); // 최신순: 글만
        assertThat(content.get(1).get("hasImage").asBoolean()).isTrue();
        assertThat(content.get(1).get("commentCount").asLong()).isZero();
    }

    @Test
    @DisplayName("잘못된 카테고리는 400")
    void invalidCategory() throws Exception {
        mvc.perform(get("/posts/list").param("category", "NOPE").cookie(authorCookie))
                .andExpect(problem(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("댓글은 대댓글까지 트리로 내려준다")
    void commentTree() throws Exception {
        int postId = createPost("<p>본문</p>");
        int parentId = comment(postId, "부모", null);
        int childId = comment(postId, "자식", parentId);
        comment(postId, "손자", childId);
        comment(postId, "두 번째 부모", null);

        JsonNode roots = objectMapper.readTree(mvc.perform(get("/comments/post/" + postId).cookie(authorCookie))
                .andReturn().getResponse().getContentAsString());

        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).get("content").asText()).isEqualTo("부모");
        assertThat(roots.get(0).get("children").get(0).get("content").asText()).isEqualTo("자식");
        assertThat(roots.get(0).get("children").get(0).get("children").get(0).get("content").asText()).isEqualTo("손자");
        assertThat(roots.get(1).get("children")).isEmpty();
    }

    @Test
    @DisplayName("다른 게시글의 댓글에는 답글을 달 수 없다")
    void replyMustBelongToSamePost() throws Exception {
        int postA = createPost("<p>A</p>");
        int postB = createPost("<p>B</p>");
        int commentOnA = comment(postA, "A 댓글", null);

        mvc.perform(post("/comments").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":%d,\"content\":\"답글\",\"parentId\":%d}".formatted(postB, commentOnA)))
                .andExpect(problem(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("본문 HTML은 저장할 때 서버에서 정제한다")
    void contentIsSanitizedOnSave() throws Exception {
        int postId = createPost("""
                <p class="ql-align-center">안녕<script>alert(1)</script></p>
                <img src="https://cdn.example.com/a.png" onerror="alert(1)" style="width: 300px; position: fixed">
                <img src="javascript:alert(1)">
                <a href="javascript:alert(1)">링크</a><a href="https://example.com">정상</a>
                """);

        String saved = boardRepository.findById(postId).orElseThrow().getContent();
        assertThat(saved)
                .contains("<p class=\"ql-align-center\">안녕</p>")
                .contains("src=\"https://cdn.example.com/a.png\"")
                .contains("href=\"https://example.com\"")
                .contains("rel=\"noopener noreferrer\"")
                .doesNotContain("<script", "onerror", "javascript:", "position");
        assertThat(boardRepository.findById(postId).orElseThrow().getHasImage()).isTrue();
    }

    private int comment(int postId, String content, Integer parentId) throws Exception {
        String parent = parentId == null ? "null" : String.valueOf(parentId);
        mvc.perform(post("/comments").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":%d,\"content\":\"%s\",\"parentId\":%s}".formatted(postId, content, parent)))
                .andExpect(status().is2xxSuccessful());
        JsonNode all = objectMapper.readTree(mvc.perform(get("/comments/post/" + postId).cookie(authorCookie))
                .andReturn().getResponse().getContentAsString());
        return findId(all, content);
    }

    private static int findId(JsonNode nodes, String content) {
        for (JsonNode n : nodes) {
            if (n.get("content").asText().equals(content)) {
                return n.get("id").asInt();
            }
            int child = findId(n.get("children"), content);
            if (child > 0) {
                return child;
            }
        }
        return -1;
    }
}
