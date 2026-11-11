package bit.bitgroundspring.ai;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    
    @JdbcTypeCode(SqlTypes.TINYINT)
    
    @Column(name = "score", nullable = false, columnDefinition = "tinyint")
    private Integer score;
    
    @Column(name = "insight", length = 1000, nullable = false)
    private String insight;

    // daily-insight-v2부터 (스케줄러가 저장, 기존 행은 비어 있음)
    @Column(name = "mood", length = 10)
    private String mood; // 등락률 규칙으로 정한 분위기 라벨

    @Column(name = "drivers", columnDefinition = "text")
    private String drivers; // 근거 (JSON 문자열 배열)

    @Column(name = "risks", columnDefinition = "text")
    private String risks; // 지켜볼 점 (JSON 문자열 배열)

    @Column(name = "sources", columnDefinition = "text")
    private String sources; // 인용한 기사 (JSON 배열)

    @Column(name = "background", columnDefinition = "text")
    private String background; // 최근 30일 배경 (daily-insight-v4부터, 배경 기사가 없으면 null)

    @Column(name = "pick_reason", length = 40)
    private String pickReason; // 분석 대상으로 고른 이유

    @Column(name = "prompt_version", length = 40, nullable = false)
    @Builder.Default
    private String promptVersion = "v1";

    @Column(name = "model", length = 60)
    private String model;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt; // 저장할 때 @CreationTimestamp가 채운다
}