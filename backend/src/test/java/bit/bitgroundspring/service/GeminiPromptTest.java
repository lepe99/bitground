package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.dto.SeasonReviewContent;
import bit.bitgroundspring.dto.SeasonReviewContent.NextAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 시즌 리뷰: 점수 규칙, 프롬프트에 넣는 숫자 표기, 응답 검증
 */
class GeminiPromptTest {

    private static InvestmentSummaryDto.InvestmentSummaryDtoBuilder base() {
        return InvestmentSummaryDto.builder()
                .seasonName("시즌 3").seasonStartAt(LocalDate.of(2026, 10, 16)).seasonEndAt(LocalDate.of(2026, 10, 31))
                .initialCashBalance(20_000_000L).finalTotalValue(20_400_000L)
                .totalProfitLossAmount(400_000L).totalProfitLossPercentage(2.0f)
                .finalRank(2).participantCount(15)
                .totalTradeCount(4).buyOrderCount(2).sellOrderCount(2).avgTradeAmount(3_100_000L)
                .coinRealizedProfitLoss(Map.of("KRW-BTC", 500_000L, "KRW-XRP", -100_000L))
                .coinTradeCounts(Map.of("KRW-BTC", 2, "KRW-XRP", 2))
                .avgTradesPerDay(0.25).focusedOnFewCoins(true);
    }

    @Test
    @DisplayName("점수: 순위 45 + 시장 대비 20 + 낙폭 20 + 매매 절제 15, 규칙대로 계산한다")
    void score() {
        // 2위/15명 → 45×(1−1/14)=41.8→42 / +2% vs 시장 −4% → +6%p → 20×0.8=16 / 낙폭 5% → 20×0.8=16 / 하루 0.25회 → 15
        InvestmentSummaryDto s = base().marketIndexChangePercentage(-4.0f).maxDrawdownPercentage(5.0f).build();

        SeasonReviewScorer.Score score = SeasonReviewScorer.score(s);

        assertThat(score.parts()).extracting(SeasonReviewScorer.Part::points).containsExactly(42, 16, 16, 15);
        assertThat(score.total()).isEqualTo(89);
        assertThat(score.parts().get(1).basis()).contains("시장 지수 -4.00%").contains("+6.00%p");
    }

    @Test
    @DisplayName("점수: 비교 기준·낙폭 기록이 없으면 각각 수익률 자체·절반 점수로, 과매매는 감점")
    void scoreWithoutBenchmark() {
        InvestmentSummaryDto s = base().finalRank(15).avgTradesPerDay(12.5).build();

        SeasonReviewScorer.Score score = SeasonReviewScorer.score(s);

        // 꼴찌 0 / 수익률 +2% → 20×0.6=12 / 낙폭 기록 없음 10 / 12.5회 → 15×0.5=7.5→8
        assertThat(score.parts()).extracting(SeasonReviewScorer.Part::points).containsExactly(0, 12, 10, 8);
        assertThat(score.total()).isEqualTo(30);
    }

    @Test
    @DisplayName("프롬프트: 금액은 쉼표 원 단위, 손익률은 부호와 % 하나, 순위·점수 구성까지 넣는다")
    void formatsNumbers() {
        InvestmentSummaryDto s = base().marketIndexChangePercentage(-4.0f).maxDrawdownPercentage(5.0f).build();

        String prompt = GeminiService.buildPrompt(s, SeasonReviewScorer.score(s));

        assertThat(prompt)
                .startsWith("<season>\n").endsWith("</season>\n")
                .contains("시즌 중 지급받은 자금: 20,000,000원")
                .contains("총 손익: +400,000원 / 수익률: +2.00%\n")
                .contains("순위: 2위 / 15명 (상위 14%)")
                .contains("같은 기간 시장 지수: -4.00% / BTC: 정보 없음")
                .contains("최대 낙폭: -5.0%")
                .contains("  - KRW-BTC: +500,000원, 거래 2회")
                .contains("  - KRW-XRP: -100,000원, 거래 2회")
                .contains("점수: 89점 / 100")
                .contains("  - 순위 42/45 (2위 / 15명)")
                .doesNotContain("%%");
    }

    @Test
    @DisplayName("최대 낙폭: 직전 최고점 대비 가장 크게 떨어진 비율, 스플릿 지급으로 늘어난 뒤의 하락도 잡는다")
    void maxDrawdown() {
        List<bit.bitgroundspring.entity.UserDailyBalance> balances = List.of(
                balance(10_000_000L), balance(12_000_000L), balance(9_000_000L), // 12M → 9M: 25%
                balance(19_000_000L), balance(17_100_000L));                      // 스플릿 후 19M → 17.1M: 10%

        assertThat(InvestmentAnalysisService.maxDrawdownPercent(balances)).isEqualTo(25.0f);
        assertThat(InvestmentAnalysisService.maxDrawdownPercent(List.of(balance(1L)))).isNull();
    }

    private static bit.bitgroundspring.entity.UserDailyBalance balance(long total) {
        return bit.bitgroundspring.entity.UserDailyBalance.builder().totalValue(total).cashBalance(0L).build();
    }

    @Test
    @DisplayName("응답 검증: 빈 항목이나 투자 권유 표현이 있으면 거절한다")
    void validate() {
        SeasonReviewContent ok = new SeasonReviewContent("시장보다 6%p 앞선 시즌",
                List.of("손실 코인을 빨리 정리했습니다."), List.of("두 종목에만 거래가 몰렸습니다."),
                List.of(new NextAction("손절 기준을 미리 정해 두세요.", "낙폭을 줄일 수 있습니다.")), "순위 점수가 높았습니다.", null);
        assertThat(GeminiService.validate(ok)).isNull();

        SeasonReviewContent advice = new SeasonReviewContent("좋은 시즌",
                List.of("잘했습니다."), List.of("아쉬웠습니다."),
                List.of(new NextAction("KRW-BTC를 매수하세요.", "오를 것입니다.")), "이유", null);
        assertThat(GeminiService.validate(advice)).startsWith("투자 권유 표현");

        SeasonReviewContent empty = new SeasonReviewContent("총평", List.of(), List.of("x"), List.of(), "이유", null);
        assertThat(GeminiService.validate(empty)).isNotNull();
    }
}
