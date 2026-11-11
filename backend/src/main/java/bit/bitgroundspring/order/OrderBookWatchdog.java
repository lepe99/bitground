package bit.bitgroundspring.order;

import bit.bitgroundspring.event.OutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis가 죽었다 살아나 데이터가 사라져도 예약 주문 대기열을 DB 기준으로 되살린다.
 * <ul>
 *   <li>10초마다: 대기열 표시 키가 없으면(Redis 재시작으로 데이터가 사라짐) 바로 다시 만든다.
 *       예전에는 백엔드가 재시작될 때만 다시 만들어, 그동안 예약 주문이 조용히 체결되지 않았다</li>
 *   <li>그때 이벤트 스트림도 사라졌으므로 최근 10분 이벤트를 아웃박스에서 다시 보낸다 (받는 쪽이 중복을 거름)</li>
 *   <li>5분마다: DB의 PENDING 주문과 대기열을 대조해 빠진 주문은 채우고 끝난 주문은 지운다
 *       (AOF로 1초 안쪽만 잃은 경우처럼 표시 키는 남았지만 일부가 빠진 경우)</li>
 * </ul>
 * 테스트에서는 끄고(order-book.watchdog.enabled=false) 메서드를 직접 부른다
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order-book.watchdog.enabled", havingValue = "true", matchIfMissing = true)
public class OrderBookWatchdog {

    /** Redis 데이터가 사라졌을 때 다시 보낼 이벤트 범위 */
    private static final Duration REPUBLISH_WINDOW = Duration.ofMinutes(10);

    private final ReservedOrderBook orderBook;
    private final TradingWindow tradingWindow;
    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void checkReset() {
        try {
            if (orderBook.rebuildIfReset()) {
                // 이벤트 스트림도 함께 사라졌으므로 처리되기 전에 사라졌을 수 있는 최근 이벤트를 다시 보낸다
                outboxRelay.republishSince(REPUBLISH_WINDOW);
            }
        } catch (RuntimeException e) {
            log.warn("Order book reset check failed (Redis unavailable?): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void reconcile() {
        // 정산 중에는 체결하지 않고 남긴 주문이 있어 대조하면 같은 주문을 계속 되돌리게 된다. 정산 후 다시 만든다
        if (tradingWindow.isClosed()) {
            return;
        }
        try {
            orderBook.reconcile();
        } catch (RuntimeException e) {
            log.warn("Order book reconcile failed (Redis unavailable?): {}", e.getMessage());
        }
    }
}
