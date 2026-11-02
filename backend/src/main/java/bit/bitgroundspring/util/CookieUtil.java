package bit.bitgroundspring.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {
    
    // 쿠키 추가
    public void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAge / 1000)
                .sameSite("Strict")  // CSRF 방어를 위한 SameSite 설정
                .build();
        
        // 쿠키 헤더 추가
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    // 쿠키 삭제
    public void deleteCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)  // 만료 시간을 0으로 설정하여 쿠키 삭제
                .sameSite("Strict")
                .build();
        
        // 쿠키 헤더 추가
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
