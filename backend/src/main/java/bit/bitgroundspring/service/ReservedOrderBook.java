package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.entity.OrderType;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약 주문 대기열 (Redis)
 * <pre>
 * buy_orders:{symbol}    ZSET  member=주문 ID, score=예약가   현재가 ≤ 예약가가 되면 매수 체결
 * sell_orders:{symbol}   ZSET  member=주문 ID, score=예약가   현재가 ≥ 예약가가 되면 매도 체결
 * {execution queue}      LIST  체결 요청 JSON {"orderId", "price", "requestedAt"}
 * </pre>
 * 원본은 DB의 PENDING 주문이고 Redis는 빠른 가격 매칭용 사본이다.
 * 기동 시와 시즌 정산 후 DB 기준으로 다시 적재하므로 Redis가 비워져도 주문이 사라지지 않는다.
 */
@Slf4j
@Component
public class ReservedOrderBook {

    private static final String BUY_PREFIX = "buy_orders:";
    private static final String SELL_PREFIX = "sell_orders:";
    // 이전 구현이 남긴 키: 주문 사본(order:*)과 읽는 곳이 없던 시세(price:*). 대기열은 이제 ZSET만 쓴다
    private static final List<String> LEGACY_PATTERNS = List.of("order:*", "price:*");
    private static final int CLAIM_LIMIT = 200;

    /**
     * 가격 조건을 만족한 주문을 대기열에서 꺼내 체결 큐에 넣는다.
     * ZREM과 LPUSH를 한 번에 처리해 같은 주문이 두 번 큐에 들어가거나, 대기열·큐 어디에도 없이 사라지지 않는다.
     * KEYS: ZSET, 체결 큐 / ARGV: 최소 점수, 최대 점수, 체결 가격, 요청 시각(ms), 최대 건수
     */
    private static final RedisScript<Long> CLAIM = new DefaultRedisScript<>("""
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2], 'LIMIT', 0, ARGV[5])
            for _, id in ipairs(ids) do
                redis.call('ZREM', KEYS[1], id)
                redis.call('LPUSH', KEYS[2], '{"orderId":' .. id .. ',"price":' .. ARGV[3] .. ',"requestedAt":' .. ARGV[4] .. '}')
            end
            return #ids
            """, Long.class);

    private final StringRedisTemplate redis;
    private final OrderRepository orderRepository;
    private final String executionQueue;

    public ReservedOrderBook(StringRedisTemplate redis, OrderRepository orderRepository,
                             @Value("${upbit.order.execution.queue}") String executionQueue) {
        this.redis = redis;
        this.orderRepository = orderRepository;
        this.executionQueue = executionQueue;
    }

    public void add(Order order) {
        redis.opsForZSet().add(key(order.getOrderType(), order.getCoin().getSymbol()),
                String.valueOf(order.getId()), order.getReservePrice());
    }

    public void remove(Order order) {
        redis.opsForZSet().remove(key(order.getOrderType(), order.getCoin().getSymbol()), String.valueOf(order.getId()));
    }

    /**
     * 현재가로 체결 가능한 예약 주문을 체결 큐로 옮기고 옮긴 건수를 반환한다.
     * 매수: 예약가 ≥ 현재가, 매도: 예약가 ≤ 현재가
     */
    public long claimMatches(String symbol, double price) {
        String p = String.valueOf(price);
        String now = String.valueOf(System.currentTimeMillis());
        String limit = String.valueOf(CLAIM_LIMIT);
        Long buy = redis.execute(CLAIM, List.of(BUY_PREFIX + symbol, executionQueue), p, "+inf", p, now, limit);
        Long sell = redis.execute(CLAIM, List.of(SELL_PREFIX + symbol, executionQueue), "-inf", p, p, now, limit);
        return (buy == null ? 0 : buy) + (sell == null ? 0 : sell);
    }

    /** 체결 처리에 실패한 주문을 아직 PENDING이면 대기열에 되돌린다 */
    @Transactional(readOnly = true)
    public void restore(int orderId) {
        orderRepository.findById(orderId)
                .filter(order -> order.getStatus() == Status.PENDING)
                .ifPresent(this::add);
    }

    /** DB의 PENDING 주문으로 대기열을 다시 만든다 (Redis 재시작·유실 대비) */
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
        return pending.size();
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
