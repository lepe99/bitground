package bit.bitgroundspring.security.token;

import bit.bitgroundspring.common.logging.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlocklist accessTokenBlocklist;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        
        // 쿠키에서 jwt_token 값 추출
        String token = extractJwtFromCookies(request);
        
        // JWT 토큰이 존재하고 유효하며, 탈퇴 등으로 무효화되지 않은 경우 인증 처리
        if (StringUtils.hasText(token)) {
            jwtTokenProvider.parse(token)
                    .filter(claims -> !accessTokenBlocklist.isRevoked(claims.get("userId", Integer.class), claims.getIssuedAt()))
                    .map(jwtTokenProvider::getAuthentication)
                    .ifPresent(authentication -> {
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // 이후 로그에 사용자 ID가 함께 남도록 (TraceIdFilter가 요청 종료 시 정리)
                        if (authentication.getPrincipal() instanceof AuthUser user) {
                            MDC.put(TraceIdFilter.USER_ID, String.valueOf(user.id()));
                        }
                    });
        }
        
        chain.doFilter(request, response);
    }
    
    // 인증이 필요없는 경로 설정
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 인증이 필요없는 경로들
        return path.equals("/") ||
                path.equals("/auth/refresh") ||
                path.startsWith("/oauth2/") ||
                path.startsWith("/actuator/") ||
                path.equals("/error") ||
                path.startsWith("/public/");
    }
    
    // 쿠키에서 JWT 토큰 추출
    private String extractJwtFromCookies(HttpServletRequest request) {
        Cookie jwtCookie = WebUtils.getCookie(request, "jwt_token");
        return jwtCookie != null ? jwtCookie.getValue() : null;
    }
}
