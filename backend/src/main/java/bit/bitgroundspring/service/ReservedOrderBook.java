package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.entity.OrderType;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 예약 주문 대기열 (Redis)
 * <pre>
 * buy_orders:{symbol}    ZSET  member=주문 ID, score=예약가   현재가 ≤ 예약가가 되면 매수 체결
 * sell_orders:{symbol}   ZSET  member=주문 ID, score=예약가   현재가 ≥ 예약가가 되면 매도 체결
 * {execution stream}     STREAM 체결 요청 {orderId, price, requestedAt}. 구독 그룹으로 읽고 체결 커밋 뒤 확인(ack)
 * order_book:epoch       STRING 대기열을 DB 기준으로 만든 표시. 없어졌으면 Redis 데이터가 사라진 것
 * </pre>
 * 원본은 DB의 PENDING 주문이고 Redis는 빠른 가격 매칭용 사본이다.
 * 기동 시, 시즌 정산 후, Redis 데이터가 사라진 것을 알아챘을 때 DB 기준으로 다시 만들고,
 * 주기적으로 DB와 대조해 빠진 주문을 채운다 ({@link OrderBookWatchdog}). 그래서 Redis가 죽어도 주문은 사라지지 않고,
 * 체결 대기 중이던 주문은 다음에 가격이 닿을 때 체결된다.
 */
@Slf4j
@Component
public class ReservedOrderBook {

    private static final String BUY_PREFIX = "buy_orders:";
    private static final String SELL_PREFIX = "sell_orders:";
    // 이전 구현이 남긴 키: 주문 사본(order:*)과 읽는 곳이 없던 시세(price:*). 대기열은 이제 ZSET만 쓴다
    private static final List<String> LEGACY_PATTERNS = List.of("order:*", "price:*", "order-execution-queue");
    /** 대기열을 DB 기준으로 만든 표시. Redis가 재시작돼 데이터가 사라지면 함께 없어진다 */
    static final String EPOCH_KEY = "order_book:epoch";
    /** 체결 스트림 길이 상한 (처리한 메시지는 지우므로 평소엔 거의 비어 있다) */
    private static final int STREAM_MAX_LEN = 100_000;
    private static final int CLAIM_LIMIT = 200;

    /**
     * 가격 조건을 만족한 주문을 대기열에서 꺼내 체결 스트림에 넣는다.
     * ZREM과 XADD를 한 번에 처리해 같은 주문이 두 번 들어가거나, 대기열·스트림 어디에도 없이 사라지지 않는다.
     * KEYS: ZSET, 체결 스트림 / ARGV: 최소 점수, 최대 점수, 체결 가격, 요청 시각(ms), 최대 건수, 스트림 길이 상한
     */
    private static final RedisScript<Long> CLAIM = new DefaultRedisScript<>("""
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2], 'LIMIT', 0, ARGV[5])
            for _, id in ipairs(ids) do
                redis.call('ZREM', KEYS[1], id)
                redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[6], '*', 'orderId', id, 'price', ARGV[3], 'requestedAt', ARGV[4])
            end
            return #ids
            """, Long.class);

    private final StringRedisTemplate redis;
    private final OrderRepository orderRepository;
    private final String executionStream;

    public ReservedOrderBook(StringRedisTemplate redis, OrderRepository orderRepository,
                             @Value("${upbit.order.execution.stream}") String executionStream) {
        this.redis = redis;
        this.orderRepository = orderRepository;
        this.executionStream = executionStream;
    }

    public void add(Order order) {
        redis.opsForZSet().add(key(order.getOrderType(), order.getCoin().getSymbol()),
                String.valueOf(order.getId()), order.getReservePrice().doubleValue());
    }

    public void remove(Order order) {
        redis.opsForZSet().remove(key(order.getOrderType(), order.getCoin().getSymbol()), String.valueOf(order.getId()));
    }

    /**
     * 현재가로 체결 가능한 예약 주문을 체결 스트림으로 옮기고 옮긴 건수를 반환한다.
     * 매수: 예약가 ≥ 현재가, 매도: 예약가 ≤ 현재가
     */
    public long claimMatches(String symbol, double price) {
        String p = String.valueOf(price);
        String now = String.valueOf(System.currentTimeMillis());
        String limit = String.valueOf(CLAIM_LIMIT);
        String maxLen = String.valueOf(STREAM_MAX_LEN);
        Long buy = redis.execute(CLAIM, List.of(BUY_PREFIX + symbol, executionStream), p, "+inf", p, now, limit, maxLen);
        Long sell = redis.execute(CLAIM, List.of(SELL_PREFIX + symbol, executionStream), "-inf", p, p, now, limit, maxLen);
        return (buy == null ? 0 : buy) + (sell == null ? 0 : sell);
    }

