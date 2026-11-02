package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.TradeDto;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    /*마이페이지 사용*/
    @Query("SELECT o FROM Order o JOIN FETCH o.coin WHERE o.user = :user AND o.season = :season")
    List<Order> findByUserAndSeason(@Param("user") User user, @Param("season") Season season);

    @Query("SELECT o.id AS id, " +  // ✅ 추가
            "c.symbol AS symbol, c.koreanName AS coinName, " +
            "o.amount AS amount, o.tradePrice AS tradePrice, " +
            "o.reservePrice AS reservePrice, " +  // 필요 시 추가
            "o.status AS status, o.createdAt AS createdAt, o.updatedAt AS updatedAt, " +
            "o.orderType AS orderType " +
            "FROM Order o JOIN o.coin c WHERE o.user.id = :userId AND o.season.id = :seasonId")
    List<OrderProjection> findBySeasonIdAndUserId(@Param("seasonId") Integer seasonId, @Param("userId") Integer userId);



    @Query(
            "SELECT new bit.bitgroundspring.dto.TradeDto( " +
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
        SELECT new bit.bitgroundspring.dto.TradeDto(
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
    
    
    // 예약 매수 전용
    Optional<Order> findByIdAndStatus(Integer id, Status status);
    
    // 거래 가능 자산 조회 위한 메서드
    @Query("""
        SELECT CAST(ROUND(SUM(o.reservePrice * o.amount)) AS integer)
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
    Integer calculateTotalReservePriceForBuyOrdersByUserId(@Param("userId") Integer userId);

    //미체결
    @Query("SELECT o FROM Order o " +
            "JOIN FETCH o.coin " +
            "WHERE o.user.id = :userId AND o.season.id = :seasonId AND o.status = bit.bitgroundspring.entity.Status.PENDING")
    List<Order> findPendingOrdersByUserAndSeason(@Param("userId") Integer userId,
                                                 @Param("seasonId") Integer seasonId);


    // pending 상태 주문 조회
    List<Order> findByStatus(Status status);

}
