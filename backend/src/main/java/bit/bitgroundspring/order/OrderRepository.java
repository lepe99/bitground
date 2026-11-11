package bit.bitgroundspring.order;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.money.TradingFee;
import bit.bitgroundspring.order.dto.OrderProjection;
import bit.bitgroundspring.order.dto.TradeDto;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    // 시즌 리포트(거래 요약·상세): 체결된 주문만, 시간순 (미체결 예약 주문은 체결가가 없어 합계에 넣을 수 없다)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.user = :user AND o.season = :season " +
            "AND o.status = bit.bitgroundspring.common.Status.COMPLETED ORDER BY o.createdAt, o.id")
    List<Order> findCompletedByUserAndSeason(@Param("user") User user, @Param("season") Season season);

    @Query("SELECT o.id AS id, " +  // ✅ 추가
            "c.symbol AS symbol, c.koreanName AS coinName, " +
            "o.amount AS amount, o.tradePrice AS tradePrice, o.fee AS fee, " +
            "o.reservePrice AS reservePrice, " +  // 필요 시 추가
            "o.status AS status, o.createdAt AS createdAt, o.updatedAt AS updatedAt, " +
            "o.orderType AS orderType " +
            "FROM Order o JOIN o.coin c WHERE o.user.id = :userId AND o.season.id = :seasonId")
    List<OrderProjection> findBySeasonIdAndUserId(@Param("seasonId") Integer seasonId, @Param("userId") Integer userId);

    @Query(
            "SELECT new bit.bitgroundspring.order.dto.TradeDto( " +
                    "    o.coin.symbol,        " + // String
                    "    o.coin.koreanName,    " + // String
                    "    o.orderType,          " + // OrderType
                    "    o.amount,             " + // Float
                    "    o.tradePrice,         " + // Float
                    "    o.createdAt,          " + // LocalDateTime
                    "    o.updatedAt           " + // LocalDateTime
                    ") " +
                    "FROM Order o " +
                    "WHERE o.coin.symbol = :symbol " +
                    "  AND o.status       = :status " +
                    "ORDER BY o.createdAt DESC"
    )
    List<TradeDto> findTop30ByCoinSymbolAndStatus(
            @Param("symbol") String symbol,
            @Param("status") Status status
    );

    List<Order> findByUserAndSeasonAndStatus(User user, Season season, Status status);
    
    
    // 체결·취소가 같은 주문을 동시에 처리하지 않도록 주문 행을 잠그고 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Integer id);
    
    // 현재 시즌 지정가 매수의 (주문 가격 × 수량) 목록
    @Query("""
        SELECT o.reservePrice * o.amount
        FROM Order o
        WHERE o.user.id = :userId
          AND o.status = 'PENDING'
          AND o.orderType = 'BUY'
          AND o.season = (
              SELECT s
              FROM Season s
              WHERE s.status = 'PENDING'
          )
    """)
    List<BigDecimal> findPendingBuyValues(@Param("userId") Integer userId);

    /**
     * 지정가 매수에 묶인 현금: 주문마다 체결 때 낼 최대 금액(대금 원 단위 올림 + 수수료)의 합.
     * 체결 때와 같은 방식으로 주문별로 올림해야 합계가 실제로 빠질 돈보다 작아지지 않는다
     */
    default long reservedBuyCash(Integer userId) {
        return findPendingBuyValues(userId).stream()
                .mapToLong(v -> TradingFee.buyCost(v.setScale(0, RoundingMode.CEILING).longValueExact()))
                .sum();
    }

    // 현재 시즌에 낸 거래 수수료 합계 (원)
    @Query("""
        SELECT COALESCE(SUM(o.fee), 0)
        FROM Order o
        WHERE o.user.id = :userId
          AND o.status = 'COMPLETED'
          AND o.season = (
              SELECT s
              FROM Season s
              WHERE s.status = 'PENDING'
          )
    """)
    long sumCurrentSeasonFee(@Param("userId") Integer userId);

    // 현재 시즌에 체결 대기 중인 예약 매도 수량 (해당 코인)
    @Query("""
        SELECT COALESCE(SUM(o.amount), 0)
        FROM Order o
        WHERE o.user.id = :userId
          AND o.coin.id = :coinId
          AND o.status = 'PENDING'
          AND o.orderType = 'SELL'
          AND o.season = (
              SELECT s
              FROM Season s
              WHERE s.status = 'PENDING'
          )
    """)
    BigDecimal sumPendingSellAmount(@Param("userId") Integer userId, @Param("coinId") Integer coinId);

    //미체결
    @Query("SELECT o FROM Order o " +
            "JOIN FETCH o.coin " +
            "WHERE o.user.id = :userId AND o.season.id = :seasonId AND o.status = bit.bitgroundspring.common.Status.PENDING")
    List<Order> findPendingOrdersByUserAndSeason(@Param("userId") Integer userId,
                                                 @Param("seasonId") Integer seasonId);

    // 체결 대기 주문 전체 (Redis 예약 주문 대기열 재적재용)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.status = bit.bitgroundspring.common.Status.PENDING")
    List<Order> findAllPendingWithCoin();

}
