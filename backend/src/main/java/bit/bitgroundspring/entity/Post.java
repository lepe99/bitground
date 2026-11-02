package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts",
        indexes = {
                @Index(name = "idx_posts_deleted_category_created_at_desc", columnList = "is_deleted, category, created_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Post {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "posts_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @Column(name = "tier", nullable = false, columnDefinition = "tinyint")
    private Integer tier;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;
    
    @Column(name = "views", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer views = 0;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "content", nullable = false, columnDefinition = "mediumtext")
    private String content;
    
    @Column(name = "likes", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer likes = 0;
    
    @Column(name = "dislikes", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer dislikes = 0;
    
    @Column(name = "is_deleted", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private Boolean isDeleted = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}