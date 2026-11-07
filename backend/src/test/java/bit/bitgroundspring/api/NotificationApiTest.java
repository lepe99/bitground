package bit.bitgroundspring.api;

import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림 목록: 종류별 탭, 읽음 여부, 하나씩 읽음(내 알림만) */
class NotificationApiTest extends IntegrationTest {

    @Autowired
    NotificationRepository notificationRepository;

    private User me;
    private User other;
    private Cookie cookie;

    @BeforeEach
    void setUp() {
        me = fixtures.user(0);
        other = fixtures.user(0);
        cookie = fixtures.login(me);
    }

    private Notification save(User user, Message type) {
        return notificationRepository.save(Notification.builder().user(user).messageType(type).message(type.name()).build());
    }

    @Test
    void 탭별로_알림_종류를_거르고_읽음_여부를_준다() throws Exception {
        save(me, Message.ORDER_EXECUTION);
        save(me, Message.COMMENT);
        save(me, Message.INQUIRY);
        save(me, Message.NOTICE);
        save(me, Message.SEASON_UPDATE);

        mvc.perform(get("/notifications").cookie(cookie)).andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].isRead").value(false));
        mvc.perform(get("/notifications").cookie(cookie).param("group", "TRADE"))
                .andExpect(jsonPath("$.content[*].messageType", containsInAnyOrder("ORDER_EXECUTION")));
        mvc.perform(get("/notifications").cookie(cookie).param("group", "COMMUNITY"))
                .andExpect(jsonPath("$.content[*].messageType", containsInAnyOrder("COMMENT", "INQUIRY")));
        mvc.perform(get("/notifications").cookie(cookie).param("group", "SYSTEM"))
                .andExpect(jsonPath("$.content[*].messageType", containsInAnyOrder("NOTICE", "SEASON_UPDATE")));
        mvc.perform(get("/notifications").cookie(cookie).param("group", "WRONG")).andExpect(status().isBadRequest());
    }

    @Test
    void 누른_알림만_읽음이_되고_남의_알림은_바꿀_수_없다() throws Exception {
        Notification mine = save(me, Message.COMMENT);
        save(me, Message.NOTICE);
        Notification others = save(other, Message.COMMENT);

        mvc.perform(patch("/notifications/" + mine.getId() + "/read").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(patch("/notifications/" + others.getId() + "/read").cookie(cookie)).andExpect(status().isNoContent());

        assertThat(notificationRepository.findById(mine.getId()).orElseThrow().getIsRead()).isTrue();
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().getIsRead()).isFalse();
        mvc.perform(get("/notifications/count").cookie(cookie)).andExpect(jsonPath("$").value(1));
    }

    @Test
    void 묶인_알림은_한_번에_읽음이_되고_남의_알림은_빠진다() throws Exception {
        Notification a = save(me, Message.COMMENT);
        Notification b = save(me, Message.COMMENT);
        save(me, Message.NOTICE);
        Notification others = save(other, Message.COMMENT);

        mvc.perform(patch("/notifications/read").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + a.getId() + "," + b.getId() + "," + others.getId() + "]}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/notifications/count").cookie(cookie)).andExpect(jsonPath("$").value(1));
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().getIsRead()).isFalse();

        String tooMany = "{\"ids\":[" + "1,".repeat(100) + "1]}";
        mvc.perform(patch("/notifications/read").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(tooMany))
                .andExpect(status().isBadRequest());
    }
}
