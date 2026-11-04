// src/main/java/bit/bitgroundspring/service/GeminiService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.dto.SeasonReviewContent;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 시즌 리뷰 생성 (Gemini). 숫자와 점수는 코드가 계산해 넘기고, 모델은 해석·회고 문장만 쓴다.
 * 프롬프트나 출력 형식을 바꾸면 PROMPT_VERSION을 올린다 (저장된 리뷰가 어떤 버전으로 만들어졌는지 남김)
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    public static final String PROMPT_VERSION = "season-review-v2";

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    // 응답이 오지 않으면 요청 스레드가 무한정 묶이지 않도록 끊는다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    // 일시적인 실패(요청 한도·서버 과부하)는 잠시 뒤 다시 시도한다. 시도 사이 대기 시간
    private static final List<Duration> RETRY_BACKOFF = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    // 형식이 틀렸거나 금지 표현이 나온 응답은 한 번 더 생성해 본다
    private static final int MAX_GENERATIONS = 2;
    // 회고가 투자 권유처럼 읽히는 표현 (약관: 특정 코인 매매를 추천하지 않음)
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(매수|매도|구매|판매)(하세요|하십시오|를 추천|을 추천|를 권|을 권)|추천합니다|목표가|목표 수익률|반드시 오|확실히 오|우상향 코인|유망 코인");

    static final String SYSTEM_INSTRUCTION = """
            너는 모의투자 게임 Bitground의 시즌 회고 코치다. 플레이어의 지난 시즌 기록을 보고 무엇이 잘 됐고 무엇을 바꾸면 좋을지 정리한다.
            규칙:
            1. <season>에 있는 숫자와 사실만 근거로 쓴다. 숫자는 그대로 옮기고 새로 계산하지 않는다. 없는 정보(뉴스, 시장 이벤트 등)는 지어내지 않는다.
            2. 점수와 구성 요소는 이미 계산되어 있다. 점수를 바꾸거나 새로 매기지 말고, scoreReason에서 어떤 요소가 점수를 올리고 내렸는지 설명한다.
            3. 특정 코인의 매수·매도 추천, 유망 코인 제시, 목표 수익률 약속은 하지 않는다. 다음 시즌 제안은 분산, 손실 관리, 거래 빈도, 기록 복기 같은 행동 습관으로 쓴다.
            4. 시장 비교 기준이 있으면 수익률을 그것과 비교해 평가한다 (시장보다 나았는지).
            5. 한국어 존댓말 평서문. headline 40자 이내, 나머지 항목은 각각 1~2문장(80자 안팎). strengths·weaknesses는 각 2~3개, nextSeason은 2~3개.
            6. 숫자는 <season>의 표기 그대로 쓴다 (예: +3.59%, -8.1%, 120,000원). '플러스', '퍼센트'처럼 풀어 쓰지 않는다.
            7. 각 항목은 문장만 쓴다. 굵게(**), 제목(#), 줄머리 목록 기호는 쓰지 않는다.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "headline", Map.of("type", "STRING"),
                    "strengths", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "weaknesses", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "nextSeason", Map.of("type", "ARRAY", "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "action", Map.of("type", "STRING"),
                                    "reason", Map.of("type", "STRING")),
                            "required", List.of("action", "reason"))),
                    "scoreReason", Map.of("type", "STRING")
            ),
            "required", List.of("headline", "strengths", "weaknesses", "nextSeason", "scoreReason")
    );

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

    public String getModelName() {
        return modelName;
    }

    /**
     * 시즌 리뷰 생성. 형식이 틀렸거나 투자 권유 표현이 섞이면 한 번 더 생성하고, 그래도 안 되면 실패로 본다
     *
     * @param summary 시즌 요약 (숫자는 모두 계산된 값)
     * @param score   코드가 계산한 점수와 구성 요소
     * @throws IOException 호출 실패(재시도 후) 또는 쓸 수 있는 응답을 못 받음
     */
    @Loggable("Gemini 호출")
    public SeasonReviewContent generateSeasonReview(InvestmentSummaryDto summary, SeasonReviewScorer.Score score)
            throws IOException, InterruptedException {
        String userPrompt = buildPrompt(summary, score);
        log.debug("[GeminiService] 시즌 리뷰 프롬프트:\n{}", userPrompt);

        String problem = null;
        for (int i = 0; i < MAX_GENERATIONS; i++) {
            SeasonReviewContent content = requestReview(userPrompt);
            problem = validate(content, userPrompt);
            if (problem == null) return content;
            log.warn("[GeminiService] 시즌 리뷰 응답 거절({}/{}): {}", i + 1, MAX_GENERATIONS, problem);
        }
        throw new IOException("AI review rejected: " + problem);
    }

    SeasonReviewContent requestReview(String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.3, // 매번 비슷한 톤과 품질
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA
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
                String json = (String) parts.get(0).get("text");
                log.debug("[GeminiService] AI 응답 JSON 문자열: {}", json);
                try {
                    return objectMapper.readValue(json, SeasonReviewContent.class);
                } catch (Exception e) {
                    log.error("[GeminiService] AI 응답 JSON 파싱 오류: {}", json, e);
                    throw new IOException("Failed to parse AI review JSON", e);
                }
            }
        }
        log.warn("[GeminiService] AI 응답에 'parts' 또는 'content'가 없음: {}", response.body());
        throw new IOException("AI response missing content parts.");
    }

    /** 형식·금지 표현에 더해, 숫자는 맞게 옮겼어도 단어를 틀린 실수(순위, 수치의 방향)를 입력과 대조한다 */
    static String validate(SeasonReviewContent c, String prompt) {
        String problem = validate(c);
        if (problem != null || prompt == null) return problem;
        return checkWordConsistency(allText(c), prompt);
    }

    // 수치 바로 뒤의 방향 단어 ('+3.59%의 수익', '-120,000원 손실', '2.84% 하락')
    private static final Pattern DIRECTION_CLAIM = Pattern.compile(
            "([+-]?)(\\d[\\d,]*(?:\\.\\d+)?)(%p|%|원)\\s?(?:의|로|가|이|만큼|가량|정도)?\\s?"
                    + "(상승|올랐|올라|오른|반등|급등|증가|늘었|늘어|수익|이익|흑자|하락|내렸|내려|내린|떨어|급락|감소|줄었|줄어|손실|적자)");
    private static final Set<String> UP_WORDS = Set.of("상승", "올랐", "올라", "오른", "반등", "급등", "증가", "늘었", "늘어", "수익", "이익", "흑자");
    // 입력의 부호 있는 수치
    private static final Pattern SIGNED_VALUE = Pattern.compile("([+-])(\\d[\\d,]*(?:\\.\\d+)?)(%p|%|원)");
    private static final Pattern RANK_CLAIM = Pattern.compile("(\\d+)위");

    /** 순위(N위)가 입력에 있는지, 부호 있는 수치의 방향 단어·부호가 입력과 맞는지. 맞으면 null */
    static String checkWordConsistency(String text, String prompt) {
        Matcher rank = RANK_CLAIM.matcher(text);
        while (rank.find()) {
            if (!Pattern.compile("(?<!\\d)" + rank.group(1) + "위").matcher(prompt).find()) {
                return "입력과 다른 순위: " + rank.group();
            }
        }

        // 수치별 입력 부호 (같은 값이 +와 -로 모두 나오면 판단하지 않는다)
        Map<String, Set<String>> signs = new HashMap<>();
        Matcher signed = SIGNED_VALUE.matcher(prompt);
        while (signed.find()) {
            if (signed.group(3).equals("%p")) continue;
            signs.computeIfAbsent(signed.group(2).replace(",", "") + signed.group(3), k -> new HashSet<>()).add(signed.group(1));
        }
        Matcher claim = DIRECTION_CLAIM.matcher(text);
        while (claim.find()) {
            // '수익률'·'상승률'처럼 '률'이 붙으면 방향이 아니라 지표 이름이다 ('-27.35% 수익률')
            if (claim.group(3).equals("%p") || (claim.end() < text.length() && text.charAt(claim.end()) == '률')) continue;
            Set<String> seen = signs.get(claim.group(2).replace(",", "") + claim.group(3));
            if (seen == null || seen.size() != 1) continue;
            String actual = seen.iterator().next();
            if (!claim.group(1).isEmpty() && !claim.group(1).equals(actual)) return "입력과 부호가 다른 수치: " + claim.group();
            if (UP_WORDS.contains(claim.group(4)) != actual.equals("+")) return "수치와 방향이 맞지 않음: " + claim.group();
        }
        return null;
    }

    static String allText(SeasonReviewContent c) {
        StringBuilder all = new StringBuilder(c.headline()).append(' ').append(c.scoreReason());
        c.strengths().forEach(s -> all.append(' ').append(s));
        c.weaknesses().forEach(s -> all.append(' ').append(s));
        c.nextSeason().forEach(a -> all.append(' ').append(a.action()).append(' ').append(a.reason()));
        return all.toString();
    }

    /** 쓸 수 있는 응답이면 null, 아니면 이유 (형식·금지 표현·길이) */
    static String validate(SeasonReviewContent c) {
        if (c == null || isBlank(c.headline()) || isBlank(c.scoreReason())) return "빈 항목";
        if (c.strengths() == null || c.strengths().isEmpty() || c.strengths().stream().anyMatch(GeminiService::isBlank)) return "잘한 점 없음";
        if (c.weaknesses() == null || c.weaknesses().isEmpty() || c.weaknesses().stream().anyMatch(GeminiService::isBlank)) return "아쉬운 점 없음";
        if (c.nextSeason() == null || c.nextSeason().isEmpty()
                || c.nextSeason().stream().anyMatch(a -> a == null || isBlank(a.action()) || isBlank(a.reason()))) return "다음 시즌 제안 없음";
        if (c.strengths().size() > 5 || c.weaknesses().size() > 5 || c.nextSeason().size() > 5) return "항목이 너무 많음";

        String all = allText(c);
        var forbidden = FORBIDDEN.matcher(all);
        if (forbidden.find()) return "투자 권유 표현: " + forbidden.group();
        if (all.length() > 2500) return "너무 김";
        return null;
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
     * 모델에 넘길 시즌 기록. 금액은 원 단위 정수에 천 단위 쉼표, 비어 있는 값은 '정보 없음'
     */
    static String buildPrompt(InvestmentSummaryDto s, SeasonReviewScorer.Score score) {
        StringBuilder p = new StringBuilder("<season>\n");
        p.append("시즌: ").append(s.getSeasonName()).append(" (").append(s.getSeasonStartAt()).append(" ~ ").append(s.getSeasonEndAt()).append(")\n");
        p.append("시즌 중 지급받은 자금: ").append(won(s.getInitialCashBalance())).append(" (시즌 시작·스플릿 지급 합계)\n");
        p.append("최종 총 자산: ").append(won(s.getFinalTotalValue())).append("\n");
        p.append("총 손익: ").append(signedWon(s.getTotalProfitLossAmount()))
                .append(" / 수익률: ").append(percent(s.getTotalProfitLossPercentage())).append("\n");
        p.append("순위: ").append(rank(s)).append("\n");
        p.append("같은 기간 시장 지수: ").append(percent(s.getMarketIndexChangePercentage()))
                .append(" / BTC: ").append(percent(s.getBtcChangePercentage())).append("\n");
        p.append("최대 낙폭: ").append(s.getMaxDrawdownPercentage() != null ? String.format("-%.1f%%", s.getMaxDrawdownPercentage()) : "정보 없음").append("\n");
        p.append("거래: ").append(s.getTotalTradeCount()).append("회 (매수 ").append(s.getBuyOrderCount())
                .append(", 매도 ").append(s.getSellOrderCount()).append(", 시즌 종료 시 보유 코인 일괄 청산 포함)")
                .append(", 하루 평균 ").append(String.format("%.1f", s.getAvgTradesPerDay())).append("회")
                .append(", 평균 거래 금액 ").append(won(s.getAvgTradeAmount())).append("\n");
        int coinCount = s.getCoinTradeCounts() != null ? s.getCoinTradeCounts().size() : 0;
        p.append("거래한 코인: ").append(coinCount).append("종\n");
        if (s.getCoinRealizedProfitLoss() != null && !s.getCoinRealizedProfitLoss().isEmpty()) {
            p.append("코인별 손익 (매도 금액 - 매수 금액):\n");
            s.getCoinRealizedProfitLoss().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> {
                        Integer trades = s.getCoinTradeCounts() != null ? s.getCoinTradeCounts().get(e.getKey()) : null;
                        p.append("  - ").append(e.getKey()).append(": ").append(signedWon(e.getValue()));
                        if (trades != null) p.append(", 거래 ").append(trades).append("회");
                        String market = s.getCoinMarketPerformanceSummary() != null ? s.getCoinMarketPerformanceSummary().get(e.getKey()) : null;
                        if (market != null) p.append(", 시즌 시세 ").append(market);
                        p.append("\n");
                    });
        }
        if (s.getDailyBalanceTrend() != null && !s.getDailyBalanceTrend().isEmpty()) {
            p.append("일별 총자산 (최근 7일):\n");
            s.getDailyBalanceTrend().stream()
                    .sorted(Comparator.comparing(UserDailyBalance::getSnapshotDate))
                    .skip(Math.max(0, s.getDailyBalanceTrend().size() - 7))
                    .forEach(b -> p.append("  - ").append(b.getSnapshotDate()).append(": ").append(won(b.getTotalValue()))
                            .append(" (현금 ").append(won(b.getCashBalance())).append(")\n"));
        }
        p.append("점수: ").append(score.total()).append("점 / 100\n");
        score.parts().forEach(part -> p.append("  - ").append(part.label()).append(" ").append(part.points()).append("/").append(part.max())
                .append(" (").append(part.basis()).append(")\n"));
        p.append("</season>\n");
        return p.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String rank(InvestmentSummaryDto s) {
        if (s.getFinalRank() == null) return "랭킹 없음";
        if (s.getParticipantCount() == null || s.getParticipantCount() <= 0) return s.getFinalRank() + "위";
        int topPercent = (int) Math.max(1, Math.ceil(s.getFinalRank() * 100.0 / s.getParticipantCount()));
        return String.format("%d위 / %d명 (상위 %d%%)", s.getFinalRank(), s.getParticipantCount(), topPercent);
    }

    private static String percent(Float value) {
        return value == null ? "정보 없음" : String.format("%+.2f%%", value);
    }

    private static String won(Long value) {
        return value == null ? "정보 없음" : String.format("%,d원", value);
    }

    private static String signedWon(Long value) {
        return value == null ? "정보 없음" : String.format("%+,d원", value);
    }
}
