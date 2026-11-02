// src/main/java/bit/bitgroundspring/repository/AiInsightRepository.java

package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.AiInsight; // AiInsight 엔티티 임포트
import bit.bitgroundspring.dto.AiInsightSymbolDto; // AiInsightSymbolDto 임포트 추가
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Query 임포트 추가
import org.springframework.data.repository.query.Param; // Param 임포트 제거 (필요 없어짐)
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, Integer> {
    // 특정 코인 심볼과 특정 날짜에 해당하는 AI 분석 결과가 이미 있는지 확인하는 메서드
    Optional<AiInsight> findBySymbolAndDate(String symbol, LocalDate date);

    // 특정 코인 심볼에 대한 모든 AI 분석 결과를 최신순으로 가져오는 메서드
    List<AiInsight> findBySymbolOrderByCreatedAtDesc(String symbol);

    /**
     * 오늘 날짜에 대한 AI 인사이트 중 드롭다운에 표시할 심볼과 한글 이름을 조회합니다.
     * AiInsight 엔티티와 Coin 엔티티를 조인하여 AiInsightSymbolDto에 매핑합니다.
     * 이 메서드는 특정 날짜를 매개변수로 받지 않고, 항상 '오늘' 날짜를 기준으로 조회합니다.
     * (데이터베이스의 `CURDATE()` 함수를 사용하여 '오늘' 날짜를 지정합니다. 이는 MySQL 기준입니다.)
     *
     * @return AiInsightSymbolDto 목록
     */
    @Query("SELECT NEW bit.bitgroundspring.dto.AiInsightSymbolDto(ai.symbol, c.koreanName) " +
            "FROM AiInsight ai JOIN Coin c ON ai.symbol = c.symbol " +
            "WHERE ai.date = CURDATE()") // CURDATE() 함수를 사용하여 오늘 날짜 지정 (MySQL 전용)
    List<AiInsightSymbolDto> findTodaySymbolsAndKoreanNames(); // 매개변수 제거

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
