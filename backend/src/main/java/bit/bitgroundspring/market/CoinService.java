package bit.bitgroundspring.market;

import bit.bitgroundspring.market.dto.CoinResponse;
import bit.bitgroundspring.market.dto.CoinSymbolDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 코인 목록 조회 (상장 폐지 코인 제외, 정렬·LIMIT은 DB에서)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoinService {

    private final CoinRepository coinRepository;

    public List<String> getActiveSymbols() {
        return coinRepository.findByIsDeletedFalse().stream().map(Coin::getSymbol).toList();
    }

    public List<CoinResponse> getTop5ByTradePrice() {
        return toResponses(coinRepository.findTop5ByIsDeletedFalseOrderByTradePrice24hDesc());
    }

    public List<CoinResponse> getTop5Gainers() {
        return toResponses(coinRepository.findTop5ByIsDeletedFalseAndChangeRateNotNullOrderByChangeRateDesc());
    }

    public List<CoinResponse> getTop5Losers() {
        return toResponses(coinRepository.findTop5ByIsDeletedFalseAndChangeRateNotNullOrderByChangeRateAsc());
    }

    /** 업비트 투자유의 종목 */
    public List<CoinResponse> getWarningCoins() {
        return toResponses(coinRepository.findByIsDeletedFalseAndIsWarningTrue());
    }

    /** 업비트 주의 종목 */
    public List<CoinResponse> getCautionCoins() {
        return toResponses(coinRepository.findByIsDeletedFalseAndIsCautionTrue());
    }

    public List<CoinSymbolDto> getSymbols() {
        return coinRepository.findAllSymbols();
    }

    private static List<CoinResponse> toResponses(List<Coin> coins) {
        return coins.stream().map(CoinResponse::from).toList();
    }
}
