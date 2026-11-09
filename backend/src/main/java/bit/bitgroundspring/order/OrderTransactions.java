package bit.bitgroundspring.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 체결 트랜잭션 (READ COMMITTED). 사용자 행을 잠근 뒤 보유 자산 행을 잠그고, 없으면 새로 만든다.
 *
 * <p>기본 격리 수준(REPEATABLE READ)에서는 아직 없는 자산 행을 잠그면 행 대신 유니크 인덱스의 빈 구간이 잠긴다(갭 락).
 * 갭 락은 여러 트랜잭션이 함께 가질 수 있어서, 같은 구간에서 처음 사는 다른 사용자와 서로의 행 추가를 기다리다
 * 데드락이 났다 (부하 테스트에서 시장가 매수 실패, TradeConcurrencyTest).
 * READ COMMITTED에서는 잠금 조회가 찾은 행만 잠그므로 이런 충돌이 없고, 같은 사용자의 주문끼리는 사용자 행 잠금으로 순서가 정해진다.
 */
@Component
public class OrderTransactions {

    private final TransactionTemplate template;

    public OrderTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> action) {
        return template.execute(status -> action.get());
    }

    public void run(Runnable action) {
        template.executeWithoutResult(status -> action.run());
    }
}
