package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.FavoriteCoin;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.repository.FavoriteCoinRepository;
import bit.bitgroundspring.repository.CoinRepository;
import bit.bitgroundspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteCoinService {
    private final FavoriteCoinRepository favRepo;
    private final UserRepository userRepo;
    private final CoinRepository coinRepo;

    /** 즐겨찾기 등록 **/
    @Transactional
    public void addFavorite(Integer userId, String symbol) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));
        Coin coin = coinRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코인"));
        // 중복 방지
        favRepo.findByUserAndCoin(user, coin)
                .ifPresent(fc -> { throw new IllegalStateException("이미 즐겨찾기 등록됨"); });

        FavoriteCoin fc = FavoriteCoin.builder()
                .user(user)
                .coin(coin)
                .build();
        favRepo.save(fc);
    }

    /** 즐겨찾기 해제 **/
    @Transactional
    public void removeFavorite(Integer userId, String symbol) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));
        Coin coin = coinRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코인"));

        FavoriteCoin fc = favRepo.findByUserAndCoin(user, coin)
                .orElseThrow(() -> new IllegalStateException("즐겨찾기에 없음"));
        favRepo.delete(fc);
    }

    /** 즐겨찾기 목록 조회 **/
    @Transactional(readOnly = true)
    public List<String> listFavorites(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));
        return favRepo.findAllByUser(user).stream()
                .map(fc -> fc.getCoin().getSymbol())
                .collect(Collectors.toList());
    }
}
