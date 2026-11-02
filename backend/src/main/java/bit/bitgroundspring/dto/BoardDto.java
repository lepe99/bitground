package bit.bitgroundspring.dto;

import bit.bitgroundspring.dto.projection.PastSeasonTierProjection;
import bit.bitgroundspring.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoardDto {
    private Integer id;
    private Integer userId;
    private String name;
    private String profileImage;
    private String title;
    private String content;
    private int tier;
    private int likes;
    private int dislikes;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String category;
    private int views;
    private Long commentCount;
    private boolean hasImage;

    // 이 두 개는 상세 보기용에서만 사용
    private int highestTier;
    private List<PastSeasonTierProjection> pastSeasonTiers;

    // 목록 조회용 생성자 (본문 제외, BoardRepository.findBoardList JPQL에서 사용)
    public BoardDto(Integer id, Integer userId, String name, String profileImage,
                    String title, Integer tier, Integer likes, Integer dislikes,
                    Boolean isDeleted, LocalDateTime createdAt, LocalDateTime updatedAt,
                    Category category, Integer views, Long commentCount, Boolean hasImage) {
        this(id, userId, name, profileImage, title, null, tier, likes, dislikes, Boolean.TRUE.equals(isDeleted),
                createdAt, updatedAt, category.name(), views, commentCount, Boolean.TRUE.equals(hasImage));
    }

    // 상세 조회용 생성자 (기본 필드만)
    public BoardDto(Integer id, Integer userId, String name, String profileImage,
                    String title, String content, int tier, int likes, int dislikes,
                    boolean isDeleted, LocalDateTime createdAt, LocalDateTime updatedAt,
                    String category, int views, Long commentCount, boolean hasImage) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.profileImage = profileImage;
        this.title = title;
        this.content = content;
        this.tier = tier;
        this.likes = likes;
        this.dislikes = dislikes;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.category = category;
        this.views = views;
        this.commentCount = commentCount;
        this.hasImage = hasImage;
    }
}