    /** 체결 처리에 실패한 주문을 아직 PENDING이면 대기열에 되돌린다 */
    @Transactional(readOnly = true)
    public void restore(int orderId) {
        orderRepository.findById(orderId)
                .filter(order -> order.getStatus() == Status.PENDING)
                .ifPresent(this::add);
    }

    /**
     * DB의 PENDING 주문으로 대기열을 다시 만든다 (Redis 재시작·유실 대비).
     * 체결 스트림에 남은 요청은 지우지 않는다: 같은 주문이 다시 체결 요청돼도 체결 시 PENDING인지 확인하므로 한 번만 체결된다
     */
    @Transactional(readOnly = true)
    public int rebuild() {
        List<String> stale = new ArrayList<>();
        for (String pattern : List.of(BUY_PREFIX + "*", SELL_PREFIX + "*")) {
            stale.addAll(scan(pattern));
        }
        LEGACY_PATTERNS.forEach(pattern -> stale.addAll(scan(pattern)));
        if (!stale.isEmpty()) {
            redis.delete(stale);
        }

        List<Order> pending = orderRepository.findAllPendingWithCoin();
        pending.forEach(this::add);
        redis.opsForValue().set(EPOCH_KEY, String.valueOf(System.currentTimeMillis()));
        return pending.size();
    }

    /**
     * Redis 데이터가 사라졌으면(재시작 등으로 표시 키가 없음) 대기열을 다시 만든다. 다시 만들었으면 true.
     * 여러 서버가 동시에 알아채도 표시 키를 먼저 잡은 한 곳만 다시 만든다
     */
    public boolean rebuildIfReset() {
        Boolean reset = redis.opsForValue().setIfAbsent(EPOCH_KEY, "rebuilding");
        if (!Boolean.TRUE.equals(reset)) {
            return false;
        }
        int count = rebuild();
        log.warn("Redis data was reset: reserved order book rebuilt from DB ({} pending orders)", count);
        return true;
    }

    /** 대조 결과: DB에는 PENDING인데 Redis 어디에도 없어 채운 주문 수, DB에선 끝났는데 대기열에 남아 지운 주문 수 */
    public record ReconcileResult(int restored, int removed) {
    }

    /**
     * DB의 PENDING 주문과 Redis(대기열 + 체결 스트림)를 비교해 맞춘다.
     * Redis를 먼저 읽고 DB를 나중에 읽는다: 대기열에 들어가는 주문은 이미 DB에 커밋돼 있고,
     * 그사이 체결·취소된 주문은 그 처리에서 대기열에서도 빠지므로, 대조와 겹쳐도 ZADD·ZREM이 결과를 바꾸지 않는다
     */
    @Transactional(readOnly = true)
    public ReconcileResult reconcile() {
        Map<Integer, String> inBook = new HashMap<>();
        for (String pattern : List.of(BUY_PREFIX + "*", SELL_PREFIX + "*")) {
            for (String key : scan(pattern)) {
                Set<String> members = redis.opsForZSet().range(key, 0, -1);
                if (members != null) {
                    members.forEach(id -> inBook.put(Integer.valueOf(id), key));
                }
            }
        }
        Set<Integer> inStream = new HashSet<>();
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(executionStream, Range.unbounded());
        if (records != null) {
            records.forEach(r -> inStream.add(Integer.valueOf(String.valueOf(r.getValue().get("orderId")))));
        }

        Map<Integer, Order> pending = new HashMap<>();
        orderRepository.findAllPendingWithCoin().forEach(o -> pending.put(o.getId(), o));

        int restored = 0;
        for (Order order : pending.values()) {
            if (!inBook.containsKey(order.getId()) && !inStream.contains(order.getId())) {
                add(order);
                restored++;
            }
        }
        int removed = 0;
        for (Map.Entry<Integer, String> e : inBook.entrySet()) {
            if (!pending.containsKey(e.getKey())) {
                redis.opsForZSet().remove(e.getValue(), String.valueOf(e.getKey()));
                removed++;
            }
        }
        if (restored > 0 || removed > 0) {
            log.warn("Reserved order book drifted from DB: restored {} missing orders, removed {} finished orders", restored, removed);
        }
        return new ReconcileResult(restored, removed);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            int count = rebuild();
            log.info("Reserved order book rebuilt from DB: {} pending orders", count);
        } catch (RuntimeException e) {
            // Redis가 늦게 뜨는 경우에도 앱은 기동한다. 체결 대기 주문은 다음 재기동·시즌 정산 때 다시 적재된다
            log.error("Failed to rebuild reserved order book", e);
        }
    }

    private List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    private static String key(OrderType type, String symbol) {
        return (type == OrderType.BUY ? BUY_PREFIX : SELL_PREFIX) + symbol;
    }
}
