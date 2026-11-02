// bit/bitgroundspring/entity/UserDailyBalance.java

package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_daily_balances",
        uniqueConstraints = {@UniqueConstraint(
                name = "uq_user_daily_balances_user_id_season_id_snapshot_date",
                columnNames = {"user_id", "season_id", "snapshot_date"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 엔티티를 위한 기본 생성자 (protected)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 모든 필드를 포함하는 생성자 (private)
@Builder // 빌더 패턴 제공
public class UserDailyBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // User 엔티티와의 다대일(Many-to-One) 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id", // 데이터베이스 컬럼명
            nullable = false,
            foreignKey = @ForeignKey( // 외래 키 제약 조건 정의
                    name = "fk_user_daily_balances_user_id",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
            )
    )
    private User user;

    // Season 엔티티와의 다대일(Many-to-One) 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "season_id", // 데이터베이스 컬럼명
            nullable = false,
            foreignKey = @ForeignKey( // 외래 키 제약 조건 정의
                    name = "fk_user_daily_balances_season_id",
                    foreignKeyDefinition = "FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE"
            )
    )
    private Season season;

    @Column(name = "snapshot_date", nullable = false, columnDefinition = "date")
    private LocalDate snapshotDate; // 스냅샷 기록 시각

    @Column(name = "total_value", nullable = false)
    private Integer totalValue; // 해당 시점의 총 자산 가치

    @Column(name = "cash_balance", nullable = false)
    private Integer cashBalance; // 해당 시점의 현금 잔고

    @Column(name = "coin_holdings_value", nullable = false)
    private Integer coinHoldingsValue; // 해당 시점의 보유 코인 평가액 합계

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt; // 생성 시각

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt; // 마지막 업데이트 시각
}
