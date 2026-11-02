package bit.bitgroundspring.entity;

import bit.bitgroundspring.dto.response.Message;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_created_at_desc", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_notifications_user_is_read", columnList = "user_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "notifications_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @Column(name = "message", nullable = false)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private Message messageType;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @Column(name = "is_read", columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private Boolean isRead = false;
}