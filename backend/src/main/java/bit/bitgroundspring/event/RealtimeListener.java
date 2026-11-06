package bit.bitgroundspring.event;

import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.handler.OrderHistoryWebSocketHandler;
import bit.bitgroundspring.service.UserSseEmitters;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 이 서버에 연결된 사용자에게 실시간 전송을 내보낸다 ({@link RealtimeBus}가 넣은 메시지를 서버마다 전부 읽음).
 * 구독 그룹을 쓰지 않고 서버마다 마지막으로 읽은 위치만 기억한다: 모든 서버가 모든 메시지를 봐야 하기 때문.
 * 기동 시점 이후 메시지만 보내고, 오래된 메시지(밀렸거나 재전송된 것)는 버린다
 */
@Slf4j
@Component
public class RealtimeListener {

    private static final int READ_COUNT = 500;
    /** 이보다 오래된 실시간 전송은 보내지 않는다 (지난 체결을 새 체결처럼 보이지 않게) */
    private static final long MAX_AGE_MILLIS = 30_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UserSseEmitters sse;
    private final OrderHistoryWebSocketHandler trades;
    private final String stream;
    private volatile String lastId;

    public RealtimeListener(StringRedisTemplate redis, ObjectMapper objectMapper, UserSseEmitters sse,
                            OrderHistoryWebSocketHandler trades, @Value("${events.realtime.stream}") String stream) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.sse = sse;
        this.trades = trades;
        this.stream = stream;
    }

    @Scheduled(fixedDelay = 100)
    public void poll() {
        try {
            if (lastId == null) {
                lastId = latestId();
            }
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    StreamReadOptions.empty().count(READ_COUNT), StreamOffset.create(stream, ReadOffset.from(lastId)));
            if (records == null) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                lastId = record.getId().getValue();
                deliver(record.getValue());
            }
        } catch (RuntimeException e) {
            log.debug("Realtime poll failed: {}", e.getMessage());
        }
    }

    /** 지금 스트림의 마지막 위치 (그 뒤부터 읽는다). 비어 있으면 처음부터 */
    private String latestId() {
        List<MapRecord<String, Object, Object>> last = redis.opsForStream().reverseRange(stream, Range.unbounded(), Limit.limit().count(1));
        return last == null || last.isEmpty() ? "0-0" : last.get(0).getId().getValue();
    }

    private void deliver(Map<Object, Object> fields) {
        try {
            long at = Long.parseLong(String.valueOf(fields.get("at")));
            if (System.currentTimeMillis() - at > MAX_AGE_MILLIS) {
                return;
            }
            String payload = String.valueOf(fields.get("payload"));
            switch (String.valueOf(fields.get("kind"))) {
                case RealtimeBus.USER -> sse.sendJsonToUser(Integer.parseInt(String.valueOf(fields.get("userId"))), payload);
                case RealtimeBus.ALL -> sse.sendJsonToAllLocal(payload);
                case RealtimeBus.TRADE -> trades.broadcast(objectMapper.readValue(payload, TradeDto.class));
                default -> log.warn("Unknown realtime message: {}", fields);
            }
        } catch (Exception e) {
            log.warn("Realtime delivery failed: {}", e.getMessage());
        }
    }
}
