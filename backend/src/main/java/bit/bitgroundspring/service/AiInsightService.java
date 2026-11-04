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
import java.util.List;

/**
 * 스케줄러가 매일 만드는 AI 시장 인사이트 조회
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiInsightService {

    private static final String MARKET_OVERALL = "MARKET_OVERALL";
    private static final String BITCOIN = "KRW-BTC";

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
     * 오늘 인사이트가 있는 심볼 목록 (분석 종목 버튼용). 없으면 빈 배열.
     * 전체 시장과 비트코인을 앞에 두고, 나머지는 스케줄러가 고른 순서(거래대금 상위 2, 상승률 1위, 하락률 1위) 그대로.
     */
    public List<AiInsightSymbolDto> getTodaySymbols() {
        LocalDate today = LocalDate.now();
        List<AiInsightSymbolDto> result = new ArrayList<>();
        if (aiInsightRepository.findByAiInsightSymbolAndDate(MARKET_OVERALL, today).isPresent()) {
            result.add(new AiInsightSymbolDto(MARKET_OVERALL, "전체 시장", null));
        }
        List<AiInsightSymbolDto> coins = aiInsightRepository.findSymbolsByDate(today);
        coins.stream().filter(dto -> dto.getSymbol().equals(BITCOIN)).forEach(result::add);
        coins.stream().filter(dto -> !dto.getSymbol().equals(BITCOIN)).forEach(result::add);
        return result;
    }
}
