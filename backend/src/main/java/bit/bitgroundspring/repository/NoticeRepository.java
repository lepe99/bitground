package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository  extends JpaRepository<Notice, Integer> {
    @EntityGraph(attributePaths = "user")
    Page<Notice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Notice> findByTitleContainingIgnoreCase(
            String title, Pageable pageable
    );
}
