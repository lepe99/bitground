package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 시즌 리뷰 프롬프트에 들어가는 숫자 표기
 */
class GeminiPromptTest {

    private final GeminiService geminiService = new GeminiService("test-model", "test-key", new ObjectMapper());

    @Test
    @DisplayName("금액은 쉼표 원 단위, 손익률은 부호와 % 하나, 순위는 참가자 수와 상위 %로 넣는다")
    void formatsNumbers() {
        InvestmentSummaryDto summary = InvestmentSummaryDto.builder()
                .seasonName("시즌 3").seasonStartAt(LocalDate.of(2026, 10, 16)).seasonEndAt(LocalDate.of(2026, 10, 31))
                .initialCashBalance(20_000_000L).finalTotalValue(20_400_000L)
                .totalProfitLossAmount(400_000L).totalProfitLossPercentage(2.0f)
                .finalRank(2).participantCount(15)
                .totalTradeCount(4).buyOrderCount(2).sellOrderCount(2).avgTradeAmount(3_100_000L)
                .coinRealizedProfitLoss(Map.of("KRW-BTC", 500_000L, "KRW-XRP", -100_000L))
                .highestProfitCoinSymbol("KRW-BTC").highestProfitCoinAmount(500_000L)
                .lowestProfitCoinSymbol("KRW-XRP").lowestProfitCoinAmount(-100_000L)
                .mostTradedCoinSymbol("KRW-BTC").mostTradedCoinTradeVolume(10_500_000L)
                .avgTradesPerDay(0.25).focusedOnFewCoins(true)
                .build();

        String prompt = geminiService.buildPrompt(summary);

        assertThat(prompt)
                .contains("시즌 중 지급받은 자금(시즌 시작·스플릿 지급 합계): 20,000,000원")
                .contains("최종 총 자산: 20,400,000원")
                .contains("총 손익률: +2.00%\n")
                .contains("최종 순위: 2위 / 15명 (상위 14%)")
                .contains("  - KRW-BTC: +500,000원")
                .contains("  - KRW-XRP: -100,000원")
                .doesNotContain("%%");
    }

    @Test
    @DisplayName("진행 중인 시즌처럼 비어 있는 값은 '정보 없음'으로 넣는다")
    void missingValues() {
        InvestmentSummaryDto summary = InvestmentSummaryDto.builder()
                .seasonName("시즌 4").totalTradeCount(0).buyOrderCount(0).sellOrderCount(0)
                .avgTradeAmount(0L).avgTradesPerDay(0.0).focusedOnFewCoins(false)
                .build();

        String prompt = geminiService.buildPrompt(summary);

        assertThat(prompt)
                .contains("최종 총 자산: 정보 없음")
                .contains("총 손익률: 정보 없음")
                .contains("최종 순위: 랭킹 없음");
    }
}
