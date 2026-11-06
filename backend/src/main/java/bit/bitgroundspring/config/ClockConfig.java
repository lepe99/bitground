package bit.bitgroundspring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 현재 시각. 시각에 따라 달라지는 동작(거래 마감 등)을 테스트에서 고정한 시각으로 바꿀 수 있게 빈으로 둔다 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
