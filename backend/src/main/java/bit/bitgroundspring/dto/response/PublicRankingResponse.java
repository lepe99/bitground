package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.dto.PublicRankingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicRankingResponse {
    private String seasonName;               // 예: "데브 시즌 44"
    private String updatedAtText;           // 예: "6월 15일 오후 11시 기준"
    private String minutesLeftText;         // 예: "다음 갱신까지 22분 남았습니다"
    private List<PublicRankingDto> rankings; // 상위 5명 리스트
}