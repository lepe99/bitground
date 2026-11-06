package bit.bitgroundspring.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 이벤트를 원래 작업과 같은 트랜잭션의 아웃박스 테이블에 남긴다.
 * 작업이 롤백되면 이벤트도 남지 않고, 커밋되면 Redis·서버가 죽어도 {@link OutboxRelay}가 결국 전달한다.
 * 커밋 직후 전달기를 깨워 지연을 줄인다 (깨우지 못해도 주기 실행이 보낸다)
 */
@Component
@RequiredArgsConstructor
public class OutboxEvents {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxRelay relay;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OrderExecutedEvent event) {
        append(OrderExecutedEvent.TYPE, event);
    }

    private void append(String type, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패: " + type, e);
        }
        jdbc.update("INSERT INTO outbox_events (event_type, payload) VALUES (?, ?)", type, json);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relay.publishSoon();
            }
        });
    }
}
