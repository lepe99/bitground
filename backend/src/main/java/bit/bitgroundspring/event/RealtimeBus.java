package bit.bitgroundspring.event;

import bit.bitgroundspring.notification.dto.NotificationResponse;
import bit.bitgroundspring.order.dto.TradeDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 실시간 전송 요청을 모든 백엔드 서버에 알린다 (Redis Stream, 서버마다 전부 읽음: {@link RealtimeListener}).
 * SSE·WebSocket 연결은 서버마다 따로 있으므로, 어느 서버에서 생긴 알림이든 사용자가 붙어 있는 서버가 보낸다.
 * 놓쳐도 되는 전송만 담는다 (알림 자체는 DB에 저장돼 있고, 화면은 다시 읽으면 된다). 그래서 실패해도 예외를 던지지 않는다
 */
@Slf4j
@Component
public class RealtimeBus {

    public static final String USER = "USER";
    public static final String ALL = "ALL";
    public static final String TRADE = "TRADE";
    /** 스트림 길이 상한 (실시간 전송만 담으므로 최근 것만 있으면 된다) */
    private static final long MAX_LEN = 10_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String stream;

    public RealtimeBus(StringRedisTemplate redis, ObjectMapper objectMapper,
                       @Value("${events.realtime.stream}") String stream) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.stream = stream;
    }

    /** 한 사용자에게 SSE 알림 */
    public void toUser(int userId, NotificationResponse notification) {
        publish(USER, userId, notification);
    }

    /** 접속 중인 모든 사용자에게 SSE 알림 */
    public void toAll(NotificationResponse notification) {
        publish(ALL, null, notification);
    }

    /** 체결 내역 방송 (해당 종목 WebSocket 구독자) */
    public void trade(TradeDto trade) {
        publish(TRADE, null, trade);
    }

    private void publish(String kind, Integer userId, Object payload) {
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("kind", kind);
            fields.put("payload", objectMapper.writeValueAsString(payload));
            fields.put("at", String.valueOf(System.currentTimeMillis()));
            if (userId != null) {
                fields.put("userId", String.valueOf(userId));
            }
            redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));
            redis.opsForStream().trim(stream, MAX_LEN, true);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Realtime publish failed ({}): {}", kind, e.getMessage());
        }
    }
}
