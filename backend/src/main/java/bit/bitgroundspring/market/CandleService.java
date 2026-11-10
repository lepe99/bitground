package bit.bitgroundspring.market;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.config.CacheConfig;
import bit.bitgroundspring.market.dto.CoinMarket;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 화면의 24시간 추이 그래프 (1시간봉 종가). 브라우저가 업비트를 직접 부르면 Origin 제한(약 10초에 1회)에 걸리므로
 * 서버가 대신 받아 종목별로 몇 분 캐시한다. 접속자가 늘어도 업비트 호출 수는 종목 수만큼으로 일정하다
 */
@Service
@RequiredArgsConstructor
public class CandleService {

    static final int HOURS = 24;

    private final CoinService coinService;
    private final UpbitClient upbitClient;

    /** 원화 마켓에 있는 종목만 받는다 (임의의 값으로 캐시 키·외부 호출이 늘어나지 않게). 조회 실패는 캐시하지 않는다 */
    @Cacheable(cacheNames = CacheConfig.HOURLY_CLOSES, key = "#symbol")
    public List<Double> hourlyCloses(String symbol) {
        boolean listed = coinService.getMarkets().stream().map(CoinMarket::symbol).anyMatch(symbol::equals);
        if (!listed) {
            throw new BusinessException(ErrorCode.COIN_NOT_FOUND);
        }
        return upbitClient.hourlyCloses(symbol, HOURS);
    }
}
