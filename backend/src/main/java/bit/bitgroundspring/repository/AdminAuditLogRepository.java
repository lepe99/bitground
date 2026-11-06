package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Integer> {
}
