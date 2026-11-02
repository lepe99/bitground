package bit.bitgroundspring.controller;

import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.projection.UserAssetProjection;
import bit.bitgroundspring.dto.response.UserAssetOwnedResponseDto;
import bit.bitgroundspring.dto.response.UserAssetResponse;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.UserAssetService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class UserAssetController {
    private final UserAssetService assetService;
    private final UserRepository userRepository;
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
        Integer cash = userService.getCashByUserId(userId); // 💡 이거 미리 구현해놔야 해
        Integer availableCash = assetService.getAvailableCash(userId);

        UserAssetOwnedResponseDto response = new UserAssetOwnedResponseDto(cash, assets, availableCash);
        return ResponseEntity.ok(response);
    }



}