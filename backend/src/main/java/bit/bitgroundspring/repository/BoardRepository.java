package bit.bitgroundspring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import bit.bitgroundspring.entity.Post;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoardRepository extends JpaRepository<Post, Integer> {

    //게시글 목록 출력
    @Query(value = "SELECT p.id, u.id AS userId, u.name, u.profile_image, " +  // ✅ 여기!
            "p.title, p.content, p.tier, p.likes, p.dislikes, p.is_deleted, " +
            "p.created_at, p.updated_at, p.category, p.views, " +
            "(SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS commentCount " +
            "FROM posts p " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE p.is_deleted = false AND (:category IS NULL OR p.category = :category)",

            countQuery = "SELECT COUNT(*) FROM posts p JOIN users u ON p.user_id = u.id " +
                    "WHERE p.is_deleted = false AND (:category IS NULL OR p.category = :category)",

            nativeQuery = true)
    Page<Object[]> findAllBoardDtosRaw(@Param("category") String category, Pageable pageable);
    //게시글 상세보기
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Post> findWithUserById(@Param("id") Integer id);
}