package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.projection.SeasonProjection;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public Integer getCurrentSeasonId() {
        return seasonRepository.findTopByStatusOrderByStartAtDesc(Status.PENDING)
                .map(season -> season.getId())
                .orElseThrow(() -> new IllegalStateException("현재 진행 중인 시즌이 없습니다."));
    }

    /*마이페이지용*/
    public Season getSeasonById(Integer seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new RuntimeException("해당 시즌이 존재하지 않습니다."));
    }

}
