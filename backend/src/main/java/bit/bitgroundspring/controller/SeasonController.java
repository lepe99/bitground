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
    
    @PostMapping("/update")
    public ResponseEntity<Void> updateSeasons(@RequestParam String secretKey,
                                              @RequestParam String seasonFlag) {
        // 스케줄러(Go)만 호출하는 내부 API. 공유 키는 시간 비교 공격을 피하도록 상수 시간 비교
        if (!MessageDigest.isEqual(secretKey.getBytes(StandardCharsets.UTF_8), seasonUpdateKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.INVALID_SECRET_KEY);
        }
        orderService.seasonUpdate(seasonFlag);
        return ResponseEntity.noContent().build();
    }
    
}
