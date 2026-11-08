package bit.bitgroundspring.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    // 게시글의 댓글 전체를 작성자와 함께 한 번에 조회 (트리는 서비스에서 메모리로 구성)
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findAllWithUserByPostId(@Param("postId") Integer postId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likes = c.likes + :delta WHERE c.id = :id")
    int addLikes(@Param("id") Integer id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.dislikes = c.dislikes + :delta WHERE c.id = :id")
    int addDislikes(@Param("id") Integer id, @Param("delta") int delta);

    Long countByPostId(Integer postId);
}
