package bit.bitgroundspring.security.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

/**
 * 사용자 단위 액세스 토큰 무효화
 *
 * 액세스 토큰(JWT)은 서버에 저장하지 않으므로, 탈퇴처럼 즉시 끊어야 하는 경우
 * "이 시각 이전에 발급된 토큰은 무효"라는 기록을 남긴다.
 * 기록은 액세스 토큰 만료 시간만큼만 유지하면 된다(그 뒤에는 이전 토큰이 모두 만료됨).
 */
@Slf4j
@Component
public class AccessTokenBlocklist {

    private static final String PREFIX = "auth:revoked:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public AccessTokenBlocklist(StringRedisTemplate redis, @Value("${jwt.expired_time}") long accessTokenMillis) {
        this.redis = redis;
        this.ttl = Duration.ofMillis(accessTokenMillis);
    }

    /** 지금까지 발급된 사용자의 액세스 토큰을 모두 무효로 만든다 */
    public void revokeAll(int userId) {
        redis.opsForValue().set(PREFIX + userId, String.valueOf(System.currentTimeMillis()), ttl);
    }

    /**
     * 무효화된 토큰인지 확인한다.
     * JWT의 iat는 초 단위라, 무효화와 같은 초에 발급된 토큰도 무효로 본다.
     * Redis 장애 시에는 인증을 막지 않는다(서비스 전체가 401이 되지 않도록). 토큰 만료 시간이 상한이다.
     */
    public boolean isRevoked(int userId, Date issuedAt) {
        try {
            String revokedAt = redis.opsForValue().get(PREFIX + userId);
            if (revokedAt == null || issuedAt == null) {
                return revokedAt != null;
            }
            return issuedAt.getTime() / 1000 <= Long.parseLong(revokedAt) / 1000;
        } catch (RuntimeException e) {
            log.warn("액세스 토큰 무효화 기록 조회 실패 - userId: {}, {}", userId, e.toString());
            return false;
        }
    }
}
