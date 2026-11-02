package bit.bitgroundspring.security.token;

import bit.bitgroundspring.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash(value = "refresh", timeToLive = 86400) // 1 day
public class RefreshToken implements Serializable {

    @Id
    private String refreshToken; // Refresh Token
    
    private int userId; // 사용자 ID
    
    private String provider; // OAuth2 제공자 (google, naver, kakao)
    
    private String providerId; // OAuth2 제공자의 subject id
    
    private Role role; // 사용자 권한 (ROLE_USER, ROLE_ADMIN)
    
    
}
