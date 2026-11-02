package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_season", columnList = "user_id, season_id"),
                @Index(name = "idx_orders_season_status_user", columnList = "season_id, status, user_id"),
                @Index(name = "idx_orders_symbol_status_created_at_desc", columnList = "symbol_id, status, created_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "orders_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false,
            foreignKey = @ForeignKey(name = "orders_coins_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (symbol_id) REFERENCES coins(id) ON DELETE CASCADE"))
    private Coin coin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false,
            foreignKey = @ForeignKey(name = "orders_seasons_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE"))
    private Season season;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "enum('PENDING', 'COMPLETED') default 'PENDING'")
    @Builder.Default
    private Status status = Status.PENDING;
    
    // 가격·수량은 DECIMAL/BigDecimal (부동소수점 반올림 오차 방지)
    @Column(name = "reserve_price", precision = 24, scale = 8)
    private BigDecimal reservePrice;
    
    @Column(name = "trade_price", precision = 24, scale = 8)
    private BigDecimal tradePrice;
    
    @Column(name = "amount", nullable = false, precision = 30, scale = 10)
    private BigDecimal amount;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
}