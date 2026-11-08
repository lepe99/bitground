package bit.bitgroundspring.season;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.season.dto.PastSeasonTierProjection;
import bit.bitgroundspring.season.dto.RankDetailResponse;
import bit.bitgroundspring.season.dto.RankProjection;
import bit.bitgroundspring.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankService {
    
    private final UserRankingRepository userRankingRepository;
    
    /**
     * 현 시즌 랭킹 조회
     */
    public List<RankProjection> getCurrentRankings() {
        return userRankingRepository.findCurrentSeasonRankings();
    }
    
    /**
     * 특정 시즌 랭킹 조회
     */
    public List<RankProjection> getSeasonRankings(int seasonId) {
        return userRankingRepository.findRankingsBySeasonId(seasonId);
    }
    
    /**
     * 유저의 최고 티어, 참여한 최근 5시즌 기록(티어·순위·자산), 참여 시즌 수 조회
     */
    public RankDetailResponse getUserTierDetails(Integer userId) {
        User user = User.builder().id(userId).build();

        Integer highestTier = userRankingRepository.findHighestTierByUser(user);

        List<PastSeasonTierProjection> pastSeasonTiers = userRankingRepository.findTop5CompletedSeasonsByUser(
                user,
                Status.COMPLETED,
                PageRequest.of(0, 5)
        );

        long seasonCount = userRankingRepository.countSeasonsByUser(user, Status.COMPLETED);

        return RankDetailResponse.builder()
                .highestTier(highestTier != null ? highestTier : 0)
                .pastSeasonTiers(pastSeasonTiers)
                .seasonCount(seasonCount)
                .build();
    }
}
