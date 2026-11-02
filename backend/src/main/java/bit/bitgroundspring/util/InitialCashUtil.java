package bit.bitgroundspring.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class InitialCashUtil {
    
    @Value("${scheduler.setting}")
    private String schedulerSetting;
    
    public int getInitialCash() {
        LocalDateTime now = LocalDateTime.now();
        int day = now.getDayOfMonth();
        int hour = now.getHour();
        if (schedulerSetting.equals("dev")) {
            return hour % 6 >= 3 ? 20000000 : 10000000; // 개발 환경: 3시간 단위로 초기 자금 변경
        } else {
            return (day >= 8 && day <= 15) || day >= 23 ? 20000000 : 10000000; // 운영 환경: 8~15일, 23일 이후 2000만원, 나머지 1000만원
        }
    }
}
