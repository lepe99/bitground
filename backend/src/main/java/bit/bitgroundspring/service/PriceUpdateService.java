package bit.bitgroundspring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Upbit 실시간 시세를 받아 체결 조건을 만족한 예약 주문을 체결 큐로 넘긴다.
 * 시장가 주문은 주문마다 Upbit REST로 현재가를 다시 조회하므로 시세를 따로 저장하지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceUpdateService {

    private final ReservedOrderBook orderBook;

    @Async("priceUpdateTaskExecutor")
    public void updatePrice(String symbol, double currentPrice) {
        try {
            long claimed = orderBook.claimMatches(symbol, currentPrice);
            if (claimed > 0) {
                log.debug("{} reserved orders matched for {} at {}", claimed, symbol, currentPrice);
            }
        } catch (RuntimeException e) {
            log.error("Error matching reserved orders for symbol: {}", symbol, e);
        }
    }
}
