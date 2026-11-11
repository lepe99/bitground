package bit.bitgroundspring.season;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 스케줄러(Go)가 부르는 내부 API의 공유 키 확인.
 * 키는 X-Scheduler-Key 헤더로 받는다 (쿼리·본문에 두면 접근 로그에 남을 수 있음)
 */
@Component
public class SchedulerKey {

    public static final String HEADER = "X-Scheduler-Key";

    private final byte[] expected;

    public SchedulerKey(@Value("${season.update.key}") String key) {
        this.expected = key.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String given) {
        // 시간 비교 공격을 피하도록 상수 시간 비교
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected)) {
            throw new BusinessException(ErrorCode.INVALID_SECRET_KEY);
        }
    }
}
