package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.AiInsightSymbolDto;
import bit.bitgroundspring.dto.CoinSymbolDto;
import bit.bitgroundspring.dto.response.AiInsightResponse;
import bit.bitgroundspring.dto.response.CoinResponse;
import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.repository.AiInsightRepository;
import bit.bitgroundspring.repository.CoinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
public class CoinController {

    private static final String MARKET_OVERALL = "MARKET_OVERALL";
    // 드롭다운 상단에 고정으로 보여줄 심볼 순서
    private static final List<String> PREFERRED_SYMBOLS = List.of(MARKET_OVERALL, "KRW-BTC", "KRW-ETH", "KRW-SOL", "KRW-XRP");

    private final CoinRepository coinRepository;
    private final AiInsightRepository aiInsightRepository;

    @GetMapping("/coins")
    public List<CoinResponse> getAllCoins() {
        return coins(coinRepository.findAll().stream());
    }

    @GetMapping("/coins/high-trade-price")
    public List<CoinResponse> getTop5HighTradePriceCoins() {
        return coins(coinRepository.findAll().stream()
                .sorted(Comparator.comparing(Coin::getTradePrice24h).reversed())
                .limit(5));
    }

    @GetMapping("/coins/price-increase")
    public List<CoinResponse> getTop5PriceIncreaseCoins() {
        return coins(coinRepository.findAll().stream()
                .filter(coin -> coin.getChangeRate() != null)
                .sorted(Comparator.comparing(Coin::getChangeRate).reversed())
                .limit(5));
    }

    @GetMapping("/coins/price-decrease")
    public List<CoinResponse> getTop5PriceDecreaseCoins() {
        return coins(coinRepository.findAll().stream()
                .filter(coin -> coin.getChangeRate() != null)
                .sorted(Comparator.comparing(Coin::getChangeRate))
                .limit(5));
    }

    @GetMapping("/coins/caution")
    public List<CoinResponse> getCautionCoins() {
        return coins(coinRepository.findAll().stream().filter(Coin::getIsWarning));
    }

    @GetMapping("/coins/alert")
    public List<CoinResponse> getAlertCoins() {
        return coins(coinRepository.findAll().stream().filter(Coin::getIsCaution));
    }

    @GetMapping("/coins/symbols")
    public List<CoinSymbolDto> getCoinSymbols() {
        return coinRepository.findAll().stream()
                .map(coin -> new CoinSymbolDto(coin.getSymbol(), coin.getKoreanName()))
                .toList();
    }

    @GetMapping("/coins/{symbol}/insight")
    public AiInsightResponse getCoinInsight(@PathVariable String symbol) {
        return aiInsightRepository.findBySymbolAndDate(symbol, LocalDate.now())
                .map(AiInsightResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INSIGHT_NOT_FOUND));
    }

    @GetMapping("/ai-insights/overall-market")
    public AiInsightResponse getOverallMarketInsight() {
        return aiInsightRepository.findByAiInsightSymbolAndDate(MARKET_OVERALL, LocalDate.now())
                .map(AiInsightResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INSIGHT_NOT_FOUND));
    }

    /**
     * 오늘 인사이트가 있는 심볼 목록 (드롭다운용). 없으면 빈 배열.
     * 고정 심볼(전체 시장, BTC, ETH, SOL, XRP)을 먼저, 나머지는 한글명 순으로 최대 5개.
     */
    @GetMapping("/ai-insights/today-symbols")
    public List<AiInsightSymbolDto> getTodayInsightSymbolsForDropdown() {
        List<AiInsightSymbolDto> todayInsights = new ArrayList<>(aiInsightRepository.findTodaySymbolsAndKoreanNames());
        Optional<?> marketOverall = aiInsightRepository.findByAiInsightSymbolAndDate(MARKET_OVERALL, LocalDate.now());
        if (marketOverall.isPresent()) {
            todayInsights.add(0, new AiInsightSymbolDto(MARKET_OVERALL, "전체 시장"));
        }

        List<AiInsightSymbolDto> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String symbol : PREFERRED_SYMBOLS) {
            todayInsights.stream().filter(dto -> dto.getSymbol().equals(symbol)).findFirst().ifPresent(dto -> {
                result.add(dto);
                added.add(symbol);
            });
        }
        Predicate<AiInsightSymbolDto> notAdded = dto -> !added.contains(dto.getSymbol());
        todayInsights.stream()
                .filter(notAdded)
                .sorted(Comparator.comparing(AiInsightSymbolDto::getKoreanName))
                .limit(5)
                .forEach(result::add);
        return result;
    }

    private List<CoinResponse> coins(Stream<Coin> stream) {
        return stream.map(CoinResponse::from).toList();
    }
}
