package bit.bitgroundspring.security.token;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
   
   private final RefreshTokenRepository refreshTokenRepository;
   
    // Refresh Token 생성 및 저장
    public String createRefreshToken(RefreshToken refreshToken) {
        // UUID를 사용하여 Refresh Token 생성
        String token = UUID.randomUUID().toString();
        
        // Refresh Token 객체 저장
        refreshToken.setRefreshToken(token);
        refreshTokenRepository.save(refreshToken);
        
        // 생성된 Refresh Token 반환
        return token;
    }
    
    // Refresh Token 조회
    public Optional<RefreshToken> getRefreshToken(String token) {
        return refreshTokenRepository.findById(token);
    }
    
    // Refresh Token 삭제
    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteById(token);
    }
    
    // Refresh Token 로테이션
    public String rotateRefreshToken(String oldToken, RefreshToken refreshToken) {
        // 기존 Refresh Token 삭제
        deleteRefreshToken(oldToken);
        
        // 새로운 Refresh Token 생성 및 저장
        return createRefreshToken(refreshToken);
    }
    
}
