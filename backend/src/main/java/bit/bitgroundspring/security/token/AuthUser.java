package bit.bitgroundspring.security.token;

import java.security.Principal;

/**
 * JWT에서 꺼낸 로그인 사용자 정보.
 * 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 주입받아,
 * 요청 본문이나 파라미터로 넘어온 userId 대신 사용한다.
 */
public record AuthUser(Integer id, String providerId, String provider, boolean admin) implements Principal {

    // 기존 코드의 authentication.getName() 이 providerId를 반환하도록 유지
    @Override
    public String getName() {
        return providerId;
    }
}
