package bit.bitgroundspring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Upbit 실시간 시세를 받아 매매에 쓸 현재가로 남기고, 체결 조건을 만족한 예약 주문을 체결 큐로 넘긴다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceUpdateService {

    private final ReservedOrderBook orderBook;
    private final MarketPriceService marketPriceService;

    @Async("priceUpdateTaskExecutor")
    public void updatePrice(String symbol, BigDecimal price, long tradeTimestampMs) {
        try {
            marketPriceService.record(symbol, price, tradeTimestampMs);
        } catch (RuntimeException e) {
            log.warn("Failed to record latest price for {}: {}", symbol, e.getMessage());
        }
        double currentPrice = price.doubleValue();
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
