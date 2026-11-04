package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.entity.Role;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private int id;
    
    private String provider;    // oauth2 제공자 (google, naver, kakao)
    
    private String providerId;  // oauth2 제공자의 subject id
    
    private String name;
    
    private String email;
    
    private String profileImage;
    
    private Role role;
    
    private Long cash; // 사용자의 현금 자산
    
    private Integer tier; // 사용자의 티어 (0~7)

    private boolean onboarded; // 처음 가입 후 프로필 확인 화면을 마쳤는지 (false면 /welcome으로 보냄)

}