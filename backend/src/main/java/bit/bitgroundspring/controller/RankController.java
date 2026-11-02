package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.projection.RankProjection;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.service.RankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/rank")
public class RankController {

    private final RankService rankService;
    
    /**
     * 현재 시즌 랭킹 조회
     */
    @GetMapping("/current")
    public ResponseEntity<List<RankProjection>> getCurrentRank() {
        return ResponseEntity.ok(rankService.getCurrentRankings());
    }

    /**
     * 특정 시즌 랭킹 조회
     */
    @GetMapping("/{seasonId}")
    public ResponseEntity<List<RankProjection>> getSeasonRank(@PathVariable Integer seasonId) {
        return ResponseEntity.ok(rankService.getSeasonRankings(seasonId));
    }
    
    /**
     * 유저의 최고 티어, 지난 5시즌 티어 조회
     */
    @GetMapping("/detail/{userId}")
    public ResponseEntity<RankDetailResponse> getUserTierDetail(@PathVariable Integer userId) {
        return ResponseEntity.ok(rankService.getUserTierDetails(userId));
    }
}
