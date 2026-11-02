package bit.bitgroundspring.controller;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.security.token.AuthTokenService;
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

    private final AuthTokenService authTokenService;
    private final CookieUtil cookieUtil;

    @Value("${jwt.expired_time}")
    private int jwtExpiredTime;

    @Value("${refresh_expired_time}")
    private int refreshExpiredTime;

    /**
     * 액세스 토큰 재발급 (Refresh Token Rotation).
     * 성공: 204 + 새 jwt_token/refresh_token 쿠키, 실패: 401 REFRESH_TOKEN_MISSING / REFRESH_TOKEN_INVALID
     * 탈퇴한 사용자, 폐기·만료된 토큰, 이미 교체된 토큰(재사용 → 세션 전체 폐기)은 401
     */
    @PostMapping("/refresh")
    @Loggable("토큰 재발급")
    public ResponseEntity<Void> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }

        try {
            // 교체 직후 같은 브라우저의 동시 요청이면 새 토큰 없이 204 (먼저 끝난 요청이 이미 새 쿠키를 받음)
            authTokenService.refresh(refreshToken).ifPresent(tokens -> {
                cookieUtil.addCookie(response, "jwt_token", tokens.accessToken(), jwtExpiredTime);
                cookieUtil.addCookie(response, "refresh_token", tokens.refreshToken(), refreshExpiredTime);
            });
        } catch (BusinessException e) {
            // 쓸 수 없는 토큰이 브라우저에 남아 재시도가 반복되지 않도록 쿠키를 지운다
            cookieUtil.deleteCookie(response, "jwt_token");
            cookieUtil.deleteCookie(response, "refresh_token");
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    // 로그아웃: 쿠키 삭제 + 이 세션의 리프레시 토큰 폐기 (토큰이 없어도 204)
    @PostMapping("/logout")
    @Loggable("로그아웃")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        cookieUtil.deleteCookie(response, "jwt_token");
        cookieUtil.deleteCookie(response, "refresh_token");

        if (refreshToken != null && !refreshToken.isEmpty()) {
            authTokenService.logout(refreshToken);
        }
        return ResponseEntity.noContent().build();
    }
}
