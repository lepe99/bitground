package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, Integer> {
    // 예: 오늘 날짜 데이터만 조회
    List<MarketIndex> findByDate(LocalDate date);

    // 기간의 첫 기록 / 마지막 기록 (시즌 동안 시장 지수 등락률 계산용)
    Optional<MarketIndex> findFirstByDateBetweenOrderByDateAscHourAsc(LocalDate from, LocalDate to);

    Optional<MarketIndex> findFirstByDateBetweenOrderByDateDescHourDesc(LocalDate from, LocalDate to);
}