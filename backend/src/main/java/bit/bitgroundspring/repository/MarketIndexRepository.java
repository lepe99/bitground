package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, Integer> {
    // 예: 오늘 날짜 데이터만 조회
    List<MarketIndex> findByDate(LocalDate date);
}