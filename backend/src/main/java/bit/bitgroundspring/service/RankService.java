package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.projection.PastSeasonTierProjection;
import bit.bitgroundspring.dto.projection.RankProjection;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.RankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankService {
    
    private final RankRepository rankRepository;
    
    /**
     * 현 시즌 랭킹 조회
     */
    public List<RankProjection> getCurrentRankings() {
        return rankRepository.findCurrentSeasonRankings();
    }
    
    /**
     * 특정 시즌 랭킹 조회
     */
    public List<RankProjection> getSeasonRankings(int seasonId) {
        return rankRepository.findRankingsBySeasonId(seasonId);
    }
    
    /**
     * 유저의 최고 티어, 지난 5시즌 티어 조회
     */
    public RankDetailResponse getUserTierDetails(Integer userId) {
        User user = User.builder().id(userId).build();

        Integer highestTier = rankRepository.findHighestTierByUser(user);

        List<PastSeasonTierProjection> pastSeasonTiers = rankRepository.findTop5CompletedSeasonsByUser(
                user,
                Status.COMPLETED,
                PageRequest.of(0, 5)
        );

        return RankDetailResponse.builder()
                .highestTier(highestTier != null ? highestTier : 0)
                .pastSeasonTiers(pastSeasonTiers)
                .build();
    }
}
