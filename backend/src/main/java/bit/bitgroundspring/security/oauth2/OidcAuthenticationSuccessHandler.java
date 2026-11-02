package bit.bitgroundspring.security.oauth2;

import bit.bitgroundspring.util.CookieUtil;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import bit.bitgroundspring.security.token.RefreshToken;
import bit.bitgroundspring.security.token.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OidcAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final CookieUtil cookieUtil;
    private final RefreshTokenService refreshTokenService;
    
    @Value("${react.host}")
    private String reactHost;
    
    @Value("${jwt.expired_time}")
    private int jwtExpiredTime;
    
    @Value("${refresh_expired_time}")
    private int refreshExpiredTime;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String provider = extractProvider(request);
        String providerId = oidcUser.getSubject();
        
        // 사용자 정보 조회
        Optional<User> userOptional = userRepository.findByProviderAndProviderId(provider, providerId);
        
        if (userOptional.isEmpty()) {
            // 이 부분은 이론적으로 발생하지 않아야 함 (CustomOidcUserService에서 사용자를 이미 저장했기 때문)
            response.sendRedirect("/login?error=user_not_found");
            return;
        }
        
        User user = userOptional.get();
        int userId = user.getId();
        
        // JWT 토큰, Refresh 토큰 생성
        String token = jwtTokenProvider.createToken(authentication, userId, provider);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(userId)
                .provider(provider)
                .providerId(providerId)
                .role(user.getRole())
                .build();
        String refreshToken = refreshTokenService.createRefreshToken(refreshTokenEntity);
        
        // JWT 토큰, Refresh 토큰 쿠키에 저장
        cookieUtil.addCookie(response, "jwt_token", token, jwtExpiredTime);
        cookieUtil.addCookie(response, "refresh_token", refreshToken, refreshExpiredTime);
        
        // 정상 로그인 처리
        String targetUrl = UriComponentsBuilder.fromUriString(reactHost + "/auth/callback")
                .build().toUriString();
        
        // 로그 출력
        log.info("[login] login success. - userId: {}", userId);
        
        // 리다이렉트 실행
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
    
    private String extractProvider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String[] segments = uri.split("/");
        // "/login/oauth2/code/{provider}" 형식의 URL에서 provider 추출
        return segments[segments.length - 1];
    }
    
}
