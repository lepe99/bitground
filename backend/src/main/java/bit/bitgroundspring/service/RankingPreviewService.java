package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.PublicRankingDto;
import bit.bitgroundspring.dto.projection.RankProjection;
import bit.bitgroundspring.dto.response.PublicRankingResponse;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.repository.RankRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingPreviewService {

    private final RankRepository rankRepository;
    private final SeasonRepository seasonRepository;

    public PublicRankingResponse getPublicRankingPreview() {
        List<RankProjection> projections = rankRepository.findCurrentSeasonRankings();

        String seasonName;
        LocalDateTime baseTime;

        if (projections.isEmpty()) {
            // 참여한 유저가 없을 경우, 현재 시즌 이름만 따로 불러옴
            Season currentSeason = seasonRepository.findByStatus(Status.PENDING).orElse(null);
            seasonName = (currentSeason != null) ? currentSeason.getName() : "시즌 정보 없음";
            baseTime = LocalDateTime.now();
        } else {
            // 참여한 유저가 있으면 첫 번째 projection에서 시간과 시즌 정보 추출
            RankProjection first = projections.get(0);
            seasonName = first.getSeasonName(); // ← 이 줄 중요
            baseTime = first.getUpdatedAt();
        }

        String updatedAtText = formatUpdatedTime(baseTime);
        String minutesLeftText = formatMinutesLeft(baseTime);

        List<PublicRankingDto> rankingDtos = projections.stream()
                .limit(5)
                .map(p -> PublicRankingDto.builder()
                        .name(p.getName())
                        .profileImage(p.getProfileImage())
                        .tier(p.getTier())
                        .totalValue(p.getTotalValue())
                        .ranks(p.getRanks())
                        .build()
                ).collect(Collectors.toList());

        return PublicRankingResponse.builder()
                .seasonName(seasonName)
                .updatedAtText(updatedAtText)
                .minutesLeftText(minutesLeftText)
                .rankings(rankingDtos)
                .build();
    }

    private String formatUpdatedTime(LocalDateTime time) {
        String meridiem = time.getHour() < 12 ? "오전" : "오후";
        int hour12 = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return String.format("%d월 %d일 %s %d시 기준", time.getMonthValue(), time.getDayOfMonth(), meridiem, hour12);
    }

    private String formatMinutesLeft(LocalDateTime updatedAt) {
        LocalDateTime nextHour = updatedAt.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        long minutesLeft = java.time.Duration.between(LocalDateTime.now(), nextHour).toMinutes();
        minutesLeft = Math.max(0, minutesLeft);
        return String.format("다음 갱신까지 %d분 남았습니다", minutesLeft);
    }
}