package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.response.CoinSymbolDto;
import bit.bitgroundspring.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Integer> {
    // symbol 필드를 기준으로 코인을 조회하기 위한 사용자 정의 메서드
    Optional<Coin> findBySymbol(String symbol);

    // symbol, korean_name 반환
    @Query("SELECT new bit.bitgroundspring.dto.response.CoinSymbolDto(c.symbol, c.koreanName) "
            + "FROM Coin c WHERE c.isDeleted = false")
    List<CoinSymbolDto> findAllSymbols();

    List<Coin> findAllBySymbolIn(Collection<String> symbols);
    
    List<Coin> findByIsDeletedFalse();

    // 메인 화면 상위 5개: 전체를 읽어 자바에서 정렬하지 않고 DB에서 정렬·LIMIT (상장 폐지 코인 제외)
    List<Coin> findTop5ByIsDeletedFalseOrderByTradePrice24hDesc();

    List<Coin> findTop5ByIsDeletedFalseAndChangeRateNotNullOrderByChangeRateDesc();

    List<Coin> findTop5ByIsDeletedFalseAndChangeRateNotNullOrderByChangeRateAsc();

    List<Coin> findByIsDeletedFalseAndIsWarningTrue();

    List<Coin> findByIsDeletedFalseAndIsCautionTrue();
}
