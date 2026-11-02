package bit.bitgroundspring.security.oauth2;

import bit.bitgroundspring.util.CookieUtil;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import bit.bitgroundspring.security.token.RefreshToken;
import bit.bitgroundspring.security.token.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;
    
    @Value("${jwt.expired_time}")
    private int jwtExpiredTime;
    
    @Value("${refresh_expired_time}")
    private int refreshExpiredTime;
    
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            if (refreshToken == null || refreshToken.isEmpty()) {
                Map<String, Object> errorResponse = Map.of(
                        "success", false,
                        "message", "리프레시 토큰이 없습니다."
                );
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            // 리프레시 토큰 검증
            Optional<RefreshToken> refreshTokenObj = refreshTokenService.getRefreshToken(refreshToken);
            if (refreshTokenObj.isEmpty()) {
                Map<String, Object> errorResponse = Map.of(
                        "success", false,
                        "message", "리프레시 토큰이 유효하지 않습니다."
                );
                return ResponseEntity.status(401).body(errorResponse);
            }
            RefreshToken refreshTokenEntity = RefreshToken.builder()
                    .userId(refreshTokenObj.get().getUserId())
                    .provider(refreshTokenObj.get().getProvider())
                    .providerId(refreshTokenObj.get().getProviderId())
                    .role(refreshTokenObj.get().getRole())
                    .build();
       
            // JWT 토큰 재발급
            String newJwtToken = jwtTokenProvider.createTokenByRefresh(refreshTokenEntity);
            
            // 리프레시 토큰 로테이션
            String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken, refreshTokenEntity);
            
            // 쿠키에 새로운 jwt_token, refresh_token 저장
            cookieUtil.addCookie(response, "jwt_token", newJwtToken, jwtExpiredTime);
            cookieUtil.addCookie(response, "refresh_token", newRefreshToken, refreshExpiredTime);
            
            // 토큰에서 userId 추출 후 로그 기록
            int userId = refreshTokenEntity.getUserId();
            log.info("[login] token refresh. - userId: {}", userId);
            
            Map<String, Object> successResponse = Map.of(
                    "success", true,
                    "message", "리프레시 토큰 갱신 성공"
            );
            return ResponseEntity.ok(successResponse);
            
        } catch (Exception e) {
            // 서버 오류 처리
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "서버 오류: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            // 쿠키에서 jwt_token, refresh_token 삭제
            cookieUtil.deleteCookie(response, "jwt_token");
            cookieUtil.deleteCookie(response, "refresh_token");
            
            // 로그 기록
            Optional<RefreshToken> refreshTokenObj = refreshTokenService.getRefreshToken(refreshToken);
            if (refreshTokenObj.isPresent()) {
                int userId = refreshTokenObj.get().getUserId();
                log.info("[login] logout success. - userId: {}", userId);
            }
            
            // refresh_token redis에서 삭제
            refreshTokenService.deleteRefreshToken(refreshToken);
            
            Map<String, Object> successResponse = Map.of(
                    "success", true,
                    "message", "로그아웃 성공"
            );
            return ResponseEntity.ok(successResponse);
           
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "서버 오류: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
