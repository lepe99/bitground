package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.response.CurrentSeasonDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 관리자: 진행 중인 시즌의 일정(스플릿 2 시작, 시즌 종료) 바꾸기.
 *
 * <p>스케줄러(Lambda)는 매시 정각에 DB의 일정을 읽어 시각이 지났으면 스플릿·정산을 한다.
 * 그래서 여기서 DB만 바꾸면 다음 정각 실행에 반영되고, 정각 단위로만 받는다 (그 사이 시각은 다음 정각에야 처리됨).
 *
 * <ul>
 *   <li>이미 지난 스플릿은 바꿀 수 없다 (스케줄러는 스플릿 시각별로 한 번 지급하므로, 옮기면 다시 지급된다)</li>
 *   <li>종료 시각이 지나 정산을 기다리는 시즌은 바꿀 수 없다</li>
 *   <li>새 시각은 모두 미래이고, 스플릿 &lt; 종료</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminSeasonService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final SeasonRepository seasonRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final TradingWindow tradingWindow;
    private final AdminAuditService audit;
    private final Clock clock;

    @Transactional
    public CurrentSeasonDto updateSchedule(AuthUser admin, LocalDateTime splitStartsAt, LocalDateTime endsAt) {
        Season season = seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
        LocalDateTime now = LocalDateTime.now(clock);

        if (splitStartsAt == null || endsAt == null) {
            throw invalid("스플릿 2 시작과 시즌 종료 시각을 모두 입력해 주세요.");
        }
        if (!onTheHour(splitStartsAt) || !onTheHour(endsAt)) {
            throw invalid("시각은 정각 단위로만 정할 수 있어요.");
        }
        if (season.getEndsAt() != null && !now.isBefore(season.getEndsAt())) {
            throw new BusinessException(ErrorCode.SEASON_SETTLING);
        }

        boolean splitChanged = !Objects.equals(splitStartsAt, season.getSplitStartsAt());
        if (splitChanged) {
            if (season.getSplitStartsAt() != null && !now.isBefore(season.getSplitStartsAt())) {
                throw invalid("이미 시작된 스플릿 2는 바꿀 수 없어요.");
            }
            if (!splitStartsAt.isAfter(now)) {
                throw invalid("스플릿 2 시작은 지금 이후여야 해요.");
            }
        }
        if (!endsAt.isAfter(now)) {
            throw invalid("시즌 종료는 지금 이후여야 해요.");
        }
        if (!endsAt.isAfter(splitStartsAt)) {
            throw invalid("시즌 종료는 스플릿 2 시작보다 뒤여야 해요.");
        }
        if (season.getStartAt() != null && !splitStartsAt.isAfter(season.getStartAt().atStartOfDay())) {
            throw invalid("스플릿 2 시작은 시즌 시작 이후여야 해요.");
        }

        // 스케줄러 정산(시즌 행 잠금 후 상태 변경)과 겹치면 진행 중이고 종료 전인 시즌만 바뀌도록 조건을 건다
        int updated = jdbc.update("""
                UPDATE seasons SET split_starts_at = :split, ends_at = :ends, end_at = :endAt
                WHERE id = :id AND status = 'PENDING' AND (ends_at IS NULL OR ends_at > :now)
                """, new MapSqlParameterSource()
                .addValue("split", splitStartsAt)
                .addValue("ends", endsAt)
                .addValue("endAt", endsAt.minusSeconds(1).toLocalDate()) // 화면용 종료일 (종료 시각 전날, 정각이 아니면 당일)
                .addValue("id", season.getId())
                .addValue("now", now));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SEASON_SETTLING);
        }

        audit.record(admin.id(), "SEASON_SCHEDULE", "SEASON", season.getId(), "%s · 스플릿 2 %s → %s, 종료 %s → %s".formatted(
                season.getName(), format(season.getSplitStartsAt()), format(splitStartsAt),
                format(season.getEndsAt()), format(endsAt)));
        tradingWindow.refresh();

        // 엔티티는 고치지 않는다 (위 조건부 UPDATE를 커밋 때 다시 덮어쓰지 않게)
        int split = now.isBefore(splitStartsAt) ? 1 : 2;
        return new CurrentSeasonDto(season.getId(), season.getName(), season.getStartAt(),
                endsAt.minusSeconds(1).toLocalDate(), splitStartsAt, endsAt, split, now);
    }

    private static boolean onTheHour(LocalDateTime t) {
        return t.getMinute() == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }

    private static String format(LocalDateTime t) {
        return t == null ? "-" : t.format(FMT);
    }

    private static BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.INVALID_SEASON_SCHEDULE, detail);
    }
}
