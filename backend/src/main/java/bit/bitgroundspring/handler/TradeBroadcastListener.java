package bit.bitgroundspring.handler;

import bit.bitgroundspring.event.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 체결 내역 실시간 방송
 * 체결 트랜잭션이 커밋된 뒤 별도 스레드에서 실행한다.
 * (기존: 발행한 트랜잭션 안에서 동기로 전송해, 사용자 행 락을 쥔 채 느린 클라이언트를 기다렸고 롤백될 체결도 방송됨)
 */
@Component
@RequiredArgsConstructor
public class TradeBroadcastListener {

    private final OrderHistoryWebSocketHandler handler;

    @Async("webSocketTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onTradeExecuted(TradeExecutedEvent event) {
        handler.broadcast(event.trade());
    }
}
