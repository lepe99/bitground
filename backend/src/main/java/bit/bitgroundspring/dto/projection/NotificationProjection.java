package bit.bitgroundspring.dto.projection;

import bit.bitgroundspring.entity.Message;

import java.time.LocalDateTime;

public interface NotificationProjection {
    Integer getId();
    String getMessage();
    Message getMessageType();
    Integer getPostId();
    LocalDateTime getCreatedAt();
}
