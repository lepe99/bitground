package bit.bitgroundspring.dto.projection;

import bit.bitgroundspring.dto.response.Message;

import java.time.LocalDateTime;

public interface NotificationProjection {
    Integer getId();
    String getMessage();
    Message getMessageType();
    LocalDateTime getCreatedAt();
}
