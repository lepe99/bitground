package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.response.TierDto;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.service.UserService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MypageController {
    private final UserService userService;

    @GetMapping("/tier")
    public ResponseEntity<TierDto> getMyTier(@AuthenticationPrincipal AuthUser authUser, @RequestParam Integer seasonId) {
        User user = userService.getActiveUser(authUser.id());
        TierDto tierDto = userService.getTierDtoForUserAndSeason(user, seasonId);
        return ResponseEntity.ok(tierDto);
    }
}
