// src/main/java/bit/bitgroundspring/controller/CoinController.java

package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.CoinSymbolDto;
import bit.bitgroundspring.dto.AiInsightSymbolDto; // AiInsightSymbolDto 임포트 유지
import bit.bitgroundspring.entity.AiInsight;
import bit.bitgroundspring.entity.Coin; // Coin 엔티티 임포트 유지 (coinRepository 사용 위함)
import bit.bitgroundspring.repository.CoinRepository;
import bit.bitgroundspring.service.GeminiService;
import bit.bitgroundspring.repository.AiInsightRepository; // AiInsightRepository 임포트 유지
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate; // LocalDate 임포트 유지
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList; // ArrayList 임포트 유지
import java.util.HashSet; // HashSet 임포트 유지


@RestController
@RequiredArgsConstructor
@Slf4j
public class CoinController {

    private final CoinRepository coinRepository;
    private final GeminiService geminiService;
    private final AiInsightRepository aiInsightRepository; // AiInsightRepository 주입

    // 모든 코인 정보 조회 엔드포인트
    // GET 요청: http://localhost:8090/api/coins
    @GetMapping("/coins")
    public List<Coin> getAllCoins() {
        log.info("Request to get all coins.");
        return coinRepository.findAll();
    }

    // 거래대금 상위 5개 코인 조회 엔드포인트
    // GET 요청: http://localhost:8090/api/coins/high-trade-price
    @GetMapping("/coins/high-trade-price")
    public List<Coin> getTop5HighTradePriceCoins() {
        log.info("Request to get top 5 coins by high trade price.");
        return coinRepository.findAll().stream()
                .sorted(Comparator.comparing(Coin::getTradePrice24h).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    // 상승폭 큰 종목 상위 5개 코인 조회 엔드포인트
    // GET 요청: http://localhost:8090/api/coins/price-increase
    @GetMapping("/coins/price-increase")
    public List<Coin> getTop5PriceIncreaseCoins() {
        log.info("Request to get top 5 coins by price increase.");
        return coinRepository.findAll().stream()
                .filter(coin -> coin.getChangeRate() != null)
                .sorted(Comparator.comparing(Coin::getChangeRate).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    // 하락폭 큰 종목 상위 5개 코인 조회 엔드포인트
    // GET 요청: http://localhost:8090/api/coins/price-decrease
    @GetMapping("/coins/price-decrease")
    public List<Coin> getTop5PriceDecreaseCoins() {
        log.info("Request to get top 5 coins by price decrease.");
        return coinRepository.findAll().stream()
                .filter(coin -> coin.getChangeRate() != null)
                .sorted(Comparator.comparing(Coin::getChangeRate))
                .limit(5)
                .collect(Collectors.toList());
    }

    // 거래유의 종목 조회 엔드포인트 (Upbit의 'warning'에 해당)
    // GET 요청: http://localhost:8090/api/coins/caution
    @GetMapping("/coins/caution")
    public List<Coin> getCautionCoins() {
        log.info("Request to get caution coins (isWarning).");
        return coinRepository.findAll().stream()
                .filter(Coin::getIsWarning)
                .collect(Collectors.toList());
    }

    // 투자주의 종목 조회 엔드포인트 (Upbit의 'caution'에 해당)
    // GET 요청: http://localhost:8090/api/coins/alert
    @GetMapping("/coins/alert")
    public List<Coin> getAlertCoins() {
        log.info("Request to get alert coins (isCaution).");
        return coinRepository.findAll().stream()
                .filter(Coin::getIsCaution)
                .collect(Collectors.toList());
    }

    // 모든 코인 심볼 조회 엔드포인트 (DTO 사용)
    // GET 요청: http://localhost:8090/api/coins/symbols
    @GetMapping("/coins/symbols")
    public List<CoinSymbolDto> getCoinSymbols() {
        log.info("Request to get all coin symbols.");
        return coinRepository.findAll().stream()
                .map(coin -> new CoinSymbolDto(coin.getSymbol(), coin.getKoreanName()))
                .collect(Collectors.toList());
    }

    /**
     * 특정 코인 또는 전체 시장에 대한 AI 분석 결과를 조회하는 엔드포인트입니다.
     * Go 서비스에 의해 DB에 미리 생성되어 저장된 데이터를 조회합니다.
     * GET 요청: http://localhost:8090/api/coins/{symbol}/insight
     *
     * @param symbol 조회할 코인 또는 시장의 심볼 (예: "KRW-BTC" 또는 "MARKET_OVERALL")
     * @return AI 분석 데이터를 담은 AiInsight 객체
     */
    @GetMapping("/coins/{symbol}/insight")
    public ResponseEntity<AiInsight> getCoinInsight(@PathVariable String symbol) {
        log.info("Request to get AI insight for symbol: {}", symbol);

        // Go 서비스가 이미 AI 인사이트를 생성하여 DB에 저장했으므로,
        // Spring 서비스는 단순히 조회만 수행합니다.
        // AiInsightRepository의 findBySymbolAndDate를 사용합니다.
        Optional<AiInsight> aiInsightOptional = aiInsightRepository.findBySymbolAndDate(symbol, LocalDate.now());

        if (aiInsightOptional.isPresent()) {
            AiInsight aiInsight = aiInsightOptional.get();
            log.info("Successfully retrieved AI insight for symbol: {}. Insight ID: {}", symbol, aiInsight.getId());
            return ResponseEntity.ok(aiInsight);
        } else {
            log.warn("AI insight not found for symbol: {} today. Returning NOT_FOUND.", symbol);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    /**
     * 전체 암호화폐 시장에 대한 AI 분석 결과를 조회하는 엔드포인트입니다.
     * 이 엔드포인트는 Go 서비스가 'MARKET_OVERALL' 심볼로 저장한 데이터를 가져오는 데 사용됩니다.
     * GET 요청: http://localhost:8090/api/ai-insights/overall-market
     *
     * @return 전체 시장 AI 분석 데이터를 담은 AiInsight 객체
     */
    @GetMapping("/ai-insights/overall-market")
    public ResponseEntity<AiInsight> getOverallMarketInsight() {
        log.info("Request to get overall market AI insight.");

        // 'MARKET_OVERALL' 심볼에 대한 오늘자 인사이트를 AiInsightRepository에서 직접 조회
        Optional<AiInsight> aiInsightOptional = aiInsightRepository.findByAiInsightSymbolAndDate("MARKET_OVERALL", LocalDate.now());

        if (aiInsightOptional.isPresent()) {
            AiInsight aiInsight = aiInsightOptional.get();
            log.info("Successfully retrieved overall market AI insight for today. Insight ID: {}", aiInsight.getId());
            return ResponseEntity.ok(aiInsight);
        } else {
            log.warn("Overall market AI insight not found for today. Returning NOT_FOUND.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    /**
     * 오늘 날짜에 대한 AI 분석 드롭다운에 표시할 코인 심볼 목록을 조회합니다.
     * 프론트엔드의 CoinTrends.jsx에서 호출됩니다.
     * GET /ai-insights/today-symbols
     *
     * @return AiInsightSymbolDto (심볼, 한글 이름) 목록
     */
    @GetMapping("/ai-insights/today-symbols")
    public ResponseEntity<List<AiInsightSymbolDto>> getTodayInsightSymbolsForDropdown() {
        log.info("Fetching today's AI insight symbols for dropdown from CoinController.");

        LocalDate today = LocalDate.now(); // 오늘 날짜

        // AiInsightRepository에 추가된 findSymbolsAndKoreanNamesByDate 메서드를 사용합니다.
        List<AiInsightSymbolDto> allInsightsForToday = aiInsightRepository.findTodaySymbolsAndKoreanNames();

        // MARKET_OVERALL 심볼은 Coin 엔티티와 조인되지 않으므로, 별도로 조회하여 추가합니다.
        Optional<AiInsight> marketOverallInsightOptional = aiInsightRepository.findByAiInsightSymbolAndDate("MARKET_OVERALL", today);
        if (marketOverallInsightOptional.isPresent()) {
            // MARKET_OVERALL은 항상 "전체 시장"으로 표시되도록 명시적으로 추가
            AiInsightSymbolDto marketOverallDto = new AiInsightSymbolDto("MARKET_OVERALL", "전체 시장");
            // 기존 목록의 맨 앞에 삽입
            ((ArrayList<AiInsightSymbolDto>) allInsightsForToday).add(0, marketOverallDto);
        }


        // 고객님 요청에 따른 AI 분석 드롭다운의 고정 표시 순서 및 목록 재조합
        final String MARKET_OVERALL_SYMBOL = "MARKET_OVERALL";
        final String KRW_BTC_SYMBOL = "KRW-BTC";
        final String KRW_ETH_SYMBOL = "KRW-ETH";
        final String KRW_SOL_SYMBOL = "KRW-SOL";
        final String KRW_XRP_SYMBOL = "KRW-XRP";

        List<String> preferredFixedSymbolsOrder = List.of(
                MARKET_OVERALL_SYMBOL, KRW_BTC_SYMBOL, KRW_ETH_SYMBOL, KRW_SOL_SYMBOL, KRW_XRP_SYMBOL
        );

        List<AiInsightSymbolDto> finalDropdownSymbols = new ArrayList<>();
        java.util.Set<String> addedSymbols = new HashSet<>();

        // 1. 고정적으로 표시되어야 하는 심볼을 우선적으로 추가합니다.
        for (String symbol : preferredFixedSymbolsOrder) {
            Optional<AiInsightSymbolDto> foundInsightDto = allInsightsForToday.stream()
                    .filter(dto -> dto.getSymbol().equals(symbol))
                    .findFirst();
            if (foundInsightDto.isPresent()) {
                finalDropdownSymbols.add(foundInsightDto.get());
                addedSymbols.add(symbol);
            }
        }

        // 2. 고정 목록에 없는, AI가 추천한 나머지 코인들을 추가합니다.
        // 최대 5개까지만 추가하도록 제한하며, 한글 이름 기준으로 정렬합니다.
        List<AiInsightSymbolDto> recommendedCoins = allInsightsForToday.stream()
                .filter(dto -> !addedSymbols.contains(dto.getSymbol()))
                .sorted((d1, d2) -> d1.getKoreanName().compareTo(d2.getKoreanName()))
                .collect(Collectors.toList());

        for (int i = 0; i < Math.min(5, recommendedCoins.size()); i++) {
            finalDropdownSymbols.add(recommendedCoins.get(i));
        }

        if (finalDropdownSymbols.isEmpty()) {
            log.warn("No AI insight symbols found for today.");
            return ResponseEntity.noContent().build(); // 204 No Content
        }
        return ResponseEntity.ok(finalDropdownSymbols);
    }
}
