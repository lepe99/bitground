// src/main/java/bit/bitgroundspring/service/GeminiService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.dto.response.AiAdviceDto;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.UserDailyBalance;

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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    // 응답이 오지 않으면 요청 스레드가 무한정 묶이지 않도록 끊는다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    // 일시적인 실패(요청 한도·서버 과부하)는 잠시 뒤 다시 시도한다. 시도 사이 대기 시간
    private static final List<Duration> RETRY_BACKOFF = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    // 저장 컬럼보다 넉넉히 짧게 요청한다 (모델이 글자 수를 정확히 지키지는 않음)
    private static final int ADVICE_MAX_CHARS = 1200;

    private final String modelName;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    // HttpClient는 한 번만 생성하여 재사용하는 것이 효율적입니다.
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public GeminiService(@Value("${gemini.model-name}") String modelName,
                         @Value("${gemini.api-key}") String apiKey,
                         ObjectMapper objectMapper) {
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * 투자 요약 데이터를 기반으로 Gemini AI로부터 시즌 리뷰를 생성합니다.
     *
     * @param summary 사용자 투자 요약 DTO
     * @return AiAdviceDto (AI 점수 및 조언 포함)
     * @throws IOException HTTP 요청/응답 처리 중 오류 발생 시 (재시도 후에도 실패)
     * @throws InterruptedException HTTP 요청 중 인터럽트 발생 시
     */
    @Loggable("Gemini 호출")
    public AiAdviceDto generateInvestmentAdvice(InvestmentSummaryDto summary) throws IOException, InterruptedException {
        String prompt = buildPrompt(summary);
        log.debug("[GeminiService] AI 조언 생성 요청 프롬프트:\n{}", prompt);

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
                                        "score", Map.of("type", "INTEGER"),
                                        "advice", Map.of("type", "STRING")
                                ),
                                "required", List.of("score", "advice")
                        )
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL.formatted(modelName)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey) // 키를 URL 쿼리에 노출하지 않음
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = sendWithRetry(request);

        if (response.statusCode() != 200) {
            log.error("[GeminiService] Gemini API 호출 실패. 상태 코드: {}, 응답 본문: {}", response.statusCode(), response.body());
            throw new IOException("Failed to call Gemini API: " + response.statusCode());
        }

        Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = content != null ? (List<Map<String, Object>>) content.get("parts") : null;

            if (parts != null && !parts.isEmpty()) {
                String jsonString = (String) parts.get(0).get("text");
                log.debug("[GeminiService] AI 응답 JSON 문자열: {}", jsonString);
                try {
                    return objectMapper.readValue(jsonString, AiAdviceDto.class);
                } catch (Exception e) {
                    log.error("[GeminiService] AI 응답 JSON 파싱 오류: {}", jsonString, e);
                    throw new IOException("Failed to parse AI advice JSON", e);
                }
            }
        }
        log.warn("[GeminiService] AI 응답에 'parts' 또는 'content'가 없음: {}", response.body());
        throw new IOException("AI response missing content parts.");
    }

    /** 요청 한도 초과·서버 오류·네트워크 오류는 잠시 기다렸다가 다시 보낸다 (최대 RETRY_BACKOFF.size()회) */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            boolean lastAttempt = attempt >= RETRY_BACKOFF.size();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (lastAttempt || !RETRYABLE_STATUS.contains(response.statusCode())) {
                    return response;
                }
                log.warn("[GeminiService] 일시적 실패(상태 코드 {}), {}초 뒤 다시 시도", response.statusCode(), RETRY_BACKOFF.get(attempt).toSeconds());
            } catch (IOException e) { // 타임아웃(HttpTimeoutException) 포함
                if (lastAttempt) throw e;
                log.warn("[GeminiService] 요청 오류({}), {}초 뒤 다시 시도", e.getClass().getSimpleName(), RETRY_BACKOFF.get(attempt).toSeconds());
            }
            Thread.sleep(RETRY_BACKOFF.get(attempt).toMillis());
        }
    }

    /**
     * InvestmentSummaryDto를 기반으로 AI 조언 생성을 위한 프롬프트를 구성합니다.
     * 금액은 원 단위 정수에 천 단위 쉼표, 비어 있는 값은 '정보 없음'으로 넣는다.
     */
    String buildPrompt(InvestmentSummaryDto summary) {
        StringBuilder p = new StringBuilder();
        p.append("당신은 가상 투자 시뮬레이션 게임의 전문 AI 투자 분석가입니다. ");
        p.append("사용자의 과거 투자 기록과 현재 투자 요약을 기반으로, ");
        p.append("다음 시즌을 위한 구체적이고 실행 가능한 조언을 제공해야 합니다.\n");
        p.append("아래 숫자는 이미 정확히 계산된 값이므로 그대로 인용하고, 새로 계산하지 마세요.\n\n");

        p.append("--- 사용자 투자 요약 ---\n");
        p.append("시즌명: ").append(summary.getSeasonName()).append("\n");
        p.append("시즌 기간: ").append(summary.getSeasonStartAt()).append(" ~ ").append(summary.getSeasonEndAt()).append("\n");
        p.append("시즌 중 지급받은 자금(시즌 시작·스플릿 지급 합계): ").append(won(summary.getInitialCashBalance())).append("\n");
        p.append("최종 총 자산: ").append(won(summary.getFinalTotalValue())).append("\n");
        p.append("총 손익 금액: ").append(won(summary.getTotalProfitLossAmount())).append("\n");
        p.append("총 손익률: ").append(summary.getTotalProfitLossPercentage() != null
                ? String.format("%+.2f%%", summary.getTotalProfitLossPercentage()) : "정보 없음").append("\n");
        p.append("최종 순위: ").append(rank(summary)).append("\n");
        p.append("총 거래 횟수: ").append(summary.getTotalTradeCount()).append("회 (시즌 종료 시 보유 코인 일괄 청산 기록 포함)\n");
        p.append("매수 횟수: ").append(summary.getBuyOrderCount()).append("회\n");
        p.append("매도 횟수: ").append(summary.getSellOrderCount()).append("회\n");
        p.append("평균 거래 금액: ").append(won(summary.getAvgTradeAmount())).append("\n");
        if (summary.getCoinRealizedProfitLoss() != null && !summary.getCoinRealizedProfitLoss().isEmpty()) {
            p.append("코인별 손익 (매도 금액 - 매수 금액, 시즌 종료 청산 포함):\n");
            summary.getCoinRealizedProfitLoss().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> p.append("  - ").append(e.getKey()).append(": ").append(signedWon(e.getValue())).append("\n"));
        }
        if (summary.getCoinTradeCounts() != null && !summary.getCoinTradeCounts().isEmpty()) {
            p.append("코인별 거래 횟수:\n");
            summary.getCoinTradeCounts().forEach((coin, count) ->
                    p.append("  - ").append(coin).append(": ").append(count).append("회\n"));
        }
        p.append("가장 많이 거래한 코인: ").append(orNa(summary.getMostTradedCoinSymbol()))
                .append(" (거래 금액: ").append(won(summary.getMostTradedCoinTradeVolume())).append(")\n");
        p.append("가장 수익이 큰 코인: ").append(orNa(summary.getHighestProfitCoinSymbol()))
                .append(" (").append(signedWon(summary.getHighestProfitCoinAmount())).append(")\n");
        p.append("가장 수익이 작은(또는 손실이 큰) 코인: ").append(orNa(summary.getLowestProfitCoinSymbol()))
                .append(" (").append(signedWon(summary.getLowestProfitCoinAmount())).append(")\n");
        p.append("일별 평균 거래 횟수: ").append(String.format("%.2f", summary.getAvgTradesPerDay())).append("회\n");
        p.append("소수 코인(3종 이하)에 집중했는가: ").append(Boolean.TRUE.equals(summary.getFocusedOnFewCoins()) ? "예" : "아니오").append("\n");

        if (summary.getCoinMarketPerformanceSummary() != null && !summary.getCoinMarketPerformanceSummary().isEmpty()) {
            p.append("주요 코인 시즌 내 시세 변동:\n");
            summary.getCoinMarketPerformanceSummary().forEach((coin, perf) ->
                    p.append("  - ").append(coin).append(": ").append(perf).append("\n"));
        }

        if (summary.getDailyBalanceTrend() != null && !summary.getDailyBalanceTrend().isEmpty()) {
            p.append("\n--- 일별 자산 잔고 추이 (최근 7일) ---\n");
            summary.getDailyBalanceTrend().stream()
                    .sorted(Comparator.comparing(UserDailyBalance::getSnapshotDate))
                    .skip(Math.max(0, summary.getDailyBalanceTrend().size() - 7))
                    .forEach(b -> p.append(b.getSnapshotDate()).append(": 총 자산 ").append(won(b.getTotalValue()))
                            .append(", 현금 ").append(won(b.getCashBalance())).append("\n"));
            if (summary.getDailyBalanceTrend().size() > 7) {
                p.append("...(전체 ").append(summary.getDailyBalanceTrend().size()).append("일치 데이터 중 일부)\n");
            }
        }

        p.append("\nAI 조언은 다음 JSON 형식으로 제공해주세요:\n");
        p.append("{\n");
        p.append("  \"score\": 0~100 사이의 정수 평가 점수,\n");
        p.append("  \"advice\": \"한국어 조언. 강점, 약점, 개선점, 다음 시즌 전략 제안을 포함하고, 마크다운 목록(- 항목)과 강조(**단어**)를 써서 ")
                .append("단락·항목 사이에 줄바꿈(\\n)을 넣어 주세요. 공백 포함 ").append(ADVICE_MAX_CHARS).append("자 이내로 작성하세요.\"\n");
        p.append("}\n");

        return p.toString();
    }

    private static String rank(InvestmentSummaryDto s) {
        if (s.getFinalRank() == null) return "랭킹 없음";
        if (s.getParticipantCount() == null || s.getParticipantCount() <= 0) return s.getFinalRank() + "위";
        int topPercent = (int) Math.max(1, Math.ceil(s.getFinalRank() * 100.0 / s.getParticipantCount()));
        return String.format("%d위 / %d명 (상위 %d%%)", s.getFinalRank(), s.getParticipantCount(), topPercent);
    }

    private static String won(Long value) {
        return value == null ? "정보 없음" : String.format("%,d원", value);
    }

    private static String signedWon(Long value) {
        return value == null ? "정보 없음" : String.format("%+,d원", value);
    }

    private static String orNa(String value) {
        return value != null ? value : "없음";
    }
}
