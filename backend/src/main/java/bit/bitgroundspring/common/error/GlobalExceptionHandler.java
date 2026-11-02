package bit.bitgroundspring.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * 모든 API 예외를 RFC 9457 Problem Details 형식으로 통일한다.
 * Spring MVC 표준 예외(검증 실패, 404, 405, 413 등)는 상위 클래스가 ProblemDetail로 만들고,
 * 여기서 code·timestamp·instance 같은 공통 필드를 덧붙인다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {} {}", code, request.getRequestURI(), e.getMessage(), e);
        } else {
            log.info("[{}] {} {}", code, request.getRequestURI(), e.getMessage());
        }
        return ResponseEntity.status(code.getStatus())
                .body(ProblemDetails.of(code, e.getMessage(), request.getRequestURI()));
    }

    // 경로 변수·쿼리 파라미터 타입 불일치 (예: /orders/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String detail = "'%s' 값이 올바르지 않습니다.".formatted(e.getName());
        return ResponseEntity.badRequest()
                .body(ProblemDetails.of(ErrorCode.INVALID_INPUT, detail, request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.INVALID_INPUT, null, request.getRequestURI());
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    // 예상하지 못한 예외: 내부 메시지는 숨기고 로그로만 남긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("[INTERNAL_ERROR] {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.internalServerError()
                .body(ProblemDetails.of(ErrorCode.INTERNAL_ERROR, null, request.getRequestURI()));
    }

    // @Valid 요청 본문 검증 실패: 필드별 메시지를 errors 배열로 내려준다
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail(ErrorCode.INVALID_INPUT.getMessage());
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Spring 표준 예외 응답에 공통 필드(code, timestamp, instance) 추가
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd
                ? pd
                : ProblemDetail.forStatusAndDetail(statusCode, ErrorCode.fromStatus(statusCode.value()).getMessage());
        String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
        ProblemDetails.decorate(problem, ErrorCode.fromStatus(statusCode.value()), path);
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }
}
