package bit.bitgroundspring.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청마다 traceId를 MDC에 넣어 한 요청에서 나온 로그를 묶을 수 있게 한다.
 * - 응답 헤더 X-Trace-Id 로 돌려주고, 에러 응답(Problem Details)에도 traceId 필드로 포함
 * - 앞단(프록시·클라이언트)이 보낸 X-Trace-Id 는 형식이 안전할 때만 이어받음 (로그 인젝션 방지)
 * - 보안 필터보다 먼저 실행되어 인증 실패 로그에도 traceId가 남는다
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // 스레드 재사용 시 이전 요청 값이 남지 않도록
        }
    }

    // SSE·WebSocket처럼 오래 열린 비동기 요청도 같은 traceId를 유지
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
