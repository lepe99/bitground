// bit/bitgroundspring/repository/CoinPriceHistoryRepository.java

package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.entity.CoinPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoinPriceHistoryRepository extends JpaRepository<CoinPriceHistory, Integer> {

    // 특정 코인과 날짜에 대한 가장 최신 시간(hour)의 가격 이력을 조회합니다. (단일 결과)
    // InvestmentAnalysisService에서 시즌 종료일의 코인 종가를 가져올 때 사용됩니다.
    Optional<CoinPriceHistory> findTopByCoin_SymbolAndDateOrderByHourDesc(String coinSymbol, LocalDate date);

    // 특정 코인과 날짜에 해당하는 모든 가격 이력을 시간(hour) 오름차순으로 조회합니다.
    // 이는 특정 날짜의 시작 가격(openPrice)을 찾기 위해 첫 번째 요소를 사용하거나,
    // 특정 날짜의 종료 가격(closePrice)을 찾기 위해 마지막 요소를 사용할 수 있습니다.
    List<CoinPriceHistory> findByCoinAndDateOrderByHourAsc(Coin coin, LocalDate date);

    // ⭐ 새로 추가: 특정 코인과 특정 날짜에 대한 모든 가격 이력을 시간(hour) 내림차순으로 조회합니다.
    // 시즌 종료일의 종가 또는 특정 날짜의 마지막 캔들을 가져올 때 유용합니다.
    List<CoinPriceHistory> findByCoinAndDateOrderByHourDesc(Coin coin, LocalDate date);

    // 추가적으로, 특정 코인과 날짜 범위에 해당하는 데이터를 조회하는 메서드가 필요하다면 아래와 같이 정의할 수 있습니다.
    // 하지만 현재는 findByCoinAndDateOrderByHourAsc/Desc 와 조합하여 사용합니다.
    // List<CoinPriceHistory> findByCoinAndDateBetweenOrderByDateAscHourAsc(Coin coin, LocalDate startDate, LocalDate endDate);
}
