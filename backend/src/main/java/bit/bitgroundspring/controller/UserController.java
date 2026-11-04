package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.response.TradeDetailDto;
import bit.bitgroundspring.dto.response.TradeSummaryDto;
import bit.bitgroundspring.dto.response.UserDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.SeasonService;
import bit.bitgroundspring.service.UserService;
import bit.bitgroundspring.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final SeasonService seasonService;
    private final OrderService orderService;
    private final CookieUtil cookieUtil;

    // 내 정보 조회
    @GetMapping("/me")
    public UserDto getCurrentUser(@AuthenticationPrincipal AuthUser authUser) {
        return toDto(userService.getActiveUser(authUser.id()));
    }

    // 내 정보 수정 (닉네임, 이메일, 프로필 이미지). 처음 가입 후 확인 화면(/welcome)도 이걸로 마친다
    @PutMapping("/me")
    public UserDto updateCurrentUser(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("name") String name,
            @RequestParam(value = "email", required = false) String email, // 비우면 이메일 없음 (카카오 로그인 등)
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage // 기본 프로필 사진으로
    ) {
        User user = userService.getActiveUser(authUser.id());
        String imageUrl = (image != null && !image.isEmpty()) ? userService.uploadProfileImage(image) : null;
        return toDto(userService.updateUser(user.getId(), name, email, imageUrl, removeImage));
    }

    // 회원 탈퇴 (is_deleted = 1) + 모든 세션 폐기, 로그인 쿠키 삭제
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(@AuthenticationPrincipal AuthUser authUser, HttpServletResponse response) {
        userService.softDeleteUser(userService.getActiveUser(authUser.id()).getId());
        cookieUtil.deleteCookie(response, "jwt_token");
        cookieUtil.deleteCookie(response, "refresh_token");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trade-summary")
    public List<TradeSummaryDto> getTradeSummary(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestParam("seasonId") Integer seasonId) {
        User user = userService.getActiveUser(authUser.id());
        Season season = seasonService.getSeasonById(seasonId);
        return orderService.getTradeSummary(user, season);
    }

    @GetMapping("/trade-details")
    public List<TradeDetailDto> getTradeDetails(@AuthenticationPrincipal AuthUser authUser,
                                                @RequestParam("seasonId") Integer seasonId) {
        User user = userService.getActiveUser(authUser.id());
        Season season = seasonService.getSeasonById(seasonId);
        return orderService.getTradeDetails(user, season);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .provider(user.getProvider())
                .role(user.getRole())
                .cash(user.getCash())
                .tier(user.getTier())
                .currentTier(userService.getCurrentTier(user))
                .onboarded(Boolean.TRUE.equals(user.getIsOnboarded()))
                .build();
    }
}
