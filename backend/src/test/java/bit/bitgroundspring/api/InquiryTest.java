package bit.bitgroundspring.api;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.community.PostRepository;
import bit.bitgroundspring.notification.Message;
import bit.bitgroundspring.notification.NotificationRepository;
import bit.bitgroundspring.support.IntegrationTest;
import bit.bitgroundspring.user.User;
import static bit.bitgroundspring.support.ProblemMatchers.problem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문의: 작성자와 관리자만 보고(목록·상세·댓글·반응 모두), 전체 목록에 섞이지 않으며,
 * 관리자가 답하면 답변 완료가 되고 알림이 오간다
 */
class InquiryTest extends IntegrationTest {

    @Autowired
    PostRepository postRepository;
    @Autowired
    NotificationRepository notificationRepository;

    private User author;
    private User other;
    private User admin;
    private Cookie authorCookie;
    private Cookie otherCookie;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        author = fixtures.user(0);
        other = fixtures.user(0);
        admin = fixtures.admin();
        authorCookie = fixtures.login(author);
        otherCookie = fixtures.login(other);
        adminCookie = fixtures.login(admin);
    }

    private int write(Cookie cookie, String category, String inquiryType, String title) throws Exception {
        String type = inquiryType == null ? "" : ",\"inquiryType\":\"%s\"".formatted(inquiryType);
        String json = mvc.perform(post("/posts/form").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"content\":\"내용\",\"category\":\"%s\"%s}".formatted(title, category, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions comment(Cookie cookie, int postId, String content) throws Exception {
        return mvc.perform(post("/comments").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":%d,\"content\":\"%s\"}".formatted(postId, content)));
    }

    private ResultActions list(Cookie cookie, String category) throws Exception {
        var request = get("/posts/list").cookie(cookie);
        if (category != null) {
            request.param("category", category);
        }
        return mvc.perform(request);
    }

    @Test
    void 문의는_전체_목록에_섞이지_않고_문의_탭에서_일반_사용자는_내_글만_관리자는_모두_본다() throws Exception {
        write(authorCookie, "CHAT", null, "잡담");
        write(authorCookie, "INQUIRY", "BUG", "내 문의");
        write(otherCookie, "INQUIRY", "ACCOUNT", "남의 문의");

        list(authorCookie, null).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("잡담"));
        list(adminCookie, null).andExpect(jsonPath("$.content", hasSize(1)));

        list(authorCookie, "INQUIRY").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("내 문의"))
                .andExpect(jsonPath("$.content[0].inquiryType").value("BUG"))
                .andExpect(jsonPath("$.content[0].answered").value(false));
        list(adminCookie, "INQUIRY").andExpect(jsonPath("$.content[*].title", containsInAnyOrder("내 문의", "남의 문의")));
    }

    @Test
    void 남의_문의는_상세_댓글_댓글수_반응_모두_없는_글로_응답한다() throws Exception {
        int id = write(authorCookie, "INQUIRY", "ETC", "비공개");

        mvc.perform(get("/posts/" + id).cookie(otherCookie)).andExpect(problem(ErrorCode.POST_NOT_FOUND));
        mvc.perform(get("/comments/post/" + id).cookie(otherCookie)).andExpect(problem(ErrorCode.POST_NOT_FOUND));
        comment(otherCookie, id, "끼어들기").andExpect(problem(ErrorCode.POST_NOT_FOUND));
        mvc.perform(post("/reactions").cookie(otherCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":%d,\"liked\":true}".formatted(id)))
                .andExpect(problem(ErrorCode.POST_NOT_FOUND));

        mvc.perform(get("/posts/" + id).cookie(authorCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiryType").value("ETC"));
        mvc.perform(get("/posts/" + id).cookie(adminCookie)).andExpect(status().isOk());
    }

    @Test
    void 문의에는_반응할_수_없고_조회수도_올리지_않는다() throws Exception {
        int id = write(authorCookie, "INQUIRY", "SUGGESTION", "건의");

        mvc.perform(post("/reactions").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":%d,\"liked\":true}".formatted(id)))
                .andExpect(problem(ErrorCode.INVALID_INPUT));
        mvc.perform(get("/posts/" + id).cookie(authorCookie)).andExpect(jsonPath("$.views").value(0));
        assertThat(postRepository.findById(id).orElseThrow().getViews()).isZero();
    }

    @Test
    void 문의는_유형이_필요하다() throws Exception {
        mvc.perform(post("/posts/form").cookie(authorCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"category\":\"INQUIRY\"}"))
                .andExpect(problem(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 새_문의는_관리자에게_알리고_관리자가_답하면_답변_완료와_작성자_알림() throws Exception {
        int id = write(authorCookie, "INQUIRY", "BUG", "버그");
        assertThat(notificationRepository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getUser().getId()).isEqualTo(admin.getId());
            assertThat(n.getMessageType()).isEqualTo(Message.INQUIRY);
            assertThat(n.getPostId()).isEqualTo(id);
        });

        comment(adminCookie, id, "확인했습니다").andExpect(status().isCreated());

        list(authorCookie, "INQUIRY").andExpect(jsonPath("$.content[0].answered").value(true));
        mvc.perform(get("/posts/list").cookie(adminCookie).param("category", "INQUIRY").param("answered", "false"))
                .andExpect(jsonPath("$.content", hasSize(0)));
        assertThat(notificationRepository.findAll())
                .filteredOn(n -> n.getUser().getId().equals(author.getId()))
                .singleElement().satisfies(n -> assertThat(n.getMessage()).startsWith("문의에 답변이 등록됐어요."));
        mvc.perform(get("/admin/overview").cookie(adminCookie)).andExpect(jsonPath("$.pendingInquiries").value(0));
    }

    @Test
    void 작성자의_추가_질문은_관리자에게_알린다() throws Exception {
        int id = write(authorCookie, "INQUIRY", "ACCOUNT", "계정");
        notificationRepository.deleteAll();

        comment(authorCookie, id, "추가로 궁금한 점").andExpect(status().isCreated());

        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getUser().getId()).isEqualTo(admin.getId()));
        mvc.perform(get("/admin/overview").cookie(adminCookie)).andExpect(jsonPath("$.pendingInquiries").value(1));
    }
}
