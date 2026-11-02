package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.TradeDetailDto;
import bit.bitgroundspring.dto.TradeSummaryDto;
import bit.bitgroundspring.dto.UserDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.naver.NcpObjectStorageService;
import bit.bitgroundspring.security.oauth2.AuthService;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.SeasonService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthService authService;
    private final SeasonService seasonService;
    private final OrderService orderService;

    //사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @CookieValue(name = "jwt_token", required = false) String jwtToken) {

        // 인증된 사용자의 정보를 가져옵니다.
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);
        String provider = userDto.getProvider();
        String providerId = userDto.getProviderId();

        // 사용자 정보를 DB에서 조회합니다.
        Optional<User> user = userService.getUserBySocialId(provider, providerId);

        if (user.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "사용자 정보가 존재하지 않습니다."
            ));
        }

        userDto = UserDto.builder()
                .id(user.get().getId())
                .name(user.get().getName())
                .email(user.get().getEmail())
                .profileImage(user.get().getProfileImage())
                .provider(user.get().getProvider())
                .role(user.get().getRole())
                .cash(user.get().getCash())
                .tier(user.get().getTier())
                .build();

        // 사용자 정보를 Map 형태로 변환합니다.
        Map<String, Object> userInfo = Map.of(
                "success", true,
                "message", "사용자 정보 조회 성공",
                "user", userDto
        );
        return ResponseEntity.ok(userInfo);
    }

    //사용자 프로필 이미지 수정
    private final NcpObjectStorageService objectStorageService;

    @Value("${ncp.bucket}")
    private String bucketName;

    @PostMapping("/upload-profile")
    public ResponseEntity<String> uploadProfileImage(@RequestParam MultipartFile image) {
        String imageUrl = objectStorageService.uploadFile(bucketName, "profile", image);
        return ResponseEntity.ok(imageUrl);
    }

    //사용자 정보 수정
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateCurrentUser(
            @CookieValue(name = "jwt_token", required = false) String jwtToken,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        // 1. 인증 정보 확인
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);
        String provider = userDto.getProvider();
        String providerId = userDto.getProviderId();

        // 2. 유저 조회
        Optional<User> userOpt = userService.getUserBySocialId(provider, providerId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "수정 대상 사용자를 찾을 수 없습니다."
            ));
        }

        User user = userOpt.get();

        // 3. 이미지 업로드 처리
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            imageUrl = userService.uploadProfileImage(image);
        }

        // 4. 사용자 정보 업데이트
        User updated = userService.updateUser(user.getId(), name, email, imageUrl);

        // 5. 응답 반환
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "사용자 정보 수정 성공",
                "user", UserDto.builder()
                        .id(updated.getId())
                        .name(updated.getName())
                        .email(updated.getEmail())
                        .profileImage(updated.getProfileImage())
                        .provider(updated.getProvider())
                        .role(updated.getRole())
                        .build()
        ));
    }

    //사용자 탈퇴(is Deleted->1)
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteCurrentUser(
            @CookieValue(name = "jwt_token", required = false) String jwtToken
    ){
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);
        Optional<User> userOpt = userService.getUserBySocialId(userDto.getProvider(), userDto.getProviderId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "사용자 없음"
            ));
        }
        userService.softDeleteUser(userOpt.get().getId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원 탈퇴 처리 완료"
        ));
    }

    @GetMapping("/trade-summary")
    public ResponseEntity<List<TradeSummaryDto>> getTradeSummary(
            @CookieValue(name = "jwt_token", required = false) String jwtToken,
            @RequestParam("seasonId") Integer seasonId) {

        // 1. 토큰에서 유저 식별 정보 추출
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);

        // 2. provider + providerId로 실제 유저 찾기
        Optional<User> userOpt = userService.getUserBySocialId(userDto.getProvider(), userDto.getProviderId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(List.of());
        }

        // 3. 유저, 시즌 가져와서 요약 정보 조회
        User user = userOpt.get();
        Season season = seasonService.getSeasonById(seasonId);
        List<TradeSummaryDto> summaryList = orderService.getTradeSummary(user, season);

        return ResponseEntity.ok(summaryList);
    }

    @GetMapping("/trade-details")
    public ResponseEntity<List<TradeDetailDto>> getTradeDetails(
            @CookieValue(name = "jwt_token", required = false) String jwtToken,
            @RequestParam("seasonId") Integer seasonId) {

        // 1. 인증된 사용자 정보 가져오기
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);
        Optional<User> userOpt = userService.getUserBySocialId(userDto.getProvider(), userDto.getProviderId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(List.of());
        }

        // 2. 시즌과 사용자 기준으로 상세 내역 조회
        User user = userOpt.get();
        Season season = seasonService.getSeasonById(seasonId);
        List<TradeDetailDto> details = orderService.getTradeDetails(user, season);

        return ResponseEntity.ok(details);
    }
}