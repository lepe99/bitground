package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "comments",
        indexes = {
                @Index(name = "idx_comments_post_parent_created_at", columnList = "post_id, parent_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Comment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false,
            foreignKey = @ForeignKey(name = "comments_posts_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE"))
    private Post post;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "comments_users_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"))
    private User user;
    
    @Column(name = "content", nullable = false)
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id",
            foreignKey = @ForeignKey(name = "comments_comments_id_fk",
                    foreignKeyDefinition = "FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE"))
    private Comment parent;
    
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<Comment> children;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}