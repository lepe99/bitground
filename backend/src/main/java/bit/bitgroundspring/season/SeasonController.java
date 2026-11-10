package bit.bitgroundspring.season;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.order.OrderService;
import bit.bitgroundspring.season.dto.CurrentSeasonDto;
import bit.bitgroundspring.season.dto.SeasonProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seasons")
@RequiredArgsConstructor
public class SeasonController {
    private final SeasonService seasonService;
    private final OrderService orderService;
    private final SchedulerKey schedulerKey;
    private final RankingCache rankingCache;
    
    /**
     * 모든 시즌 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<SeasonProjection>> getLatestSeasons() {
        return ResponseEntity.ok(seasonService.getLatestSeasons());
    }

    /**
     * 진행 중인 시즌의 이름·스플릿·종료 시각 (로그인하지 않아도 볼 수 있음)
     */
    @GetMapping("/current")
    public CurrentSeasonDto getCurrentSeason() {
        return seasonService.getCurrentSeason();
    }

    /**
     * 현재 진행 중인 시즌 ID만 반환
     */
    @GetMapping("/current-id")
    public ResponseEntity<Integer> getCurrentSeasonId() {
        Integer currentId = seasonService.getCurrentSeasonId();
        return ResponseEntity.ok(currentId);
    }
    
    /**
     * 스케줄러(Go)가 시즌 정산·스플릿 완료를 알리는 내부 API (공유 키: X-Scheduler-Key 헤더)
     */
    @PostMapping("/update")
    public ResponseEntity<Void> updateSeasons(@RequestHeader(name = SchedulerKey.HEADER, required = false) String key,
                                              @RequestParam String seasonFlag) {
        schedulerKey.verify(key);
        if (!seasonFlag.equals("season") && !seasonFlag.equals("split")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "seasonFlag는 season 또는 split이어야 합니다.");
        }
        orderService.seasonUpdate(seasonFlag);
        rankingCache.evictAll(); // 정산하면 진행 중 시즌이 바뀐다
        return ResponseEntity.noContent().build();
    }
    
}
