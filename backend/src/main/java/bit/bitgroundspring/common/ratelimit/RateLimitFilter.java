package bit.bitgroundspring.common.ratelimit;

import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.error.ProblemDetailSecurityHandler;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 요청 횟수 제한 (Redis 고정 윈도 카운터)
 *
 * 비용이 크거나 남용되기 쉬운 API만 경로 규칙으로 제한한다. 로그인한 요청은 사용자 ID, 아니면 클라이언트 IP 기준.
 * 로그인 리다이렉트(/oauth2/authorization)는 Spring Security 필터에서 처리되므로 그보다 앞에서 실행한다.
 * 초과하면 429 TOO_MANY_REQUESTS + Retry-After. Redis 장애 시에는 요청을 막지 않는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // TraceIdFilter·ForwardedHeaderFilter 다음, Spring Security(-100)보다 앞
public class RateLimitFilter extends OncePerRequestFilter {

    /** 제한 규칙: 이름, HTTP 메서드(null이면 전체), 경로 패턴, 윈도당 허용 횟수, 윈도 길이(초), IP 기준 여부 */
    record Rule(String name, String method, String pattern, int limit, int windowSeconds, boolean byIp) {
    }

    static final List<Rule> RULES = List.of(
            // 주문: 매크로·스크립트로 초당 수십 건 넣는 것 방지
            new Rule("order", "POST", "/trade", 30, 60, false),
            new Rule("order", "POST", "/orders/reserve", 30, 60, false),
            // 업로드: S3 저장 공간·트래픽
            new Rule("upload", "POST", "/uploads/images", 10, 60, false),
            new Rule("upload", "PUT", "/users/me", 10, 60, false),
            // AI 조언 생성(버튼 요청): Gemini 호출 비용. 조회(GET)는 제한하지 않음
            new Rule("ai", "POST", "/mypage/ai-advice", 5, 60, false),
            // 로그인 시도·토큰 재발급: 비로그인 상태에서도 호출되므로 IP 기준
            new Rule("login", "GET", "/oauth2/authorization/**", 20, 60, true),
            new Rule("refresh", "POST", "/auth/refresh", 30, 60, true)
    );

    // KEYS: 카운터 키 / ARGV: 윈도 길이(초). 첫 요청에만 만료를 걸어 고정 윈도를 만든다
    private static final RedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwtTokenProvider;
    private final ProblemDetailSecurityHandler problemWriter;
    private final boolean enabled;

    public RateLimitFilter(StringRedisTemplate redis, JwtTokenProvider jwtTokenProvider,
                           ProblemDetailSecurityHandler problemWriter,
                           @Value("${rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.jwtTokenProvider = jwtTokenProvider;
        this.problemWriter = problemWriter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || findRule(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = findRule(request).orElseThrow();
        String key = "ratelimit:" + rule.name() + ":" + clientKey(request, rule);

        Long count;
        try {
            count = redis.execute(INCREMENT, List.of(key), String.valueOf(rule.windowSeconds()));
        } catch (RuntimeException e) {
            log.warn("Rate limit check skipped (Redis error): {}", e.toString());
            chain.doFilter(request, response);
            return;
        }

        if (count != null && count > rule.limit()) {
            Long ttl = redis.getExpire(key);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ttl == null || ttl < 1 ? rule.windowSeconds() : ttl));
            log.warn("Rate limit exceeded: {} ({}/{} per {}s)", key, count, rule.limit(), rule.windowSeconds());
            problemWriter.write(response, ErrorCode.TOO_MANY_REQUESTS, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<Rule> findRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        return RULES.stream()
                .filter(r -> r.method() == null || r.method().equalsIgnoreCase(request.getMethod()))
                .filter(r -> matcher.match(r.pattern(), path))
                .findFirst();
    }

    // 사용자 기준 규칙이라도 유효한 토큰이 없으면 IP로 센다 (인증 실패는 뒤의 보안 필터가 401로 처리)
    private String clientKey(HttpServletRequest request, Rule rule) {
        if (!rule.byIp()) {
            Cookie jwt = WebUtils.getCookie(request, "jwt_token");
            Optional<Integer> userId = Optional.ofNullable(jwt)
                    .flatMap(c -> jwtTokenProvider.parse(c.getValue()))
                    .map(claims -> claims.get("userId", Integer.class));
            if (userId.isPresent()) {
                return "user:" + userId.get();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
