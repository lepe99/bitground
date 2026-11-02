package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_rankings",
        uniqueConstraints = {@UniqueConstraint(
                name = "uq_user_rankings_user_symbol",
                columnNames = {"user_id", "season_id"})
        },
        indexes = {
                @Index(name = "idx_user_rankings_season_ranks", columnList = "season_id, ranks"),
                @Index(name = "idx_user_rankings_user_season", columnList = "user_id, season_id"), // 기존 uniqueConstraint와 동일한 컬럼이더라도, index로 명시하여 명확성을 높일 수 있습니다. (DB가 알아서 최적화)
                @Index(name = "idx_user_rankings_user_tier_desc", columnList = "user_id, tier DESC")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserRanking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "users_rankings_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false,
            foreignKey = @ForeignKey(name = "users_rankings_seasons_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE"))
    private Season season;
    
    @Column(name = "tier", nullable = false, columnDefinition = "tinyint default 0")
    @Builder.Default
    private Integer tier = 0;
    
    @Column(name = "ranks", nullable = false)
    private Integer ranks;
    
    @Column(name = "total_value", nullable = false)
    private Integer totalValue;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}