package bit.bitgroundspring.season.dto;

import bit.bitgroundspring.season.Season;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 진행 중인 시즌의 일정 (홈·랭킹 화면의 남은 시간 표시)
 * @param split         지금 스플릿 (1: 시즌 시작 ~ 스플릿 2 전, 2: 스플릿 2 ~ 시즌 종료)
 * @param splitStartsAt 스플릿 2 시작 (추가 자금 지급)
 * @param endsAt        시즌 정산 시각 (자산 초기화, 티어 확정)
 * @param now           서버 시각 (사용자 PC 시계가 틀려도 남은 시간이 맞도록)
 */
public record CurrentSeasonDto(Integer id, String name, LocalDate startAt, LocalDate endAt,
                               LocalDateTime splitStartsAt, LocalDateTime endsAt, int split, LocalDateTime now) {

    public static CurrentSeasonDto of(Season season, LocalDateTime now) {
        int split = season.getSplitStartsAt() != null && !now.isBefore(season.getSplitStartsAt()) ? 2 : 1;
        return new CurrentSeasonDto(season.getId(), season.getName(), season.getStartAt(), season.getEndAt(),
                season.getSplitStartsAt(), season.getEndsAt(), split, now);
    }
}
