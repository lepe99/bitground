package bit.bitgroundspring.security.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 리프레시 토큰 저장소 (Redis)
 *
 * 로그인 한 번이 토큰 패밀리 하나가 되고, 재발급할 때마다 패밀리 안에서 토큰이 교체된다(Refresh Token Rotation).
 * <pre>
 * refresh:token:{token}   HASH  userId, familyId, rotatedAt(교체된 경우)   TTL = 리프레시 만료 시간
 * refresh:family:{id}     STRING 패밀리의 현재 유효 토큰                     TTL = 리프레시 만료 시간
 * refresh:user:{userId}   SET   사용자의 패밀리 ID 목록 (탈퇴 시 전체 폐기)    TTL = 리프레시 만료 시간
 * </pre>
 * 이미 교체된 토큰이 다시 들어오면 탈취로 보고 패밀리 전체를 폐기한다(OAuth 2.0 Security BCP 재사용 탐지).
 * 같은 브라우저의 동시 요청처럼 교체 직후 짧은 시간(grace) 안에 들어온 경우는 폐기하지 않는다.
 * 여러 키를 함께 바꾸므로 모두 Lua 스크립트로 원자적으로 처리한다.
 */
@Service
public class RefreshTokenService {

    static final String PREFIX = "refresh:";
    private static final long REUSE_GRACE_MILLIS = 10_000;

    private static final RedisScript<String> ISSUE = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'familyId', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[4])
            return 'OK'
            """, String.class);

    // KEYS: 기존 토큰 키, 새 토큰 키 / ARGV: 기존 토큰, 새 토큰, TTL(ms), 현재 시각(ms), grace(ms), prefix
    private static final RedisScript<String> ROTATE = new DefaultRedisScript<>("""
            local uid = redis.call('HGET', KEYS[1], 'userId')
            if not uid then return 'INVALID' end
            local fid = redis.call('HGET', KEYS[1], 'familyId')
            local familyKey = ARGV[6] .. 'family:' .. fid
            local current = redis.call('GET', familyKey)
            if not current then return 'INVALID' end
            if current ~= ARGV[1] then
                local rotatedAt = tonumber(redis.call('HGET', KEYS[1], 'rotatedAt') or '0')
                if tonumber(ARGV[4]) - rotatedAt <= tonumber(ARGV[5]) then return 'GRACE' end
                redis.call('DEL', ARGV[6] .. 'token:' .. current, familyKey)
                redis.call('SREM', ARGV[6] .. 'user:' .. uid, fid)
                return 'REUSED'
            end
            redis.call('HSET', KEYS[1], 'rotatedAt', ARGV[4])
            redis.call('HSET', KEYS[2], 'userId', uid, 'familyId', fid)
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            redis.call('SET', familyKey, ARGV[2], 'PX', ARGV[3])
            redis.call('PEXPIRE', ARGV[6] .. 'user:' .. uid, ARGV[3])
            return 'OK'
            """, String.class);

    // KEYS: 토큰 키 / ARGV: prefix
    private static final RedisScript<Long> REVOKE_FAMILY = new DefaultRedisScript<>("""
            local uid = redis.call('HGET', KEYS[1], 'userId')
            if not uid then return 0 end
            local fid = redis.call('HGET', KEYS[1], 'familyId')
            local familyKey = ARGV[1] .. 'family:' .. fid
            local current = redis.call('GET', familyKey)
            if current then redis.call('DEL', ARGV[1] .. 'token:' .. current) end
            redis.call('DEL', familyKey, KEYS[1])
            redis.call('SREM', ARGV[1] .. 'user:' .. uid, fid)
            return 1
            """, Long.class);

    // KEYS: 사용자 키 / ARGV: prefix
    private static final RedisScript<Long> REVOKE_ALL = new DefaultRedisScript<>("""
            local families = redis.call('SMEMBERS', KEYS[1])
            for _, fid in ipairs(families) do
                local familyKey = ARGV[1] .. 'family:' .. fid
                local current = redis.call('GET', familyKey)
                if current then redis.call('DEL', ARGV[1] .. 'token:' .. current) end
                redis.call('DEL', familyKey)
            end
            redis.call('DEL', KEYS[1])
            return #families
            """, Long.class);

    public enum RotationStatus {
        /** 교체 성공 */
        OK,
        /** 없거나 만료·폐기된 토큰 */
        INVALID,
        /** 교체 직후 같은 토큰이 다시 들어옴 (동시 요청) — 새 토큰은 발급하지 않음 */
        GRACE,
        /** 이미 교체된 토큰 재사용 — 패밀리 전체를 폐기함 */
        REUSED
    }

    public record Rotation(RotationStatus status, String newToken) {
    }

    private final StringRedisTemplate redis;
    private final long ttlMillis;

    public RefreshTokenService(StringRedisTemplate redis, @Value("${refresh_expired_time}") long ttlMillis) {
        this.redis = redis;
        this.ttlMillis = ttlMillis;
    }

    /** 로그인: 새 패밀리를 만들고 첫 토큰을 발급한다 */
    public String issue(int userId) {
        String token = newToken();
        String familyId = UUID.randomUUID().toString();
        redis.execute(ISSUE, List.of(tokenKey(token), PREFIX + "family:" + familyId, userKey(userId)),
                String.valueOf(userId), familyId, token, String.valueOf(ttlMillis));
        return token;
    }

    /** 토큰의 사용자 ID (교체·검증 없이 조회만) */
    public Optional<Integer> findUserId(String token) {
        Object userId = redis.opsForHash().get(tokenKey(token), "userId");
        return Optional.ofNullable(userId).map(id -> Integer.valueOf(id.toString()));
    }

    /** 재발급: 같은 패밀리 안에서 토큰을 교체한다 */
    public Rotation rotate(String oldToken) {
        String newToken = newToken();
        String result = redis.execute(ROTATE, List.of(tokenKey(oldToken), tokenKey(newToken)),
                oldToken, newToken, String.valueOf(ttlMillis), String.valueOf(System.currentTimeMillis()),
                String.valueOf(REUSE_GRACE_MILLIS), PREFIX);
        RotationStatus status = RotationStatus.valueOf(result);
        return new Rotation(status, status == RotationStatus.OK ? newToken : null);
    }

    /** 로그아웃: 이 토큰이 속한 패밀리(해당 기기 세션)를 폐기한다 */
    public void revoke(String token) {
        redis.execute(REVOKE_FAMILY, List.of(tokenKey(token)), PREFIX);
    }

    /** 탈퇴 등: 사용자의 모든 세션을 폐기하고 폐기한 패밀리 수를 반환한다 */
    public long revokeAll(int userId) {
        Long count = redis.execute(REVOKE_ALL, List.of(userKey(userId)), PREFIX);
        return count == null ? 0 : count;
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }

    private static String tokenKey(String token) {
        return PREFIX + "token:" + token;
    }

    private static String userKey(int userId) {
        return PREFIX + "user:" + userId;
    }
}
