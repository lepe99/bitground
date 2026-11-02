package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.entity.Message;
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
