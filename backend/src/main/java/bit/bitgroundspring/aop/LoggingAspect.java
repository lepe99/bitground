package bit.bitgroundspring.aop;

import bit.bitgroundspring.common.error.BusinessException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 공통 로깅 AOP. 클래스나 메서드에 {@link Loggable} 을 붙이면 적용된다.
 *
 * <ul>
 *   <li>INFO  : 호출 결과와 실행 시간 — {@code [주문 접수] OrderService.createReserveOrder 12ms}</li>
 *   <li>WARN  : 느린 호출(logging.aop.slow-threshold-ms 또는 {@link Loggable#slowMs()} 이상)과 서버 쪽 비즈니스 예외(5xx)</li>
 *   <li>INFO  : 예상된 클라이언트 쪽 실패(4xx: 잔액 부족, 리프레시 토큰 없음 등) — 에러 코드는 그대로 남겨 집계한다.
 *               GlobalExceptionHandler와 같은 기준 (로그인하지 않은 방문자의 토큰 재발급 시도까지 WARN이 되던 문제)</li>
 *   <li>ERROR : 예상하지 못한 예외 (스택트레이스는 GlobalExceptionHandler가 한 번만 남김)</li>
 *   <li>DEBUG : 인자·반환값 — 토큰·비밀번호 등 민감 값과 파일 내용은 마스킹</li>
 * </ul>
 * 로그에는 TraceIdFilter가 넣은 traceId·userId가 함께 찍혀 Loki에서 요청 단위로 추적할 수 있다.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // 이름만 보고 값을 가리는 파라미터
    private static final Pattern SENSITIVE_NAME = Pattern.compile("(?i).*(token|password|secret|key|authorization|cookie).*");
    private static final String MASK = "****";

    @Value("${logging.aop.max-string-length:200}")
    private int maxStringLength;

    @Value("${logging.aop.max-collection-size:5}")
    private int maxCollectionSize;

    @Value("${logging.aop.slow-threshold-ms:500}")
    private long slowThresholdMs;

    @Around("@within(bit.bitgroundspring.aop.Loggable) || @annotation(bit.bitgroundspring.aop.Loggable)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String target = joinPoint.getTarget().getClass().getSimpleName() + "." + signature.getName();
        Loggable loggable = loggable(signature, joinPoint.getTarget().getClass());
        String label = loggable == null || loggable.value().isEmpty() ? "" : "[" + loggable.value() + "] ";
        long threshold = loggable != null && loggable.slowMs() > 0 ? loggable.slowMs() : slowThresholdMs;

        if (log.isDebugEnabled()) {
            log.debug("{}{} args={}", label, target, formatArguments(signature.getParameterNames(), joinPoint.getArgs()));
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsed = elapsedMs(start);
            if (elapsed >= threshold) {
                log.warn("{}{} {}ms (SLOW, threshold {}ms)", label, target, elapsed, threshold);
            } else {
                log.info("{}{} {}ms", label, target, elapsed);
            }
            if (log.isDebugEnabled()) {
                log.debug("{}{} return={}", label, target, formatValue(result));
            }
            return result;
        } catch (BusinessException e) {
            // 잔액 부족 등 예상된 실패: 에러 코드로 집계할 수 있게 남긴다 (4xx는 INFO, 5xx는 WARN)
            log.atLevel(failureLevel(e)).log("{}{} {}ms FAILED code={} detail={}",
                    label, target, elapsedMs(start), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Throwable e) {
            log.error("{}{} {}ms ERROR {}: {}", label, target, elapsedMs(start), e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    // 메서드에 붙은 값 우선, 없으면 클래스에 붙은 값
    private Loggable loggable(MethodSignature signature, Class<?> targetClass) {
        Loggable loggable = AnnotatedElementUtils.findMergedAnnotation(signature.getMethod(), Loggable.class);
        return loggable != null ? loggable : AnnotatedElementUtils.findMergedAnnotation(targetClass, Loggable.class);
    }

    static Level failureLevel(BusinessException e) {
        return e.getErrorCode().getStatus().is5xxServerError() ? Level.WARN : Level.INFO;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    String formatArguments(String[] names, Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            sb.append(name).append('=');
            sb.append(SENSITIVE_NAME.matcher(name).matches() ? MASK : formatValue(args[i]));
        }
        return truncate(sb.append('}').toString());
    }

    String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence s) {
            return truncate("\"" + s + "\"");
        }
        // 파일은 내용 대신 메타데이터만
        if (value instanceof MultipartFile file) {
            return "MultipartFile(name=%s, type=%s, size=%d)".formatted(file.getOriginalFilename(), file.getContentType(), file.getSize());
        }
        if (value instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }
        if (value instanceof ServletRequest || value instanceof ServletResponse) {
            return value.getClass().getSimpleName();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Object item : collection) {
                if (count >= maxCollectionSize) {
                    sb.append(", ... (").append(collection.size() - maxCollectionSize).append(" more)");
                    break;
                }
                if (count++ > 0) {
                    sb.append(", ");
                }
                sb.append(formatValue(item));
            }
            return truncate(sb.append(']').toString());
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count >= maxCollectionSize) {
                    sb.append(", ... (").append(map.size() - maxCollectionSize).append(" more)");
                    break;
                }
                if (count++ > 0) {
                    sb.append(", ");
                }
                String key = String.valueOf(entry.getKey());
                sb.append(key).append('=')
                        .append(SENSITIVE_NAME.matcher(key).matches() ? MASK : formatValue(entry.getValue()));
            }
            return truncate(sb.append('}').toString());
        }
        try {
            return truncate(value.toString());
        } catch (RuntimeException e) {
            // 지연 로딩 엔티티 등 toString 실패가 본 로직을 깨지 않도록
            return ClassUtils.getShortName(value.getClass()); // 익명 클래스도 이름이 비지 않도록
        }
    }

    private String truncate(String s) {
        return s.length() <= maxStringLength ? s : s.substring(0, maxStringLength) + "...(" + s.length() + " chars)";
    }
}
