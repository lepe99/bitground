package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import bit.bitgroundspring.dto.projection.SeasonProjection;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seasons")
@RequiredArgsConstructor
public class SeasonController {
    private final SeasonService seasonService;
    private final OrderService orderService;
    
    @Value("${season.update.key}")
    private String seasonUpdateKey;
    
    /**
     * 모든 시즌 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<SeasonProjection>> getLatestSeasons() {
        return ResponseEntity.ok(seasonService.getLatestSeasons());
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
     * 스케줄러(Go)가 시즌 정산·스플릿 완료를 알리는 내부 API
     * 공유 키는 X-Scheduler-Key 헤더로 받는다 (쿼리·본문에 두면 접근 로그에 남을 수 있음)
     */
    @PostMapping("/update")
    public ResponseEntity<Void> updateSeasons(@RequestHeader(name = "X-Scheduler-Key", required = false) String schedulerKey,
                                              @RequestParam String seasonFlag) {
        // 시간 비교 공격을 피하도록 상수 시간 비교
        if (schedulerKey == null
                || !MessageDigest.isEqual(schedulerKey.getBytes(StandardCharsets.UTF_8), seasonUpdateKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.INVALID_SECRET_KEY);
        }
        if (!seasonFlag.equals("season") && !seasonFlag.equals("split")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "seasonFlag는 season 또는 split이어야 합니다.");
        }
        orderService.seasonUpdate(seasonFlag);
        return ResponseEntity.noContent().build();
    }
    
}
