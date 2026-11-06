package bit.bitgroundspring.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 아웃박스 전달기: 아직 보내지 않은 이벤트를 이벤트 스트림(Redis Stream)에 넣고 보냈다고 표시한다.
 * <ul>
 *   <li>스트림에 넣은 뒤 표시하므로, 그 사이에 죽으면 같은 이벤트를 다시 보낸다 (받는 쪽이 이벤트 ID로 중복을 거른다)</li>
 *   <li>여러 서버가 동시에 돌아도 행 잠금(SKIP LOCKED)으로 같은 이벤트를 나눠 갖지 않는다</li>
 *   <li>Redis 데이터가 사라지면 최근 이벤트를 다시 보낸다 ({@link #republishSince})</li>
 * </ul>
 */
@Slf4j
@Component
public class OutboxRelay {

    static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final TransactionTemplate tx;
    private final String stream;

    public OutboxRelay(JdbcTemplate jdbc, StringRedisTemplate redis, TransactionTemplate tx,
                       @Value("${events.domain.stream}") String stream) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.tx = tx;
        this.stream = stream;
    }

    /** 커밋 직후 깨우기 (호출한 요청 스레드를 붙잡지 않는다) */
    @Async
    public void publishSoon() {
        publishPending();
    }

    @Scheduled(fixedDelay = 1_000, initialDelay = 1_000)
    public void publishPendingOnSchedule() {
        publishPending();
    }

    /** 보내지 않은 이벤트를 모두 보낸다. 보낸 수 */
    public int publishPending() {
        int total = 0;
        try {
            int sent;
            do {
                sent = publishBatch();
                total += sent;
            } while (sent == BATCH);
        } catch (RuntimeException e) {
            // Redis가 죽어 있으면 이벤트는 아웃박스에 남고 다음 실행에서 보낸다
            log.warn("Outbox publish failed, will retry: {}", e.getMessage());
        }
        return total;
    }

    private int publishBatch() {
        Integer sent = tx.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, event_type, payload FROM outbox_events
                    WHERE published_at IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                    """, BATCH);
            if (rows.isEmpty()) {
                return 0;
            }
            for (Map<String, Object> row : rows) {
                redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(Map.of(
                        "eventId", String.valueOf(row.get("id")),
                        "type", String.valueOf(row.get("event_type")),
                        "payload", String.valueOf(row.get("payload")))));
            }
            // 잠근 행만 표시한다 (사이의 다른 서버가 잠근 행은 건드리지 않음). ID는 DB에서 읽은 숫자
            String ids = rows.stream().map(r -> String.valueOf(((Number) r.get("id")).longValue()))
                    .collect(Collectors.joining(","));
            jdbc.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP(6) WHERE id IN (" + ids + ")");
            return rows.size();
        });
        return sent == null ? 0 : sent;
    }

    /**
     * Redis 데이터가 사라졌을 때: 이 시간 안에 보낸 이벤트를 다시 보낸다 (스트림에서 처리되기 전에 사라졌을 수 있음).
     * 받는 쪽은 이미 처리한 이벤트를 이벤트 ID로 거르고, 오래된 체결 방송은 보내지 않는다
     */
    public int republishSince(Duration window) {
        // 시각은 DB 기준으로 비교한다 (created_at은 DB가 넣은 값이라 서버 시간대와 섞지 않는다)
        int n = jdbc.update("""
                UPDATE outbox_events SET published_at = NULL
                WHERE published_at IS NOT NULL AND created_at >= NOW(6) - INTERVAL ? SECOND
                """, window.toSeconds());
        if (n > 0) {
            log.warn("Redis data was reset: republishing {} recent outbox events", n);
            publishPending();
        }
        return n;
    }

    /** 보낸 지 오래된 이벤트 정리 (매일 4시) */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeOld() {
        int n = jdbc.update("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < NOW(6) - INTERVAL 7 DAY");
        if (n > 0) {
            log.info("Purged {} published outbox events", n);
        }
    }
}
