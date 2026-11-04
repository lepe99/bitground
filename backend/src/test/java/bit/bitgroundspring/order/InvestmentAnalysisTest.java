package bit.bitgroundspring.order;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.CoinPriceHistoryRepository;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserRankingRepository;
import bit.bitgroundspring.service.InvestmentAnalysisService;
import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 조언 입력용 투자 요약: 거래한 코인의 시즌 시작일 시가 → 종료일 종가
 */
class InvestmentAnalysisTest extends IntegrationTest {

    @Autowired
    InvestmentAnalysisService investmentAnalysisService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    CoinPriceHistoryRepository coinPriceHistoryRepository;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    UserRankingRepository userRankingRepository;
    @Autowired
    SeasonRepository seasonRepository;

    @Test
    @DisplayName("시즌 시작일의 가장 이른 시가와 종료일의 가장 늦은 종가로 코인 시세 변동을 요약한다")
    void coinMarketPerformance() {
        Season season = fixtures.activeSeason();
        Coin btc = fixtures.coin("KRW-BTC");
        User user = fixtures.user(1_000_000);
        orderRepository.save(Order.builder().user(user).coin(btc).season(season)
                .orderType(OrderType.BUY).amount(new BigDecimal("0.01")).tradePrice(new BigDecimal("100")).status(Status.COMPLETED).build());

        LocalDate start = season.getStartAt();
        LocalDate end = season.getEndAt();
        price(btc, start, 3, 100f, 0f);
        price(btc, start, 1, 90f, 0f);   // 시작일 가장 이른 시간 → 시가 90
        price(btc, end, 5, 0f, 120f);
        price(btc, end, 10, 0f, 150f);   // 종료일 가장 늦은 시간 → 종가 150
        price(btc, start.plusDays(1), 0, 1f, 1f); // 시작·종료일이 아닌 날은 무시

        InvestmentSummaryDto summary = transactionTemplate.execute(s ->
                investmentAnalysisService.getUserInvestmentSummaryForSeason(user.getId(), season.getId()));

        assertThat(summary.getCoinMarketPerformanceSummary())
                .containsEntry("KRW-BTC", "초기 90.00원 -> 최종 150.00원 (변동률: 66.67%)");
    }

    @Test
    @DisplayName("완료 시즌: 최종 자산은 랭킹 값, 손익은 청산 포함 체결 내역, 기준 자금은 그 차이(스플릿 지급 포함)로 계산한다")
    void completedSeasonProfit() {
        Season season = seasonRepository.save(Season.builder()
                .name("지난 시즌").startAt(LocalDate.now().minusDays(15)).endAt(LocalDate.now().minusDays(1))
                .status(Status.COMPLETED).build());
        Coin btc = fixtures.coin("KRW-BTC");
        Coin xrp = fixtures.coin("KRW-XRP");
        User user = fixtures.user(0);
        User other = fixtures.user(0);

        order(user, btc, season, OrderType.BUY, "0.1", "50000000");   // -5,000,000
        order(user, btc, season, OrderType.SELL, "0.1", "55000000");  // +5,500,000 → BTC +500,000
        order(user, xrp, season, OrderType.BUY, "1000", "1000");      // -1,000,000
        order(user, xrp, season, OrderType.SELL, "1000", "900");      // 시즌 종료 청산 +900,000 → XRP -100,000
        // 시작 지급 1,000만 + 스플릿 1,000만 + 손익 40만
        userRankingRepository.save(UserRanking.builder().user(user).season(season).ranks(2).totalValue(20_400_000L).build());
        userRankingRepository.save(UserRanking.builder().user(other).season(season).ranks(1).totalValue(25_000_000L).build());

        InvestmentSummaryDto summary = investmentAnalysisService.getUserInvestmentSummaryForSeason(user.getId(), season.getId());

        assertThat(summary.getFinalTotalValue()).isEqualTo(20_400_000L);
        assertThat(summary.getTotalProfitLossAmount()).isEqualTo(400_000L);
        assertThat(summary.getInitialCashBalance()).isEqualTo(20_000_000L);
        assertThat(summary.getTotalProfitLossPercentage()).isEqualTo(2.0f);
        assertThat(summary.getParticipantCount()).isEqualTo(2);
        assertThat(summary.getCoinRealizedProfitLoss()).containsEntry("KRW-BTC", 500_000L).containsEntry("KRW-XRP", -100_000L);
        assertThat(summary.getAvgTradeAmount()).isEqualTo(3_100_000L);
        assertThat(summary.getHighestProfitCoinSymbol()).isEqualTo("KRW-BTC");
        assertThat(summary.getLowestProfitCoinSymbol()).isEqualTo("KRW-XRP");
    }

    @Test
    @DisplayName("진행 중인 시즌은 보유 코인이 아직 청산되지 않았으므로 기준 자금·총 손익을 비워 둔다")
    void activeSeasonHasNoProfit() {
        Season season = fixtures.activeSeason();
        Coin btc = fixtures.coin("KRW-BTC");
        User user = fixtures.user(5_000_000);
        order(user, btc, season, OrderType.BUY, "0.1", "50000000");
        userRankingRepository.save(UserRanking.builder().user(user).season(season).ranks(1).totalValue(10_200_000L).build());

        InvestmentSummaryDto summary = investmentAnalysisService.getUserInvestmentSummaryForSeason(user.getId(), season.getId());

        assertThat(summary.getFinalTotalValue()).isEqualTo(10_200_000L);
        assertThat(summary.getInitialCashBalance()).isNull();
        assertThat(summary.getTotalProfitLossAmount()).isNull();
        assertThat(summary.getTotalProfitLossPercentage()).isNull();
    }

    private void order(User user, Coin coin, Season season, OrderType type, String amount, String price) {
        orderRepository.save(Order.builder().user(user).coin(coin).season(season)
                .orderType(type).amount(new BigDecimal(amount)).tradePrice(new BigDecimal(price)).status(Status.COMPLETED).build());
    }

    private void price(Coin coin, LocalDate date, int hour, float open, float close) {
        coinPriceHistoryRepository.save(CoinPriceHistory.builder()
                .coin(coin).date(date).hour(hour)
                .openPrice(open).closePrice(close).highPrice(Math.max(open, close)).lowPrice(Math.min(open, close))
                .volume(0f)
                .build());
    }
}
