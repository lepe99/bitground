package bit.bitgroundspring.order;

import bit.bitgroundspring.order.dto.UserAssetOwnedResponseDto;
import bit.bitgroundspring.order.dto.UserAssetProjection;
import bit.bitgroundspring.order.dto.UserAssetResponse;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class UserAssetController {
    private final UserAssetService assetService;
    private final UserService userService;


    /**
     * 보유 자산들 조회
     */
    @GetMapping
    public ResponseEntity<UserAssetResponse> getUserAssets(
            @AuthenticationPrincipal AuthUser authUser) {
        // JWT 토큰에서 사용자 ID 추출
        Integer userId = authUser.id();
        
        UserAssetResponse response = assetService.getUserAssets(userId);
        return ResponseEntity.ok(response);
    }

    //투자내역 보유자산
    @GetMapping("/owned")
    public ResponseEntity<UserAssetOwnedResponseDto> getOwnedAssets(
            @AuthenticationPrincipal AuthUser authUser) {

        Integer userId = authUser.id();

        List<UserAssetProjection> assets = assetService.getOnlyUserAssets(userId);
        Long cash = userService.getCashByUserId(userId);
        Long availableCash = assetService.getAvailableCash(userId);

        UserAssetOwnedResponseDto response = new UserAssetOwnedResponseDto(cash, assets, availableCash,
                assetService.getSeasonFee(userId));
        return ResponseEntity.ok(response);
    }



}