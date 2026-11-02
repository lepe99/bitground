package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.entity.AiInsight;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AiInsightResponse(
        Integer id,
        String symbol,
        Integer score,
        String insight,
        LocalDate date,
        LocalDateTime createdAt
) {
    public static AiInsightResponse from(AiInsight insight) {
        return new AiInsightResponse(insight.getId(), insight.getSymbol(), insight.getScore(),
                insight.getInsight(), insight.getDate(), insight.getCreatedAt());
    }
}
