package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_assets",
        uniqueConstraints = {@UniqueConstraint(
                name = "uq_user_assets_user_symbol",
                columnNames = {"user_id", "symbol_id"})
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserAsset {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "user_assets_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false,
            foreignKey = @ForeignKey(name = "user_assets_coins_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (symbol_id) REFERENCES coins(id) ON DELETE CASCADE"))
    private Coin coin;
    
    @Column(name = "amount", nullable = false, columnDefinition = "DOUBLE")
    private Double amount;
    
    @Column(name = "avg_price", nullable = false, columnDefinition = "DOUBLE")
    private Double avgPrice;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}