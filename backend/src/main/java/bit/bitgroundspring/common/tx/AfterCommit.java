package bit.bitgroundspring.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 커밋이 끝난 뒤에 실행할 작업 등록
 * Redis·외부 전송처럼 롤백할 수 없는 작업을 트랜잭션 안에서 바로 실행하면,
 * DB가 롤백돼도 바깥에는 반영된 채로 남는다. 커밋이 확정된 뒤에만 실행한다.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /** 트랜잭션 안이면 커밋 후 실행, 트랜잭션 밖이면 즉시 실행 */
    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
