package bit.bitgroundspring.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicRankingDto {
    private String name;             // 유저 이름
    private String profileImage;     // 유저 프로필
    private int tier;                // 티어 번호
    private int totalValue;          // 총 자산 (ex: 20,088,602)
    private int ranks;               // 순위 (1, 2, ...)
}