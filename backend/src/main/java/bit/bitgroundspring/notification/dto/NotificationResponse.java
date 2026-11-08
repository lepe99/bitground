package bit.bitgroundspring.notification.dto;

import bit.bitgroundspring.notification.Message;
import bit.bitgroundspring.notification.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    MessageType messageType;
    Message message;
    Map<String, Object> data;
}
