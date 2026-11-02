package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.projection.SeasonProjection;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Integer> {
    // 시즌 목록을 내림차순 정렬하여 조회 (최신 시즌부터, 최대 48개 시즌)
    List<SeasonProjection> findTop48ByOrderByIdDesc();

    Optional<Season> findByStatus(Status status);

    // ✅ 특정 상태(PENDING)의 시즌 중 가장 최근에 시작된 시즌 하나
    Optional<Season> findTopByStatusOrderByStartAtDesc(Status status);
}
