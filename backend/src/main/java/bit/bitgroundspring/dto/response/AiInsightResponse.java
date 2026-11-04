package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.entity.AiInsight;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일일 AI 인사이트. daily-insight-v2부터 mood·drivers·risks·sources·pickReason이 채워지고, 기존 행은 비어 있다
 *
 * @param score 등락률로 계산한 0~100 값 (50 = 보합). 화면은 mood를 쓴다
 */
public record AiInsightResponse(
        Integer id,
        String symbol,
        Integer score,
        String insight,
        LocalDate date,
        LocalDateTime createdAt,
        String mood,
        List<String> drivers,
        List<String> risks,
        List<Source> sources,
        String pickReason,
        String promptVersion
) {
    public record Source(String id, String title, String url, String publishedAt) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AiInsightResponse from(AiInsight insight) {
        return new AiInsightResponse(insight.getId(), insight.getSymbol(), insight.getScore(),
                insight.getInsight(), insight.getDate(), insight.getCreatedAt(),
                insight.getMood(),
                parse(insight.getDrivers(), new TypeReference<List<String>>() {}),
                parse(insight.getRisks(), new TypeReference<List<String>>() {}),
                parse(insight.getSources(), new TypeReference<List<Source>>() {}),
                insight.getPickReason(), insight.getPromptVersion());
    }

    // 스케줄러가 쓴 JSON. 비었거나 깨졌으면 빈 목록 (본문만으로도 보여줄 수 있음)
    private static <T> List<T> parse(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return List.of();
        }
    }
}
