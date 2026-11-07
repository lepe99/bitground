package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    // 시즌 리포트(거래 요약·상세): 체결된 주문만, 시간순 (미체결 예약 주문은 체결가가 없어 합계에 넣을 수 없다)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.user = :user AND o.season = :season " +
            "AND o.status = bit.bitgroundspring.entity.Status.COMPLETED ORDER BY o.createdAt, o.id")
    List<Order> findCompletedByUserAndSeason(@Param("user") User user, @Param("season") Season season);

    @Query("SELECT o.id AS id, " +  // ✅ 추가
            "c.symbol AS symbol, c.koreanName AS coinName, " +
            "o.amount AS amount, o.tradePrice AS tradePrice, " +
            "o.reservePrice AS reservePrice, " +  // 필요 시 추가
            "o.status AS status, o.createdAt AS createdAt, o.updatedAt AS updatedAt, " +
            "o.orderType AS orderType " +
            "FROM Order o JOIN o.coin c WHERE o.user.id = :userId AND o.season.id = :seasonId")
    List<OrderProjection> findBySeasonIdAndUserId(@Param("seasonId") Integer seasonId, @Param("userId") Integer userId);



    @Query(
            "SELECT new bit.bitgroundspring.dto.response.TradeDto( " +
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

    @Query("""
        SELECT new bit.bitgroundspring.dto.response.TradeDto(
            o.coin.symbol,
            o.coin.koreanName,
            o.orderType,
            o.amount,
            o.tradePrice,
            o.createdAt,
            o.updatedAt
        )
        FROM Order o
        WHERE o.coin.symbol = :symbol
          AND o.status       = :status
          AND o.createdAt > :since
        ORDER BY o.createdAt ASC
    """)
    List<TradeDto> findNewTradesByCoinSymbolAndStatusSince(
            @Param("symbol") String symbol,
            @Param("status") Status status,
            @Param("since") LocalDateTime since
    );

    List<Order> findByUserAndSeasonAndStatus(User user, Season season, Status status);
    
    
    // 체결·취소가 같은 주문을 동시에 처리하지 않도록 주문 행을 잠그고 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Integer id);
    
    // 현재 시즌 예약 매수에 묶인 금액 합계 (예약가 × 수량)
    @Query("""
        SELECT SUM(o.reservePrice * o.amount)
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
    BigDecimal sumPendingBuyCost(@Param("userId") Integer userId);

    /** 예약 매수에 묶인 현금 (원 단위 올림, 없으면 0) */
    default long reservedBuyCash(Integer userId) {
        BigDecimal sum = sumPendingBuyCost(userId);
        return sum == null ? 0 : sum.setScale(0, RoundingMode.CEILING).longValueExact();
    }

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
            "WHERE o.user.id = :userId AND o.season.id = :seasonId AND o.status = bit.bitgroundspring.entity.Status.PENDING")
    List<Order> findPendingOrdersByUserAndSeason(@Param("userId") Integer userId,
                                                 @Param("seasonId") Integer seasonId);


    // 체결 대기 주문 전체 (Redis 예약 주문 대기열 재적재용)
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.status = bit.bitgroundspring.entity.Status.PENDING")
    List<Order> findAllPendingWithCoin();

}
