package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.projection.UserAssetProjection;
import bit.bitgroundspring.dto.response.UserAssetOwnedResponseDto;
import bit.bitgroundspring.dto.response.UserAssetResponse;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.oauth2.AuthService;
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
    private final AuthService authService;
    private final UserAssetService assetService;
    private final UserRepository userRepository;
    private final UserService userService;


    /**
     * 보유 자산들 조회
     */
    @GetMapping
    public ResponseEntity<UserAssetResponse> getUserAssets(
            @CookieValue (value = "jwt_token", required = false) String jwtToken) {
        // JWT 토큰에서 사용자 ID 추출
        Integer userId = authService.getUserIdFromToken(jwtToken);
        
        UserAssetResponse response = assetService.getUserAssets(userId);
        return ResponseEntity.ok(response);
    }

    //투자내역 보유자산
    @GetMapping("/owned")
    public ResponseEntity<UserAssetOwnedResponseDto> getOwnedAssets(
            @CookieValue(value = "jwt_token", required = false) String jwtToken) {

        Integer userId = authService.getUserIdFromToken(jwtToken);

        List<UserAssetProjection> assets = assetService.getOnlyUserAssets(userId);
        Integer cash = userService.getCashByUserId(userId); // 💡 이거 미리 구현해놔야 해
        Integer availableCash = assetService.getAvailableCash(userId);

        UserAssetOwnedResponseDto response = new UserAssetOwnedResponseDto(cash, assets, availableCash);
        return ResponseEntity.ok(response);
    }



}