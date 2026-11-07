package bit.bitgroundspring.api;

import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림 설정(종류별 받기·토스트)과 알림 지우기 */
class NotificationSettingsTest extends IntegrationTest {

    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    NotificationService notificationService;

    private User me;
    private User other;
    private Cookie cookie;
    private Cookie otherCookie;

    @BeforeEach
    void setUp() {
        me = fixtures.user(0);
        other = fixtures.user(0);
        cookie = fixtures.login(me);
        otherCookie = fixtures.login(other);
    }

    private void settings(Cookie c, String json) throws Exception {
        mvc.perform(put("/notifications/settings").cookie(c).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private Notification save(User user, Message type, boolean read) {
        Notification n = Notification.builder().user(user).messageType(type).message(type.name()).build();
        n.setIsRead(read);
        return notificationRepository.save(n);
    }

    private long countOf(User user, Message type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUser().getId().equals(user.getId()) && n.getMessageType() == type).count();
    }

    @Test
    void 설정한_적_없으면_모두_켜져_있고_보낸_항목만_바뀐다() throws Exception {
        mvc.perform(get("/notifications/settings").cookie(cookie))
                .andExpect(jsonPath("$.orderExecution").value(true))
                .andExpect(jsonPath("$.toast").value(true));

        settings(cookie, "{\"comment\":false,\"toast\":false}");

        mvc.perform(get("/notifications/settings").cookie(cookie))
                .andExpect(jsonPath("$.comment").value(false))
                .andExpect(jsonPath("$.toast").value(false))
                .andExpect(jsonPath("$.notice").value(true))
                .andExpect(jsonPath("$.orderExecution").value(true));
    }

    @Test
    void 댓글_알림을_끄면_내_글에_댓글이_달려도_알림이_없다() throws Exception {
        String json = mvc.perform(post("/posts/form").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\",\"category\":\"CHAT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int postId = JsonPath.read(json, "$.id");
        String comment = "{\"postId\":%d,\"content\":\"댓글\"}".formatted(postId);

        mvc.perform(post("/comments").cookie(otherCookie).contentType(MediaType.APPLICATION_JSON).content(comment));
        assertThat(countOf(me, Message.COMMENT)).isEqualTo(1);

        settings(cookie, "{\"comment\":false}");
        mvc.perform(post("/comments").cookie(otherCookie).contentType(MediaType.APPLICATION_JSON).content(comment));
        assertThat(countOf(me, Message.COMMENT)).isEqualTo(1);
    }

    @Test
    void 공지_알림을_끈_사용자에게는_공지_알림을_저장하지_않는다() throws Exception {
        settings(cookie, "{\"notice\":false}");

        notificationService.saveForAllUsers(Message.NOTICE, "공지");
        notificationService.saveForAllUsers(Message.SEASON_UPDATE, "시즌");

        assertThat(countOf(me, Message.NOTICE)).isZero();
        assertThat(countOf(other, Message.NOTICE)).isEqualTo(1);
        assertThat(countOf(me, Message.SEASON_UPDATE)).isEqualTo(1);
    }

    @Test
    void 고른_알림과_읽은_알림을_지우고_남의_알림은_지우지_않는다() throws Exception {
        Notification a = save(me, Message.COMMENT, false);
        Notification b = save(me, Message.COMMENT, false);
        Notification read = save(me, Message.NOTICE, true);
        Notification keep = save(me, Message.NOTICE, false);
        Notification others = save(other, Message.COMMENT, true);

        mvc.perform(delete("/notifications").cookie(cookie).param("ids", a.getId() + "," + b.getId() + "," + others.getId()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/notifications/read").cookie(cookie)).andExpect(status().isNoContent());

        assertThat(notificationRepository.findAll()).extracting(Notification::getId)
                .containsExactlyInAnyOrder(keep.getId(), others.getId());
        assertThat(read.getId()).isNotNull();
    }
}
