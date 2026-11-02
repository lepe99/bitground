package bit.bitgroundspring.security.oauth2;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import bit.bitgroundspring.security.token.RefreshToken;
import bit.bitgroundspring.security.token.RefreshTokenService;
import bit.bitgroundspring.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @Value("${jwt.expired_time}")
    private int jwtExpiredTime;

    @Value("${refresh_expired_time}")
    private int refreshExpiredTime;

    /**
     * 액세스 토큰 재발급 (Refresh Token Rotation).
     * 성공: 204 + 새 jwt_token/refresh_token 쿠키, 실패: 401 REFRESH_TOKEN_MISSING / REFRESH_TOKEN_INVALID
     */
    @PostMapping("/refresh")
    @Loggable("토큰 재발급")
    public ResponseEntity<Void> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }

        RefreshToken stored = refreshTokenService.getRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(stored.getUserId())
                .provider(stored.getProvider())
                .providerId(stored.getProviderId())
                .role(stored.getRole())
                .build();

        // 새 액세스 토큰 발급 + 리프레시 토큰 교체(기존 토큰 폐기)
        String newJwtToken = jwtTokenProvider.createTokenByRefresh(refreshTokenEntity);
        String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken, refreshTokenEntity);

        cookieUtil.addCookie(response, "jwt_token", newJwtToken, jwtExpiredTime);
        cookieUtil.addCookie(response, "refresh_token", newRefreshToken, refreshExpiredTime);

        log.info("[login] token refresh. - userId: {}", refreshTokenEntity.getUserId());
        return ResponseEntity.noContent().build();
    }

    // 로그아웃: 쿠키 삭제 + 리프레시 토큰 폐기 (토큰이 없어도 204)
    @PostMapping("/logout")
    @Loggable("로그아웃")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        cookieUtil.deleteCookie(response, "jwt_token");
        cookieUtil.deleteCookie(response, "refresh_token");

        if (refreshToken != null) {
            refreshTokenService.getRefreshToken(refreshToken)
                    .ifPresent(token -> log.info("[login] logout success. - userId: {}", token.getUserId()));
            refreshTokenService.deleteRefreshToken(refreshToken);
        }
        return ResponseEntity.noContent().build();
    }
}
