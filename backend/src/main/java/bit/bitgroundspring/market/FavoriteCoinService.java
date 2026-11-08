package bit.bitgroundspring.market;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Coin coin = coinRepo.findBySymbol(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));
        // 중복 방지
        favRepo.findByUserAndCoin(user, coin)
                .ifPresent(fc -> { throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS); });

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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Coin coin = coinRepo.findBySymbol(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));

        FavoriteCoin fc = favRepo.findByUserAndCoin(user, coin)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
        favRepo.delete(fc);
    }

    /** 즐겨찾기 목록 조회 **/
    @Transactional(readOnly = true)
    public List<String> listFavorites(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return favRepo.findAllByUser(user).stream()
                .map(fc -> fc.getCoin().getSymbol())
                .collect(Collectors.toList());
    }
}
