package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.response.AiInsightSymbolDto;
import bit.bitgroundspring.dto.response.CoinSymbolDto;
import bit.bitgroundspring.dto.response.AiInsightResponse;
import bit.bitgroundspring.dto.response.CoinResponse;
import bit.bitgroundspring.service.AiInsightService;
import bit.bitgroundspring.service.CoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;
    private final AiInsightService aiInsightService;

    @GetMapping("/coins")
    public List<CoinResponse> getAllCoins() {
        return coinService.getAllCoins();
    }

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

    @GetMapping("/coins/{symbol}/insight")
    public AiInsightResponse getCoinInsight(@PathVariable String symbol) {
        return aiInsightService.getTodayInsight(symbol);
    }

    @GetMapping("/ai-insights/overall-market")
    public AiInsightResponse getOverallMarketInsight() {
        return aiInsightService.getTodayMarketOverall();
    }

    @GetMapping("/ai-insights/today-symbols")
    public List<AiInsightSymbolDto> getTodayInsightSymbolsForDropdown() {
        return aiInsightService.getTodaySymbols();
    }
}
