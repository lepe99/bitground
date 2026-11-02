package bit.bitgroundspring.dto;

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
    
    private Integer cash; // 사용자의 현금 자산
    
    private Integer tier; // 사용자의 티어 (0~7)
    
}