package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "reactions", uniqueConstraints =
@UniqueConstraint(columnNames = {"user_id", "target_type", "target_id", "liked"}))  // ✅ 좋아요/싫어요 각각 1번만
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReactionTargetType targetType; // POST or COMMENT

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private boolean liked; // ✅ 필드명 변경

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
