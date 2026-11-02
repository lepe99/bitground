package bit.bitgroundspring.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor // ObjectMapper가 JSON을 객체로 변환할 때 필요
@AllArgsConstructor // 모든 필드를 포함한 생성자
public class AiAdviceDto {

    // AiAdvice 엔티티의 핵심 정보
    private Integer id; // AiAdvice 엔티티의 ID
    private Integer userId; // 조언 대상 사용자 ID
    private Integer seasonId; // 조언 대상 시즌 ID
    private LocalDateTime createdAt; // 조언 생성 일시

    // AI가 생성한 결과 (기존 AiAdviceResult의 내용)
    private Integer score; // AI가 평가한 투자 성과 점수
    private String advice; // AI가 제공하는 구체적인 조언 내용
}
