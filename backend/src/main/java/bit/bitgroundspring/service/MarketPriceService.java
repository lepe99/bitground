package bit.bitgroundspring.service;

import bit.bitgroundspring.client.UpbitClient;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 매매에 쓰는 현재가.
 * 업비트 실시간 시세(WebSocket)로 받은 체결가를 Redis에 종목별로 남겨 두고, 시세 연결이 살아 있으면 그 값을 쓴다.
 * (기존: 시장가 주문마다 업비트 REST를 불러 사용자가 몰리면 IP당 요청 제한에 걸릴 수 있었음)
 * 연결이 끊겼거나 그 종목 값이 없을 때만 REST를 부르고, 같은 종목은 잠깐 재사용하며 전체 호출 수도 제한한다.
 * Redis에 두므로 서버가 여러 대여도 같은 값을 쓴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    /** 종목별 최신 체결가: field = 종목, value = "가격|체결 시각(ms)" */
    static final String LATEST_KEY = "market:latest_price";
    /** 시세를 받고 있다는 표시. 시세가 들어올 때마다 늘리고, 끊기면 잠시 뒤 사라진다 */
    static final String ALIVE_KEY = "market:ticker_alive";
    private static final long ALIVE_TTL_MS = 10_000;
    /** REST로 받은 가격을 재사용하는 시간 (같은 종목에 주문이 몰려도 이 동안은 한 번만 부름) */
    private static final String REST_PREFIX = "market:rest_price:";
    private static final Duration REST_REUSE = Duration.ofSeconds(1);
    /** 업비트 REST 호출 상한 (초당, 모든 서버 합계). 업비트 시세 조회 제한(IP당 초당 10회)보다 낮게 둔다 */
    private static final String REST_CALLS_PREFIX = "market:rest_calls:";
    static final int REST_CALLS_PER_SECOND = 8;

    /**
     * 시세 하나 남기기 (한 번의 왕복으로): 시세를 받고 있다는 표시를 늘리고,
     * 늦게 처리된 예전 시세가 새 시세를 덮지 않도록 체결 시각이 더 늦을 때만 가격을 바꾼다.
     * KEYS: 최신가 해시, 수신 표시 / ARGV: 종목, 가격, 체결 시각(ms), 표시 유지 시간(ms)
     */
    private static final RedisScript<Long> RECORD = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[4])
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if current then
                local ts = tonumber(string.match(current, '|(%d+)$'))
                if ts and ts > tonumber(ARGV[3]) then
                    return 0
                end
            end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2] .. '|' .. ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final UpbitClient upbitClient;

    /** 실시간 시세 하나를 남긴다 (WebSocket 수신) */
    public void record(String symbol, BigDecimal price, long tradeTimestampMs) {
        redis.execute(RECORD, List.of(LATEST_KEY, ALIVE_KEY), symbol, price.toPlainString(),
                String.valueOf(tradeTimestampMs), String.valueOf(ALIVE_TTL_MS));
    }

    /**
     * 종목의 현재가.
     * 시세 연결이 살아 있으면 남겨 둔 마지막 체결가를 쓴다 (거래가 뜸한 종목도 마지막 체결가가 곧 현재가이고,
     * 다시 연결되면 업비트가 모든 종목의 현재 시세를 먼저 보내 준다)
     */
    public BigDecimal currentPrice(String symbol) {
        if (Boolean.TRUE.equals(redis.hasKey(ALIVE_KEY))) {
            Object stored = redis.opsForHash().get(LATEST_KEY, symbol);
            if (stored != null) {
                return new BigDecimal(stored.toString().substring(0, stored.toString().indexOf('|')));
            }
        }
        return fromRest(symbol);
    }

    private BigDecimal fromRest(String symbol) {
        String reuseKey = REST_PREFIX + symbol;
        String reused = redis.opsForValue().get(reuseKey);
        if (reused != null) {
            return new BigDecimal(reused);
        }
        String callsKey = REST_CALLS_PREFIX + (System.currentTimeMillis() / 1000);
        Long calls = redis.opsForValue().increment(callsKey);
        if (calls != null && calls == 1) {
            redis.expire(callsKey, Duration.ofSeconds(2));
        }
        if (calls != null && calls > REST_CALLS_PER_SECOND) {
            log.warn("업비트 현재가 조회 한도 초과로 주문을 받지 않음: {}", symbol);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "지금은 시세를 확인할 수 없어요. 잠시 후 다시 시도해 주세요.");
        }
        BigDecimal price = upbitClient.currentPrice(symbol);
        redis.opsForValue().set(reuseKey, price.toPlainString(), REST_REUSE);
        return price;
    }
}
