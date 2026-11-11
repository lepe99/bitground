package bit.bitgroundspring.season;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.season.dto.CurrentSeasonDto;
import bit.bitgroundspring.season.dto.SeasonProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    
    /**
     * 시즌 목록을 내림차순으로 조회 (최신 시즌부터 최대 48개)
     */
    public List<SeasonProjection> getLatestSeasons() {
        return seasonRepository.findTop48ByOrderByIdDesc();
    }

    /** 진행 중인 시즌의 일정 (스플릿·종료 시각) */
    public CurrentSeasonDto getCurrentSeason() {
        return seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                .map(season -> CurrentSeasonDto.of(season, LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
    }

    public Integer getCurrentSeasonId() {
        return seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                .map(season -> season.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
    }

    /*마이페이지용*/
    public Season getSeasonById(Integer seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
    }

}
