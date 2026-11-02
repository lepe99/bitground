package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_insights",
        uniqueConstraints = {@UniqueConstraint(
                name = "uq_ai_insights_date_symbol",
                columnNames = {"date", "symbol"})
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AiInsight {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "symbol", nullable = false)
    private String symbol; // VARCHAR(255) NOT NULL, 어떤 코인에 대한 분석인지 식별
    
    @Column(name = "score", nullable = false, columnDefinition = "tinyint")
    private Integer score;
    
    @Column(name = "insight", length = 1000, nullable = false)
    private String insight;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt=LocalDateTime.now();
}