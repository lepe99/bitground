package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_advices",
        uniqueConstraints = {@UniqueConstraint(
                name = "uq_ai_advices_user_season",
                columnNames = {"user_id", "season_id"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AiAdvice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "ai_advices_users_userId_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false,
            foreignKey = @ForeignKey(name = "ai_advices_seasons_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE"))
    private Season season;
    
    @Column(name = "score", nullable = false , columnDefinition = "tinyint")
    private Integer score;
    
    @Column(name = "advice", length = 2000, nullable = false)
    private String advice;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
}