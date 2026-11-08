package bit.bitgroundspring.market;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CoinPriceHistoryRepository extends JpaRepository<CoinPriceHistory, Integer> {

    // 여러 코인의 지정한 날짜들 시세를 한 번에 조회 (투자 분석의 시즌 시작·종료일 시세)
    @Query("SELECT h FROM CoinPriceHistory h JOIN FETCH h.coin WHERE h.coin IN :coins AND h.date IN :dates")
    List<CoinPriceHistory> findByCoinsAndDates(@Param("coins") Collection<Coin> coins,
                                               @Param("dates") Collection<LocalDate> dates);
}
