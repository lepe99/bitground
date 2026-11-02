package bit.bitgroundspring.controller;

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
    public ResponseEntity<?> updateSeasons(@RequestParam String secretKey,
                                           @RequestParam String seasonFlag) {
        // 비밀 키 검증
        if (!secretKey.equals(seasonUpdateKey)) {
            return ResponseEntity.status(403).body("Forbidden: Invalid secret key");
        }
        
        // 시즌 업데이트
        orderService.seasonUpdate(seasonFlag);
        
        return ResponseEntity.ok().build();
    }
    
}
