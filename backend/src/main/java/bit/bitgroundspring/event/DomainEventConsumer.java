package bit.bitgroundspring.event;

import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.entity.OrderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 도메인 이벤트 처리 (이벤트 스트림을 구독 그룹으로 나눠 읽어, 서버가 여러 대여도 이벤트마다 한 번만 처리).
 * <ul>
 *   <li>주문 체결: 체결 내역 실시간 방송, 예약 주문이면 체결 알림 저장 후 실시간 전송</li>
 * </ul>
 * 처리가 끝난 뒤에 확인한다. 같은 이벤트가 다시 와도(재전송·넘겨받기) 알림은 이벤트 ID로 한 번만 저장되고,
 * 오래된 이벤트는 실시간으로 보내지 않는다. 새 처리(통계 등)를 붙일 때 주문 코드를 건드리지 않고 여기에 추가한다
 */
@Slf4j
@Component
public class DomainEventConsumer {

    private static final int READ_COUNT = 100;
    private static final long MAX_DELIVERIES = 5;
    /** 이보다 오래된 체결은 실시간으로 보내지 않는다 (알림은 저장) */
    private static final Duration REALTIME_WINDOW = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final RealtimeBus realtime;
    private final RedisStreamGroup events;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public DomainEventConsumer(StringRedisTemplate redis, ObjectMapper objectMapper, JdbcTemplate jdbc,
                               RealtimeBus realtime,
                               @Value("${events.domain.stream}") String stream,
                               @Value("${events.domain.group}") String group,
                               @Value("${upbit.order.execution.reclaim-idle:30s}") Duration reclaimIdle) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.realtime = realtime;
        this.events = new RedisStreamGroup(redis, stream, group, reclaimIdle, MAX_DELIVERIES);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createGroupOnStartup() {
        events.ensureGroup();
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
    }

    @Scheduled(fixedDelay = 100)
    public void consume() {
        while (!shuttingDown.get()) {
            List<MapRecord<String, Object, Object>> records = events.readNew(READ_COUNT);
            if (records.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                if (handle(record)) {
                    events.finish(record.getId());
                }
            }
        }
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void reclaimStale() {
        if (!shuttingDown.get()) {
            events.reclaimStale(READ_COUNT, this::handle);
        }
    }

    /** 처리가 끝나 확인해도 되면 true. 일시적 오류면 false (잠시 뒤 다시 처리) */
    boolean handle(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        String type = String.valueOf(fields.get("type"));
        try {
            long eventId = Long.parseLong(String.valueOf(fields.get("eventId")));
            String payload = String.valueOf(fields.get("payload"));
            switch (type) {
                case OrderExecutedEvent.TYPE -> onOrderExecuted(eventId, objectMapper.readValue(payload, OrderExecutedEvent.class));
                default -> log.warn("Unknown domain event skipped: {} ({})", type, record.getId());
            }
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException e) {
            log.error("Invalid domain event skipped: {} ({})", type, record.getId(), e);
            return true;
        } catch (RuntimeException e) {
            log.error("Domain event {} failed, will retry", record.getId(), e);
            return false;
        }
    }

    private void onOrderExecuted(long eventId, OrderExecutedEvent event) {
        boolean fresh = event.updatedAt() != null
                && event.updatedAt().isAfter(LocalDateTime.now().minus(REALTIME_WINDOW));
        if (fresh) {
            realtime.trade(event.toTrade());
        }
        if (!event.reserved()) {
            return;
        }

        // 접속하지 않은 사이 체결돼도 알림이 남도록 먼저 저장한다. 같은 이벤트가 다시 오면 저장하지 않는다
        int inserted = jdbc.update("""
                INSERT IGNORE INTO notifications (user_id, message, message_type, event_id, is_read)
                VALUES (?, ?, ?, ?, 0)
                """, event.userId(), orderMessage(event), Message.ORDER_EXECUTION.name(), eventId);
        if (inserted == 0 || !fresh) {
            return;
        }
        Long cash = jdbc.query("SELECT cash FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, event.userId());
        realtime.toUser(event.userId(), NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.ORDER_EXECUTION)
                .data(Map.of(
                        "orderType", event.orderType().name(),
                        "symbol", event.symbol(),
                        "amount", event.amount(),
                        "tradePrice", event.tradePrice(),
                        "cash", cash == null ? 0L : cash))
                .build());
    }

    static String orderMessage(OrderExecutedEvent event) {
        String side = event.orderType() == OrderType.BUY ? "매수" : "매도";
        String coin = event.symbol().contains("-") ? event.symbol().split("-")[1] : event.symbol();
        return String.format("예약 %s 주문이 체결되었습니다.\n수량 : %.7f %s\n체결 : 개당 %s원",
                side, event.amount(), coin, formatPrice(event.tradePrice()));
    }

    /** 정수면 소수점 없이, 소수면 불필요한 0을 빼고 천 단위 구분 */
    private static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        BigDecimal stripped = price.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return String.format("%,d", stripped.longValueExact());
        }
        return String.format("%,." + stripped.scale() + "f", stripped);
    }
}
