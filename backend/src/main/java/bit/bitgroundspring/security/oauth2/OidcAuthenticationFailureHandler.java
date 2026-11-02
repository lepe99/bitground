package bit.bitgroundspring.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
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
        
        // 오류 메시지 로깅
        String errorMessage = exception.getMessage();
        log.error("Authentication failed: {}", errorMessage);
        
        // 클라이언트로 리다이렉트 (오류 정보 포함)
        String targetUrl = UriComponentsBuilder.fromUriString(reactHost + "/login")
                .queryParam("error", errorMessage)
                .build().toUriString();
        
        response.sendRedirect(targetUrl);
    }
}