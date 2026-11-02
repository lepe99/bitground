package bit.bitgroundspring.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE}) // 메서드와 클래스에 적용 가능하도록 설정
@Retention(RetentionPolicy.RUNTIME) // 런타임까지 어노테이션 정보 유지
public @interface Loggable {
    String value() default "";
}