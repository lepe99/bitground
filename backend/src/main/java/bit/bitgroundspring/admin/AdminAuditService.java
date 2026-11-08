package bit.bitgroundspring.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 작업 기록 남기기. 작업과 같은 트랜잭션에 넣어, 작업이 취소되면 기록도 남지 않게 한다 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int DETAIL_MAX = 500;

    private final AdminAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(int adminId, String action, String targetType, Integer targetId, String detail) {
        String trimmed = detail == null ? null : detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail;
        repository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detail(trimmed)
                .build());
    }
}
