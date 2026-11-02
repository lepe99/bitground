// src/main/java/bit/bitgroundspring/service/GeminiService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.AiAdviceDto;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.UserDailyBalance; // UserDailyBalance import

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    // 이 필드는 이제 사용되지 않을 수 있지만, 기존 구조 유지를 위해 둡니다.
    private final String modelName;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    // HttpClient는 한 번만 생성하여 재사용하는 것이 효율적입니다.
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    public GeminiService(@Value("${gemini.model-name}") String modelName,
                         @Value("${gemini.api-key}") String apiKey,
                         ObjectMapper objectMapper) {
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * 투자 요약 데이터를 기반으로 Gemini AI로부터 투자 조언을 생성합니다.
     *
     * @param summary 사용자 투자 요약 DTO
     * @return AiAdviceDto (AI 점수 및 조언 포함)
     * @throws IOException HTTP 요청/응답 처리 중 오류 발생 시
     * @throws InterruptedException HTTP 요청 중 인터럽트 발생 시
     */
    public AiAdviceDto generateInvestmentAdvice(InvestmentSummaryDto summary) throws IOException, InterruptedException {
        String prompt = buildPrompt(summary);
        log.info("[GeminiService] AI 조언 생성 요청 프롬프트:\n{}", prompt);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "score", Map.of("type", "NUMBER"),
                                        "advice", Map.of("type", "STRING")
                                ),
                                "required", List.of("score", "advice")
                        )
                )
        );

        String jsonRequestBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");

            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

                if (parts != null && !parts.isEmpty()) {
                    String jsonString = (String) parts.get(0).get("text");
                    log.info("[GeminiService] AI 응답 JSON 문자열: {}", jsonString);

                    try {
                        // JSON 문자열을 직접 AiAdviceDto로 파싱
                        return objectMapper.readValue(jsonString, AiAdviceDto.class);
                    } catch (Exception e) {
                        log.error("[GeminiService] AI 응답 JSON 파싱 오류: {}", jsonString, e);
                        throw new IOException("Failed to parse AI advice JSON: " + jsonString, e);
                    }
                }
            }
            log.warn("[GeminiService] AI 응답에 'parts' 또는 'content'가 없음: {}", response.body());
            throw new IOException("AI response missing content parts.");
        } else {
            log.error("[GeminiService] Gemini API 호출 실패. 상태 코드: {}, 응답 본문: {}",
                    response.statusCode(), response.body());
            throw new IOException("Failed to call Gemini API: " + response.statusCode() + " " + response.body());
        }
    }

    /**
     * InvestmentSummaryDto를 기반으로 AI 조언 생성을 위한 프롬프트를 구성합니다.
     * @param summary 투자 요약 DTO
     * @return AI에게 전달할 프롬프트 문자열
     */
    private String buildPrompt(InvestmentSummaryDto summary) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("당신은 가상 투자 시뮬레이션 게임의 전문 AI 투자 분석가입니다. ");
        promptBuilder.append("사용자의 과거 투자 기록과 현재 투자 요약을 기반으로, ");
        promptBuilder.append("다음 시즌을 위한 구체적이고 실행 가능한 조언을 제공해야 합니다.\n\n");
        promptBuilder.append("--- 사용자 투자 요약 ---\n");
        promptBuilder.append("시즌명: ").append(summary.getSeasonName()).append("\n");
        promptBuilder.append("시즌 기간: ").append(summary.getSeasonStartAt()).append(" ~ ").append(summary.getSeasonEndAt()).append("\n");
        promptBuilder.append("초기 현금: ").append(summary.getInitialCashBalance()).append("원\n");
        promptBuilder.append("최종 총 자산: ").append(summary.getFinalTotalValue()).append("원\n");
        promptBuilder.append("총 손익 금액: ").append(summary.getTotalProfitLossAmount()).append("원\n");
        promptBuilder.append("총 손익률: ").append(String.format("%.2f", summary.getTotalProfitLossPercentage())).append("%%\n");
        promptBuilder.append("최종 랭크: ").append(summary.getFinalRank() != null ? summary.getFinalRank() + "위" : "랭크 없음").append("\n");
        promptBuilder.append("총 거래 횟수: ").append(summary.getTotalTradeCount()).append("회\n");
        promptBuilder.append("매수 횟수: ").append(summary.getBuyOrderCount()).append("회\n");
        promptBuilder.append("매도 횟수: ").append(summary.getSellOrderCount()).append("회\n");
        promptBuilder.append("평균 거래 금액: ").append(String.format("%.2f", summary.getAvgTradeAmount())).append("원\n");
        promptBuilder.append("총 실현 손익: ").append(String.format("%.2f", summary.getTotalRealizedProfitLoss())).append("원\n");
        if (summary.getCoinRealizedProfitLoss() != null && !summary.getCoinRealizedProfitLoss().isEmpty()) {
            promptBuilder.append("코인별 실현 손익:\n");
            summary.getCoinRealizedProfitLoss().forEach((coin, pl) ->
                    promptBuilder.append("  - ").append(coin).append(": ").append(String.format("%.2f", pl)).append("원\n"));
        }
        if (summary.getCoinTradeCounts() != null && !summary.getCoinTradeCounts().isEmpty()) {
            promptBuilder.append("코인별 거래 횟수:\n");
            summary.getCoinTradeCounts().forEach((coin, count) ->
                    promptBuilder.append("  - ").append(coin).append(": ").append(count).append("회\n"));
        }
        promptBuilder.append("가장 많이 거래한 코인: ").append(summary.getMostTradedCoinSymbol() != null ? summary.getMostTradedCoinSymbol() : "N/A").append(" (거래량: ").append(String.format("%.2f", summary.getMostTradedCoinTradeVolume())).append("원)\n");
        promptBuilder.append("가장 높은 수익 코인: ").append(summary.getHighestProfitCoinSymbol() != null ? summary.getHighestProfitCoinSymbol() : "N/A").append(" (수익: ").append(String.format("%.2f", summary.getHighestProfitCoinAmount())).append("원)\n");
        promptBuilder.append("가장 낮은 수익 코인 (또는 손실 코인): ").append(summary.getLowestProfitCoinSymbol() != null ? summary.getLowestProfitCoinSymbol() : "N/A").append(" (손실: ").append(String.format("%.2f", summary.getLowestProfitCoinAmount())).append("원)\n");
        promptBuilder.append("일별 평균 거래 횟수: ").append(String.format("%.2f", summary.getAvgTradesPerDay())).append("회\n");
        promptBuilder.append("소수 코인에 집중했는가: ").append(summary.getFocusedOnFewCoins() ? "예" : "아니오").append("\n");

        if (summary.getCoinMarketPerformanceSummary() != null && !summary.getCoinMarketPerformanceSummary().isEmpty()) {
            promptBuilder.append("주요 코인 시즌 내 시세 변동:\n");
            summary.getCoinMarketPerformanceSummary().forEach((coin, perf) ->
                    promptBuilder.append("  - ").append(coin).append(": ").append(perf).append("\n"));
        }

        if (summary.getDailyBalanceTrend() != null && !summary.getDailyBalanceTrend().isEmpty()) {
            promptBuilder.append("\n--- 일별 자산 잔고 추이 (일부) ---\n");
            // 최신 7일치 또는 전체 데이터를 포함 (프롬프트 길이 제한 고려)
            List<UserDailyBalance> recentBalances = summary.getDailyBalanceTrend().stream()
                    .sorted((b1, b2) -> b2.getSnapshotDate().compareTo(b1.getSnapshotDate())) // 최신 날짜부터 정렬
                    .limit(7) // 최대 7개만 포함
                    .collect(Collectors.toList());

            for (UserDailyBalance balance : recentBalances) {
                promptBuilder.append(balance.getSnapshotDate()).append(": 총 자산 ")
                        .append(balance.getTotalValue()).append("원, 현금 잔고 ")
                        .append(balance.getCashBalance()).append("원\n");
            }
            if (summary.getDailyBalanceTrend().size() > 7) {
                promptBuilder.append("...(전체 ").append(summary.getDailyBalanceTrend().size()).append("일치 데이터 중 일부)\n");
            }
        }

        promptBuilder.append("\nAI 조언은 다음 JSON 형식으로 제공해주세요:\n");
        promptBuilder.append("```json\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"score\": [0-100 사이의 AI 평가 점수 (정수)],\n");
        promptBuilder.append("  \"advice\": \"[구체적이고 상세한 한국어 투자 조언. 강점, 약점, 개선점, 그리고 다음 시즌을 위한 구체적인 전략 제안을 포함해주세요. 조언은 여러 단락으로 구성하고, 필요에 따라 마크다운 형식의 목록(- 아이템) 또는 강조(**단어**)를 사용하여 가독성을 높여주세요. 각 단락 또는 목록 항목 사이에 줄바꿈(\\n) 문자를 포함하여 주세요.]\"\n"); // ✅ 조언 상세화 및 줄바꿈 지시 추가
        promptBuilder.append("}\n");
        promptBuilder.append("```");

        return promptBuilder.toString();
    }
}
