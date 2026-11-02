package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.TierDto;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MypageController {
    private final UserService userService; // ← 이 줄이 꼭 필요합니다

    @GetMapping("/tier")
    public ResponseEntity<TierDto> getMyTier(@RequestParam Integer seasonId) {
        User user = userService.getCurrentUser();
        TierDto tierDto = userService.getTierDtoForUserAndSeason(user, seasonId);
        return ResponseEntity.ok(tierDto);
    }
}
