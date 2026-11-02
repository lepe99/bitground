package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiries",
        indexes = { // Indexes added here
                @Index(name = "idx_inquiries_created_at_desc", columnList = "created_at DESC"),
                @Index(name = "idx_inquiries_title", columnList = "title")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Inquiry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "inquiries_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "content", length = 2000, nullable = false)
    private String content;
    
    @Column(name = "answer", length = 2000)
    private String answer;
    
    @Column(name = "is_answered", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private Boolean isAnswered = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    @Column(name = "answer_writer")
    private String answerWriter;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private Boolean isDeleted = false;
}