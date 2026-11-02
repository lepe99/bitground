package bit.bitgroundspring.common.error;

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
 *   "timestamp": "2026-11-02T16:30:00+09:00"
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
        return problem;
    }
}
