package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.response.AiInsightSymbolDto;
import bit.bitgroundspring.dto.response.AiInsightResponse;
import bit.bitgroundspring.repository.AiInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 스케줄러가 매일 만드는 AI 시장 인사이트 조회
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiInsightService {

    private static final String MARKET_OVERALL = "MARKET_OVERALL";
    // 드롭다운 상단에 고정으로 보여줄 심볼 순서
    private static final List<String> PREFERRED_SYMBOLS = List.of(MARKET_OVERALL, "KRW-BTC", "KRW-ETH", "KRW-SOL", "KRW-XRP");
    private static final int EXTRA_SYMBOL_LIMIT = 5;

    private final AiInsightRepository aiInsightRepository;

    public AiInsightResponse getTodayInsight(String symbol) {
        return aiInsightRepository.findBySymbolAndDate(symbol, LocalDate.now())
                .map(AiInsightResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INSIGHT_NOT_FOUND));
    }

    public AiInsightResponse getTodayMarketOverall() {
        return aiInsightRepository.findByAiInsightSymbolAndDate(MARKET_OVERALL, LocalDate.now())
                .map(AiInsightResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INSIGHT_NOT_FOUND));
    }

    /**
     * 오늘 인사이트가 있는 심볼 목록 (드롭다운용). 없으면 빈 배열.
     * 고정 심볼(전체 시장, BTC, ETH, SOL, XRP)을 먼저, 나머지는 한글명 순으로 최대 5개.
     */
    public List<AiInsightSymbolDto> getTodaySymbols() {
        List<AiInsightSymbolDto> todayInsights = new ArrayList<>(aiInsightRepository.findTodaySymbolsAndKoreanNames());
        if (aiInsightRepository.findByAiInsightSymbolAndDate(MARKET_OVERALL, LocalDate.now()).isPresent()) {
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
        todayInsights.stream()
                .filter(dto -> !added.contains(dto.getSymbol()))
                .sorted(Comparator.comparing(AiInsightSymbolDto::getKoreanName))
                .limit(EXTRA_SYMBOL_LIMIT)
                .forEach(result::add);
        return result;
    }
}
