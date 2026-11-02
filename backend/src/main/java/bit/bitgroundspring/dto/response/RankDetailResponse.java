package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.dto.projection.PastSeasonTierProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankDetailResponse {
    private Integer highestTier; // 최고 티어
    private List<PastSeasonTierProjection> pastSeasonTiers; // 지난 5시즌 티어 목록
}
