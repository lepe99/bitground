package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Season {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "start_at")
    private LocalDate startAt;
    
    @Column(name = "end_at")
    private LocalDate endAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "enum('PENDING', 'COMPLETED') default 'PENDING'")
    @Builder.Default
    private Status status = Status.PENDING;
    
    @Column(name = "reward_calculated", columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private Boolean rewardCalculated = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
}