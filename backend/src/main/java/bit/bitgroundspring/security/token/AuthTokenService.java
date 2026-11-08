package bit.bitgroundspring.security.token;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 로그인 토큰(액세스 JWT + 리프레시 토큰) 발급·재발급·폐기
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final UserRepository userRepository;

    public record Tokens(String accessToken, String refreshToken) {
    }

    /** 로그인 성공 시 새 세션 발급 */
    public Tokens issue(User user) {
        return new Tokens(jwtTokenProvider.createToken(user), refreshTokenService.issue(user.getId()));
    }

    /**
     * 재발급 (Refresh Token Rotation)
     * 사용자 존재·탈퇴 여부와 현재 권한을 DB에서 다시 확인한 뒤 새 토큰을 발급한다.
     *
     * @return 새 토큰. 교체 직후 동시 요청(grace)이면 empty — 다른 요청이 이미 새 쿠키를 받았으므로 그대로 둔다
     */
    public Optional<Tokens> refresh(String refreshToken) {
        int userId = refreshTokenService.findUserId(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        User user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElse(null);
        if (user == null) {
            refreshTokenService.revokeAll(userId);
            log.warn("[login] refresh rejected: user deleted or missing. - userId: {}", userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return switch (rotation.status()) {
            case OK -> Optional.of(new Tokens(jwtTokenProvider.createToken(user), rotation.newToken()));
            case GRACE -> Optional.empty();
            case REUSED -> {
                log.warn("[login] refresh token reuse detected, session revoked. - userId: {}", userId);
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
            }
            case INVALID -> throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        };
    }

    /** 로그아웃: 해당 세션만 폐기 */
    public void logout(String refreshToken) {
        refreshTokenService.findUserId(refreshToken)
                .ifPresent(userId -> log.info("[login] logout success. - userId: {}", userId));
        refreshTokenService.revoke(refreshToken);
    }

    /** 탈퇴: 모든 기기의 세션과 이미 발급된 액세스 토큰을 즉시 무효화 */
    public void revokeAll(int userId) {
        long sessions = refreshTokenService.revokeAll(userId);
        accessTokenBlocklist.revokeAll(userId);
        log.info("[login] all sessions revoked. - userId: {}, sessions: {}", userId, sessions);
    }
}
