package bit.bitgroundspring.admin;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 관리자 작업 기록 (누가 언제 무엇을 했는지). 고치거나 지우지 않고 쌓기만 한다 */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "admin_id", nullable = false)
    private Integer adminId;

    @Column(name = "action", nullable = false, length = 40)
    private String action; // USER_SUSPEND, POST_DELETE, NOTICE_UPDATE ...

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // USER, POST, COMMENT, NOTICE

    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "detail", length = 500)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
