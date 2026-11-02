// bit/bitgroundspring/entity/CoinPriceHistory.java

package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;   // LocalDate 임포트
import java.time.LocalDateTime;

@Entity
@Table(
        name = "coin_price_history",
        uniqueConstraints = {
                @UniqueConstraint( // date, hour 기준으로 Unique Constraint 변경
                        name = "uq_coin_price_history_coin_id_date_hour",
                        columnNames = {"coin_id", "date", "hour"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 엔티티를 위한 기본 생성자 (protected)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 모든 필드를 포함하는 생성자 (private)
@Builder // 빌더 패턴 제공
public class CoinPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // Coin 엔티티와의 다대일(Many-to-One) 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "coin_id", // 데이터베이스 컬럼명
            nullable = false,
            foreignKey = @ForeignKey( // 외래 키 제약 조건 정의
                    name = "fk_coin_price_history_coin_id",
                    foreignKeyDefinition = "FOREIGN KEY (coin_id) REFERENCES coins(id) ON DELETE CASCADE"
            )
    )
    private Coin coin; // `coins` 테이블의 ID (FK)

    @Column(name = "date", nullable = false)
    private LocalDate date; // 캔들 데이터 기준 날짜

    @Column(name = "hour", nullable = false, columnDefinition = "tinyint")
    private Integer hour; // 캔들 데이터 기준 시간 (0-23)

    @Column(name = "open_price", nullable = false)
    private Float openPrice; // 해당 캔들의 시작 가격

    @Column(name = "high_price", nullable = false)
    private Float highPrice; // 해당 캔들의 최고 가격

    @Column(name = "low_price", nullable = false)
    private Float lowPrice; // 해당 캔들의 최저 가격

    @Column(name = "close_price", nullable = false)
    private Float closePrice; // 해당 캔들의 종료 가격 (종가)

    @Column(name = "volume", nullable = false)
    private Float volume; // 해당 캔들의 거래량

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시각 기록
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt; // 생성 시각
}
