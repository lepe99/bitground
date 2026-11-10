package bit.bitgroundspring.config;

import bit.bitgroundspring.market.dto.CoinMarket;
import bit.bitgroundspring.market.dto.NewsResponse;
import bit.bitgroundspring.season.dto.RankEntry;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.List;

/**
 * 조회 결과 캐시 (Redis).
 * <ul>
 *   <li>{@value #NEWS}: 네이버 뉴스 검색. 요청마다 외부 API(하루 호출 한도)를 부르던 것을 10분간 재사용</li>
 *   <li>{@value #RANKINGS}: 시즌 랭킹. 스케줄러가 매시 정각에 다시 계산하므로 그 사이에는 같은 결과.
 *       스케줄러가 갱신을 알리면 비우고({@link bit.bitgroundspring.season.RankingCache}), 알림이 빠져도 1시간이면 만료된다</li>
 *   <li>{@value #COIN_MARKETS}: 원화 마켓 코인 목록(거래대금·유의·주의). 스케줄러가 매시 갱신하고 그 알림에 비운다. TTL 10분</li>
 *   <li>{@value #HOURLY_CLOSES}: 종목별 1시간봉 종가(추이 그래프). 업비트를 서버가 대신 불러 5분간 재사용</li>
 * </ul>
 * 화면이 업비트 REST를 직접 부르지 않게 하는 캐시들이다 (브라우저 요청은 Origin 헤더 때문에 약 10초에 1회로 제한됨).
 * 값은 캐시별로 정해진 타입의 JSON으로 저장한다 (타입 정보를 값에 싣는 범용 직렬화를 쓰지 않음).
 * Redis는 토큰·이벤트 스트림과 함께 쓰므로 모든 캐시에 TTL을 두고, 결과 크기가 작은 것만 캐시한다.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String NEWS = "news";
    public static final String RANKINGS = "rankings";
    public static final String COIN_MARKETS = "coinMarkets";
    public static final String HOURLY_CLOSES = "hourlyCloses";

    @Bean
    RedisCacheManagerBuilderCustomizer cacheConfigurations(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        return builder -> builder
                // 캐시 비우기(allEntries)를 KEYS 대신 SCAN으로 (KEYS는 키가 많을 때 Redis를 멈춘다)
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(100)))
                .withCacheConfiguration(NEWS, config(Duration.ofMinutes(10),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, NewsResponse.class), objectMapper))
                .withCacheConfiguration(RANKINGS, config(Duration.ofHours(1),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, RankEntry.class), objectMapper))
                .withCacheConfiguration(COIN_MARKETS, config(Duration.ofMinutes(10),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, CoinMarket.class), objectMapper))
                .withCacheConfiguration(HOURLY_CLOSES, config(Duration.ofMinutes(5),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class), objectMapper));
    }

    private static RedisCacheConfiguration config(Duration ttl, JavaType type, ObjectMapper objectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith("cache:")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type)));
    }

    /**
     * Redis 장애가 조회 실패로 번지지 않게: 캐시를 읽지 못하면 원본을 조회하고, 저장·삭제 실패는 기록만 한다.
     * (삭제 실패로 남은 값은 TTL이 지나면 사라진다)
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[cache] {} 읽기 실패, 원본 조회: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[cache] {} 저장 실패: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[cache] {} 삭제 실패: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[cache] {} 비우기 실패: {}", cache.getName(), e.toString());
            }
        };
    }
}
