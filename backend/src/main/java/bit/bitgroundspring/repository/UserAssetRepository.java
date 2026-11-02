package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.projection.UserAssetProjection;
import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.UserAsset;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserAssetRepository extends JpaRepository<UserAsset, Integer> {
    //유저가 가지고있는 보유자산 목록
    List<UserAsset> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM UserAsset a WHERE a.user = :user AND a.coin = :coin")
    Optional<UserAsset> findByUserAndCoinWithLock(
            @Param("user") User user,
            @Param("coin") Coin coin
    );
    
    // 유저의 매도 가능 자산 조회
    @Query("""
    SELECT
        ua.coin.symbol as symbol,
        CASE
            WHEN COALESCE(SUM(o.amount), 0.0) <= ua.amount
            THEN ua.amount - COALESCE(SUM(o.amount), 0.0)
            ELSE 0.0
        END as amount,
        ua.avgPrice as avgPrice
    FROM UserAsset ua
    LEFT JOIN Order o ON o.coin.id = ua.coin.id
        AND o.user.id = ua.user.id
        AND o.orderType = 'SELL'
        AND o.status = 'PENDING'
        AND o.season.id = (
            SELECT s.id FROM Season s WHERE s.status = 'PENDING'
        )
    WHERE ua.user.id = :userId
    GROUP BY ua.coin.id, ua.coin.symbol, ua.amount, ua.avgPrice
    HAVING CASE
        WHEN COALESCE(SUM(o.amount), 0.0) <= ua.amount
        THEN ua.amount - COALESCE(SUM(o.amount), 0.0)
        ELSE 0.0
    END > 0
    """)
    List<UserAssetProjection> findUserAssetsWithAvailableAmount(@Param("userId") Integer userId);

    // 전체 보유 자산 조회용 projection 쿼리
    @Query("""
    SELECT 
        ua.coin.symbol as symbol,
        ua.coin.koreanName as coinName,
        ua.amount as amount,
        ua.avgPrice as avgPrice
    FROM UserAsset ua
    WHERE ua.user.id = :userId
    """)
    List<UserAssetProjection> findByUserId(@Param("userId") Integer userId);
}
