package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.response.Message;
import bit.bitgroundspring.dto.response.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.event.ReserveOrderExecutedEvent;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.util.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 예약 주문 체결 알림
 * 체결이 커밋된 뒤 별도 스레드에서 알림을 저장·전송한다(체결 처리와 사용자 행 락을 붙잡지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final UserRepository userRepository;
    private final UserSseEmitters userSseEmitters;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReserveOrderExecuted(ReserveOrderExecutedEvent event) {
        try {
            long cash = userRepository.findById(event.userId()).map(User::getCash).orElse(0L);
            Map<String, Object> data = Map.of(
                    "orderType", event.orderType().name(),
                    "symbol", event.symbol(),
                    "amount", event.amount(),
                    "tradePrice", event.tradePrice(),
                    "cash", cash
            );
            userSseEmitters.notifyOrderExecution(event.userId(), NotificationResponse.builder()
                    .messageType(MessageType.INFO)
                    .message(Message.ORDER_EXECUTION)
                    .data(data)
                    .build());
        } catch (RuntimeException e) {
            log.error("Failed to notify order execution {}", event.orderId(), e);
        }
    }
}
