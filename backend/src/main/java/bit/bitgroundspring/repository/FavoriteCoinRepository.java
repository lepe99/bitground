package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.FavoriteCoin;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteCoinRepository extends JpaRepository<FavoriteCoin, Integer> {
    // 해당 사용자가 등록한 모든 즐겨찾기
    List<FavoriteCoin> findAllByUser(User user);
    // 특정 사용자+코인 조합 조회
    Optional<FavoriteCoin> findByUserAndCoin(User user, Coin coin);
}
