package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 시즌 중간에 가입한 사용자의 시작 현금: 기존 사용자와 같게 맞춘다.
 * 스플릿 2가 시작된 뒤면 추가 지급분까지 2,000만원, 그 전이면 1,000만원.
 * 일정은 진행 중인 시즌의 DB 값(split_starts_at)이 기준이다 (스케줄러와 같은 기준, 관리자가 바꾸면 함께 바뀜)
 */
@Component
@RequiredArgsConstructor
public class InitialCashPolicy {

    // 같은 값이 스케줄러(scheduler/service/rules.go)와 프론트(constants/season.js)에도 있다.
    // 세 곳이 같은지는 scheduler/service/rules_test.go가 확인한다
    static final int SEASON_START_CASH = 10_000_000;
    static final int SPLIT_CASH = 10_000_000;

    private final SeasonRepository seasonRepository;
    private final Clock clock;

    public int getInitialCash() {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean splitStarted = seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                .map(season -> season.getSplitStartsAt() != null && !now.isBefore(season.getSplitStartsAt()))
                .orElse(false);
        return splitStarted ? SEASON_START_CASH + SPLIT_CASH : SEASON_START_CASH;
    }
}
