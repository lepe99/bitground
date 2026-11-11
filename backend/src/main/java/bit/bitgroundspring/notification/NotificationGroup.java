package bit.bitgroundspring.notification;

import java.util.EnumSet;
import java.util.Set;

/** 알림 목록 탭: 알림 종류를 묶은 것 */
public enum NotificationGroup {
    ALL(EnumSet.allOf(Message.class)),
    TRADE(EnumSet.of(Message.ORDER_EXECUTION)),
    COMMUNITY(EnumSet.of(Message.COMMENT, Message.INQUIRY)),
    SYSTEM(EnumSet.of(Message.NOTICE, Message.SEASON_UPDATE));

    private final Set<Message> types;

    NotificationGroup(Set<Message> types) {
        this.types = types;
    }

    public Set<Message> types() {
        return types;
    }
}
