package bit.bitgroundspring.notification.dto;

import bit.bitgroundspring.notification.Message;

import java.time.LocalDateTime;

public interface NotificationProjection {
    Integer getId();
    String getMessage();
    Message getMessageType();
    Integer getPostId();
    LocalDateTime getCreatedAt();
    Boolean getIsRead();
}
