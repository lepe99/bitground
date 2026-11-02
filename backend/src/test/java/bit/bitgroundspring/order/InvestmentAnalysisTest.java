package bit.bitgroundspring.order;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.CoinPriceHistoryRepository;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.service.InvestmentAnalysisService;
import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test
    @DisplayName("시즌 시작일의 가장 이른 시가와 종료일의 가장 늦은 종가로 코인 시세 변동을 요약한다")
    void coinMarketPerformance() {
        Season season = fixtures.activeSeason();
        Coin btc = fixtures.coin("KRW-BTC");
        User user = fixtures.user(1_000_000);
        orderRepository.save(Order.builder().user(user).coin(btc).season(season)
                .orderType(OrderType.BUY).amount(0.01).tradePrice(100.0).status(Status.COMPLETED).build());

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

    private void price(Coin coin, LocalDate date, int hour, float open, float close) {
        coinPriceHistoryRepository.save(CoinPriceHistory.builder()
                .coin(coin).date(date).hour(hour)
                .openPrice(open).closePrice(close).highPrice(Math.max(open, close)).lowPrice(Math.min(open, close))
                .volume(0f)
                .build());
    }
}
