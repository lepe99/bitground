package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoardRepository extends JpaRepository<Post, Integer> {

    // 게시글 목록: 본문(content)은 읽지 않고 목록에 필요한 값만 DTO로 바로 조회
    @Query(value = """
            SELECT new bit.bitgroundspring.dto.BoardDto(
                p.id, u.id, u.name, u.profileImage, p.title, p.tier, p.likes, p.dislikes,
                p.isDeleted, p.createdAt, p.updatedAt, p.category, p.views,
                (SELECT COUNT(c) FROM Comment c WHERE c.post = p), p.hasImage)
            FROM Post p JOIN p.user u
            WHERE p.isDeleted = false AND (:category IS NULL OR p.category = :category)
            """,
            countQuery = "SELECT COUNT(p) FROM Post p WHERE p.isDeleted = false AND (:category IS NULL OR p.category = :category)")
    Page<BoardDto> findBoardList(@Param("category") Category category, Pageable pageable);

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
