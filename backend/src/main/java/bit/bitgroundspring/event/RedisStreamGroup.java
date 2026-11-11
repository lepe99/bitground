package bit.bitgroundspring.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Redis Stream 하나를 구독 그룹으로 나눠 처리하는 공통 동작.
 * <ul>
 *   <li>그룹이 없으면 만든다 (첫 기동, Redis 재시작으로 데이터가 사라짐)</li>
 *   <li>처리가 끝난 메시지만 확인(ack)하고 지운다. 확인 전에 서버가 죽으면 메시지가 남는다</li>
 *   <li>가져간 뒤 오래 확인이 없는 메시지는 넘겨받아 다시 처리하고, 너무 많이 실패한 것은 버린다</li>
 * </ul>
 * 같은 메시지가 두 번 처리될 수 있으므로(넘겨받기) 처리하는 쪽은 중복에 안전해야 한다
 */
@Slf4j
public class RedisStreamGroup {

    private final StringRedisTemplate redis;
    private final String stream;
    private final String group;
    private final String consumer;
    private final Duration reclaimIdle;
    private final long maxDeliveries;

    public RedisStreamGroup(StringRedisTemplate redis, String stream, String group, Duration reclaimIdle, long maxDeliveries) {
        this.redis = redis;
        this.stream = stream;
        this.group = group;
        this.consumer = consumerName();
        this.reclaimIdle = reclaimIdle;
        this.maxDeliveries = maxDeliveries;
    }

    /** 이 서버의 구독자 이름 (확인하지 못한 메시지를 누가 들고 있는지 구분) */
    static String consumerName() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                host = "backend";
            }
        }
        return host + "-" + ProcessHandle.current().pid();
    }

    public void ensureGroup() {
        try {
            redis.execute((RedisCallback<Object>) c -> c.streamCommands().xGroupCreate(
                    stream.getBytes(StandardCharsets.UTF_8), group, ReadOffset.from("0"), true));
            log.info("Stream group created: {}/{}", stream, group);
        } catch (RuntimeException e) {
            if (!causedBy(e, "BUSYGROUP")) { // 이미 있음 (다른 서버나 앞선 호출이 만듦)
                log.error("Failed to create stream group {}/{}", stream, group, e);
            }
        }
    }

    /** 이 서버 앞으로 새로 들어온 메시지 (그룹이 없으면 만들고 빈 목록) */
    public List<MapRecord<String, Object, Object>> readNew(int count) {
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(count),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()));
            return records == null ? List.of() : records;
        } catch (RuntimeException e) {
            if (causedBy(e, "NOGROUP")) {
                ensureGroup();
            } else {
                log.error("Failed to read stream {}", stream, e);
            }
            return List.of();
        }
    }

    /** 처리가 끝난 메시지 확인 + 삭제 (스트림이 계속 커지지 않게) */
    public void finish(RecordId id) {
        redis.opsForStream().acknowledge(stream, group, id);
        redis.opsForStream().delete(stream, id);
    }

    /**
     * 가져간 뒤 reclaimIdle 동안 확인이 없는 메시지를 넘겨받아 handler로 처리한다.
     * handler가 true를 돌려주면 확인한다. maxDeliveries번 넘게 넘겨받은 메시지는 버린다
     */
    public void reclaimStale(int count, Predicate<MapRecord<String, Object, Object>> handler) {
        try {
            PendingMessages pending = redis.opsForStream().pending(stream, group, Range.unbounded(), count);
            List<RecordId> stale = new ArrayList<>();
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(reclaimIdle) < 0) {
                    continue;
                }
                if (message.getTotalDeliveryCount() >= maxDeliveries) {
                    log.error("Stream message {} in {} dropped after {} deliveries", message.getId(), stream, message.getTotalDeliveryCount());
                    finish(message.getId());
                    continue;
                }
                stale.add(message.getId());
            }
            if (stale.isEmpty()) {
                return;
            }
            List<MapRecord<String, Object, Object>> claimed = redis.opsForStream()
                    .claim(stream, group, consumer, reclaimIdle, stale.toArray(RecordId[]::new));
            log.warn("Reclaimed {} unacknowledged messages in {}", claimed.size(), stream);
            for (MapRecord<String, Object, Object> record : claimed) {
                if (handler.test(record)) {
                    finish(record.getId());
                }
            }
        } catch (RuntimeException e) {
            if (!causedBy(e, "NOGROUP")) {
                log.error("Failed to reclaim messages in {}", stream, e);
            }
        }
    }

    /** Redis 오류 코드는 감싼 예외의 원인 쪽 메시지에 있다 */
    static boolean causedBy(Throwable e, String redisError) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains(redisError)) {
                return true;
            }
        }
        return false;
    }
}
