package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.response.PostDto;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Integer> {

    /**
     * 게시글 목록: 본문(content)은 읽지 않고 목록에 필요한 값만 DTO로 바로 조회.
     * category가 없으면(전체) 비공개 분류(문의)는 뺀다. ownerId가 있으면 그 사용자의 글만(일반 사용자의 문의 탭),
     * answered가 있으면 답변 여부로 거른다(문의 탭의 답변 대기·완료)
     */
    @Query(value = """
            SELECT new bit.bitgroundspring.dto.response.PostDto(
                p.id, u.id, u.name, u.profileImage, p.title, p.tier, p.likes, p.dislikes,
                p.isDeleted, p.createdAt, p.updatedAt, p.category, p.views,
                (SELECT COUNT(c) FROM Comment c WHERE c.post = p), p.hasImage, p.inquiryType, p.answeredAt)
            FROM Post p JOIN p.user u
            WHERE p.isDeleted = false
              AND (:category IS NULL OR p.category = :category)
              AND (:category IS NOT NULL OR p.category <> :hidden)
              AND (:ownerId IS NULL OR u.id = :ownerId)
              AND (:answered IS NULL OR (:answered = true AND p.answeredAt IS NOT NULL) OR (:answered = false AND p.answeredAt IS NULL))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Post p
            WHERE p.isDeleted = false
              AND (:category IS NULL OR p.category = :category)
              AND (:category IS NOT NULL OR p.category <> :hidden)
              AND (:ownerId IS NULL OR p.user.id = :ownerId)
              AND (:answered IS NULL OR (:answered = true AND p.answeredAt IS NOT NULL) OR (:answered = false AND p.answeredAt IS NULL))
            """)
    Page<PostDto> findPostList(@Param("category") Category category, @Param("hidden") Category hidden,
                               @Param("ownerId") Integer ownerId, @Param("answered") Boolean answered, Pageable pageable);

    /** 답변 대기 문의 수 (관리자 현황) */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.isDeleted = false AND p.category = :category AND p.answeredAt IS NULL")
    long countUnanswered(@Param("category") Category category);

    //게시글 상세보기
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id AND p.isDeleted = false")
    Optional<Post> findWithUserById(@Param("id") Integer id);

    // 카운터는 읽고 +1 해서 저장하지 않고 DB에서 원자적으로 증감 (동시 요청 시 갱신 유실 방지)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.views = p.views + 1 WHERE p.id = :id")
    int incrementViews(@Param("id") Integer id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likes = p.likes + :delta WHERE p.id = :id")
    int addLikes(@Param("id") Integer id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.dislikes = p.dislikes + :delta WHERE p.id = :id")
    int addDislikes(@Param("id") Integer id, @Param("delta") int delta);
}
