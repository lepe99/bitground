package bit.bitgroundspring.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE}) // 메서드와 클래스에 적용 가능하도록 설정
@Retention(RetentionPolicy.RUNTIME) // 런타임까지 어노테이션 정보 유지
public @interface Loggable {
    String value() default "";

    /**
     * 이 시간(ms) 이상 걸리면 느린 호출(WARN)로 본다. 0 이하면 공통값(logging.aop.slow-threshold-ms).
     * 원래 오래 걸리는 호출(AI 생성 등)은 평소 시간보다 크게 잡아, 정말 느릴 때만 WARN이 되게 한다
     */
    long slowMs() default 0;
}