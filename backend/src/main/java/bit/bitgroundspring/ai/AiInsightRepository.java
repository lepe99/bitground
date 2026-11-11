
package bit.bitgroundspring.ai;

import bit.bitgroundspring.ai.AiInsight; // AiInsight 엔티티 임포트
import bit.bitgroundspring.ai.dto.AiInsightSymbolDto; // AiInsightSymbolDto 임포트 추가
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Query 임포트 추가
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, Integer> {
    // 특정 코인 심볼과 특정 날짜에 해당하는 AI 분석 결과가 이미 있는지 확인하는 메서드
    Optional<AiInsight> findBySymbolAndDate(String symbol, LocalDate date);

    /**
     * 오늘 코인 인사이트의 심볼·한글 이름·선정 이유. 스케줄러가 선정 순서대로 넣으므로 id 순서가 곧 화면 순서다.
     * MARKET_OVERALL은 coins 테이블에 없어 조인에서 빠진다.
     */
    @Query("SELECT NEW bit.bitgroundspring.ai.dto.AiInsightSymbolDto(ai.symbol, c.koreanName, ai.pickReason) " +
            "FROM AiInsight ai JOIN Coin c ON ai.symbol = c.symbol " +
            "WHERE ai.date = :date ORDER BY ai.id")
    List<AiInsightSymbolDto> findSymbolsByDate(@Param("date") LocalDate date);

    /**
     * "MARKET_OVERALL" 심볼에 대한 특정 날짜의 인사이트를 조회합니다.
     * MARKET_OVERALL 심볼은 coins 테이블에 매핑되지 않으므로, 이 메서드는 AiInsight 엔티티 자체만 반환합니다.
     * 이 메서드는 findBySymbolAndDate와 기능적으로 겹칠 수 있으나, 명시적인 쿼리를 통해 특정 케이스를 처리합니다.
     *
     * @param symbol 조회할 AI 인사이트 심볼 (예: "MARKET_OVERALL")
     * @param date 조회할 날짜
     * @return AiInsight 엔티티 (존재할 경우)
     */
    @Query("SELECT ai FROM AiInsight ai WHERE ai.symbol = :symbol AND ai.date = :date")
    Optional<AiInsight> findByAiInsightSymbolAndDate(@Param("symbol") String symbol, @Param("date") LocalDate date);
}
