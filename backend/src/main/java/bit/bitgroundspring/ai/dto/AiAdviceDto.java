package bit.bitgroundspring.ai.dto;

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
    private Integer score; // 시즌 점수 (season-review-v2부터 코드가 계산)
    private String advice; // v1: 마크다운 본문 / season-review-v2: SeasonReviewContent JSON
    private String promptVersion;
}
