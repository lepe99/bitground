package bit.bitgroundspring.common.error;

import bit.bitgroundspring.common.logging.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * RFC 9457 Problem Details 응답 생성.
 *
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Unprocessable Entity",
 *   "status": 422,
 *   "detail": "주문 가능 금액이 부족합니다.",
 *   "instance": "/trade",
 *   "code": "INSUFFICIENT_CASH",
 *   "timestamp": "2026-11-02T16:30:00+09:00",
 *   "traceId": "3f9c2a1b7d4e8f60"
 * }
 * </pre>
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail, String path) {
        return of(code.getStatus(), code, detail, path);
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : code.getMessage());
        return decorate(problem, code, path);
    }

    // 이미 만들어진 ProblemDetail(Spring 표준 예외)에 공통 필드를 채운다
    public static ProblemDetail decorate(ProblemDetail problem, ErrorCode code, String path) {
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", OffsetDateTime.now());
        // 사용자가 오류를 신고할 때 서버 로그를 바로 찾을 수 있도록
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
