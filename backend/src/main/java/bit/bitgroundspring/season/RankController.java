package bit.bitgroundspring.season;

import bit.bitgroundspring.market.CoinService;
import bit.bitgroundspring.season.dto.RankDetailResponse;
import bit.bitgroundspring.season.dto.RankEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/rank")
public class RankController {

    private final RankService rankService;
    private final RankingCache rankingCache;
    private final SchedulerKey schedulerKey;
    private final CoinService coinService;

    /**
     * 현재 시즌 랭킹 조회
     */
    @GetMapping("/current")
    public ResponseEntity<List<RankEntry>> getCurrentRank() {
        return ResponseEntity.ok(rankService.getCurrentRankings());
    }

    /**
     * 특정 시즌 랭킹 조회
     */
    @GetMapping("/{seasonId}")
    public ResponseEntity<List<RankEntry>> getSeasonRank(@PathVariable Integer seasonId) {
        return ResponseEntity.ok(rankService.getSeasonRankings(seasonId));
    }

    /**
     * 유저의 최고 티어, 지난 5시즌 티어 조회
     */
    @GetMapping("/detail/{userId}")
    public ResponseEntity<RankDetailResponse> getUserTierDetail(@PathVariable Integer userId) {
        return ResponseEntity.ok(rankService.getUserTierDetails(userId));
    }

    /**
     * 스케줄러(Go)가 매시 코인 목록·랭킹을 다시 계산한 뒤 알리는 내부 API. 두 캐시를 비운다 (공유 키: X-Scheduler-Key 헤더)
     */
    @PostMapping("/refreshed")
    public ResponseEntity<Void> rankingsRefreshed(@RequestHeader(name = SchedulerKey.HEADER, required = false) String key) {
        schedulerKey.verify(key);
        rankingCache.evictAll();
        coinService.evictMarkets();
        return ResponseEntity.noContent().build();
    }
}
