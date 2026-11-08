package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.season.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 거래 가능 시간. 진행 중인 시즌의 종료 시각(ends_at)이 지나면 새 시즌이 시작될 때까지 거래를 막는다.
 *
 * <p>스케줄러는 종료 시각 직후 최종 순위·청산 가격을 계산하고 정산한다. 그동안 거래를 받으면
 * 그 거래가 최종 순위에는 빠진 채 청산되거나, 실시간 가격과 청산 가격의 차이를 이용할 수 있고,
 * 예약 주문 체결과 정산이 서로 잠금을 기다릴 수 있었다. 정산이 새 시즌을 만들면 그 시즌의 종료 시각은
 * 미래이므로 거래가 자동으로 다시 열린다 (상태 값을 따로 두지 않는다).
 *
 * <p>주문마다 DB를 읽지 않도록 종료 시각을 잠깐 기억해 두고, 스케줄러의 시즌 알림(/seasons/update)을 받으면 바로 새로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class TradingWindow {

    private static final long REFRESH_MILLIS = 30_000;

    private final SeasonRepository seasonRepository;
    private final Clock clock;

    private record Cached(LocalDateTime endsAt, long loadedAt) {
    }

    private volatile Cached cached;

    /** 지금 거래를 막아야 하면 true (진행 중인 시즌이 없거나 종료 시각이 지남) */
    public boolean isClosed() {
        LocalDateTime endsAt = endsAt();
        return endsAt != null && !LocalDateTime.now(clock).isBefore(endsAt);
    }

    public void ensureOpen() {
        if (isClosed()) {
            throw new BusinessException(ErrorCode.SEASON_SETTLING);
        }
    }

    /** 시즌이 바뀌었을 때 (스케줄러 알림) */
    public void refresh() {
        cached = null;
    }

    private LocalDateTime endsAt() {
        Cached c = cached;
        long now = clock.millis();
        if (c == null || now - c.loadedAt() > REFRESH_MILLIS) {
            // 종료 시각이 없는 시즌(일정 칸을 만들기 전 데이터)은 막지 않는다
            LocalDateTime endsAt = seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                    .map(s -> s.getEndsAt())
                    .orElse(null);
            c = new Cached(endsAt, now);
            cached = c;
        }
        return c.endsAt();
    }
}
