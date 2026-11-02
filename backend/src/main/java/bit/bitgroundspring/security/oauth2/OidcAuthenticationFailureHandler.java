package bit.bitgroundspring.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OidcAuthenticationFailureHandler implements AuthenticationFailureHandler {
    
    @Value("${react.host}")
    private String reactHost;
    
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        // 상세 사유(클라이언트 설정 오류, 공급자 응답 등)는 서버 로그에만 남기고
        // 브라우저 주소에는 화면 분기용 코드만 넘긴다 (기존: 예외 메시지를 ?error= 에 그대로 노출)
        String errorCode = isCancelled(exception) ? "OAUTH_CANCELLED" : "OAUTH_FAILED";
        log.warn("OAuth login failed ({}): {}", errorCode, exception.getMessage());

        String targetUrl = UriComponentsBuilder.fromUriString(reactHost + "/login")
                .queryParam("error", errorCode)
                .build().toUriString();
        
        response.sendRedirect(targetUrl);
    }

    // 사용자가 동의 화면에서 취소한 경우 (OAuth 표준 오류 코드 access_denied)
    private static boolean isCancelled(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException e
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(e.getError().getErrorCode());
    }
}