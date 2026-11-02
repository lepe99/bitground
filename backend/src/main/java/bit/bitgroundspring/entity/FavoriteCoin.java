package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "favorite_coins",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_favorite_user_symbol",
                columnNames = { "user_id", "symbol_id" }
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FavoriteCoin {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "favorite_coins_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false,
            foreignKey = @ForeignKey(name = "favorite_coins_coins_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (symbol_id) REFERENCES coins(id) ON DELETE CASCADE"))
    private Coin coin;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, insertable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
}