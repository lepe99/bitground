package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    @EntityGraph(attributePaths = {"children", "children.user", "user"})
    List<Comment> findByPostIdAndParentIsNullOrderByCreatedAtAsc(Integer postId);

    Long countByPostId(Integer postId);
}
