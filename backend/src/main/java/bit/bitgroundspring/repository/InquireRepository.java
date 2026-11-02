package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquireRepository extends JpaRepository<Inquiry, Integer> {

    @EntityGraph(attributePaths = "user")
    Page<Inquiry> findByTitleContainingIgnoreCase(
            String title, Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    Page<Inquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
