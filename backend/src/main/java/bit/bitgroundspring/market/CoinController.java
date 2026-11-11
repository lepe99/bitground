package bit.bitgroundspring.market;

import bit.bitgroundspring.ai.AiInsightService;
import bit.bitgroundspring.ai.dto.AiInsightResponse;
import bit.bitgroundspring.ai.dto.AiInsightSymbolDto;
import bit.bitgroundspring.market.dto.CoinMarket;
import bit.bitgroundspring.market.dto.CoinResponse;
import bit.bitgroundspring.market.dto.CoinSymbolDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;
    private final CandleService candleService;
    private final AiInsightService aiInsightService;

    @GetMapping("/coins/high-trade-price")
    public List<CoinResponse> getTop5HighTradePriceCoins() {
        return coinService.getTop5ByTradePrice();
    }

    @GetMapping("/coins/price-increase")
    public List<CoinResponse> getTop5PriceIncreaseCoins() {
        return coinService.getTop5Gainers();
    }

    @GetMapping("/coins/price-decrease")
    public List<CoinResponse> getTop5PriceDecreaseCoins() {
        return coinService.getTop5Losers();
    }

    @GetMapping("/coins/caution")
    public List<CoinResponse> getCautionCoins() {
        return coinService.getWarningCoins();
    }

    @GetMapping("/coins/alert")
    public List<CoinResponse> getAlertCoins() {
        return coinService.getCautionCoins();
    }

    @GetMapping("/coins/symbols")
    public List<CoinSymbolDto> getCoinSymbols() {
        return coinService.getSymbols();
    }

    /** 원화 마켓 전체 (거래대금 순, 유의·주의 포함, 매시 갱신). 화면의 종목 목록·거래대금 상위 고르기 */
    @GetMapping("/coins/markets")
    public List<CoinMarket> getMarkets() {
        return coinService.getMarkets();
    }

    /** 최근 24시간 1시간봉 종가 (오래된 것부터). 추이 그래프 */
    @GetMapping("/coins/{symbol}/hourly-closes")
    public List<Double> getHourlyCloses(@PathVariable String symbol) {
        return candleService.hourlyCloses(symbol);
    }

    @GetMapping("/coins/{symbol}/insight")
    public AiInsightResponse getCoinInsight(@PathVariable String symbol) {
        return aiInsightService.getTodayInsight(symbol);
    }

    @GetMapping("/ai-insights/today-symbols")
    public List<AiInsightSymbolDto> getTodayInsightSymbolsForDropdown() {
        return aiInsightService.getTodaySymbols();
    }
}
