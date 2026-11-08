package bit.bitgroundspring.user;

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
    
    private Integer tier; // 지난 시즌에 확정된 티어 (0~7, 게시글·댓글 등에 표시)

    private Integer currentTier; // 이번 시즌 티어 (매시 갱신, 이번 시즌 랭킹에 없으면 tier). 헤더·마이페이지 프로필 링

    private boolean onboarded; // 처음 가입 후 프로필 확인 화면을 마쳤는지 (false면 /welcome으로 보냄)

}