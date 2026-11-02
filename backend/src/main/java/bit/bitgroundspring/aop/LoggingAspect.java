package bit.bitgroundspring.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    
    @Value("${logging.aop.max-string-length:200}")
    private int maxStringLength;
    
    @Value("${logging.aop.max-collection-size:5}")
    private int maxCollectionSize;
    
    @Around("@within(loggable) || @annotation(loggable)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String description = loggable.value().isEmpty() ? "" : "[" + loggable.value() + "]";
        Object[] args = joinPoint.getArgs();
        
        // 입력 인자 로깅 (길이 제한 적용)
        String argsString = formatArguments(args);
        log.info(">>>>{} {}.{} - Arguments: {}", description, className, methodName, argsString);
        
        try {
            Object result = joinPoint.proceed();
            // 반환값 로깅 (길이 제한 적용)
            String resultString = formatValue(result);
            log.info("<<<<{} {}.{} (Success) - Return: {}", description, className, methodName, resultString);
            return result;
        } catch (Exception e) {
            log.error("!!!!{} {}.{} (Error: {})", description, className, methodName, e.getMessage());
            throw e;
        }
    }
    
    // 인자 배열을 포맷팅
    private String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatValue(args[i]));
        }
        sb.append("]");
        
        return truncateString(sb.toString(), maxStringLength);
    }
    
    // 개별 값을 포맷팅
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        // 문자열 처리
        if (value instanceof String) {
            return truncateString("\"" + value + "\"", maxStringLength);
        }
        
        // 컬렉션 처리
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return "[]";
            }
            
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Object item : collection) {
                if (count > 0) {
                    sb.append(", ");
                }
                if (count >= maxCollectionSize) {
                    sb.append("... (").append(collection.size() - maxCollectionSize).append(" more)");
                    break;
                }
                sb.append(formatValue(item));
                count++;
            }
            sb.append("]");
            
            return truncateString(sb.toString(), maxStringLength);
        }
        
        // Map 처리
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            
            StringBuilder sb = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count > 0) {
                    sb.append(", ");
                }
                if (count >= maxCollectionSize) {
                    sb.append("... (").append(map.size() - maxCollectionSize).append(" more)");
                    break;
                }
                sb.append(formatValue(entry.getKey())).append("=").append(formatValue(entry.getValue()));
                count++;
            }
            sb.append("}");
            
            return truncateString(sb.toString(), maxStringLength);
        }
        
        // 배열 처리
        if (value.getClass().isArray()) {
            if (value instanceof byte[]) {
                return "byte[" + ((byte[]) value).length + "]";
            } else if (value instanceof Object[] array) {
                if (array.length == 0) {
                    return "[]";
                }
                
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < Math.min(array.length, maxCollectionSize); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(formatValue(array[i]));
                }
                if (array.length > maxCollectionSize) {
                    sb.append("... (").append(array.length - maxCollectionSize).append(" more)");
                }
                sb.append("]");
                
                return truncateString(sb.toString(), maxStringLength);
            }
        }
        
        // 기본 객체 처리
        String valueString = value.toString();
        return truncateString(valueString, maxStringLength);
    }
    
    
    // 문자열 길이 제한
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        
        if (str.length() <= maxLength) {
            return str;
        }
        
        return str.substring(0, maxLength - 3) + "...";
    }
}