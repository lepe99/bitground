package bit.bitgroundspring.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security 필터 단계에서 발생하는 401/403도 컨트롤러 예외와 같은 Problem Details 형식으로 응답한다.
 * 컨트롤러 밖(필터)에서 에러를 응답해야 하는 다른 곳(요청 제한 등)도 {@link #write}를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED, request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN, request.getRequestURI());
    }

    public void write(HttpServletResponse response, ErrorCode code, String path) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ProblemDetails.of(code, null, path));
    }
}
