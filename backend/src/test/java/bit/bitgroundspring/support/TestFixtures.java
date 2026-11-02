package bit.bitgroundspring.support;

import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.CoinRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 테스트 데이터 생성·정리와 로그인 쿠키 발급
 */
@TestComponent
@RequiredArgsConstructor
public class TestFixtures {

    // FK 순서와 무관하게 비우기 위해 FOREIGN_KEY_CHECKS 를 끄고 TRUNCATE
    private static final List<String> TABLES = List.of(
            "reactions", "comments", "posts", "notices", "notifications", "favorite_coins", "orders",
            "user_assets", "user_rankings", "user_daily_balances", "ai_advices", "ai_insights",
            "coin_price_history", "market_indices", "coins", "seasons", "users");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final CoinRepository coinRepository;
    private final SeasonRepository seasonRepository;
    private final UserAssetRepository userAssetRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public void clear() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        TABLES.forEach(t -> jdbc.execute("TRUNCATE TABLE " + t));
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushDb();
            return null;
        });
    }

    public User user(int cash) {
        return userRepository.save(User.builder()
                .name("user-" + UUID.randomUUID().toString().substring(0, 8))
                .provider("google")
                .providerId(UUID.randomUUID().toString())
                .cash((long) cash)
                .build());
    }

    public User admin() {
        User admin = user(0);
        admin.setRole(Role.ROLE_ADMIN);
        return userRepository.save(admin);
    }

    public Season activeSeason() {
        return seasonRepository.save(Season.builder()
                .name("테스트 시즌")
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(14))
                .status(Status.PENDING)
                .build());
    }

    public Coin coin(String symbol) {
        return coinRepository.save(Coin.builder()
                .symbol(symbol)
                .koreanName(symbol)
                .tradePrice24h(0L)
                .changeRate(0f)
                .isCaution(false)
                .isWarning(false)
                .isDeleted(false)
                .build());
    }

    public UserAsset asset(User user, Coin coin, double amount, double avgPrice) {
        return userAssetRepository.save(UserAsset.builder()
                .user(user).coin(coin).amount(BigDecimal.valueOf(amount)).avgPrice(BigDecimal.valueOf(avgPrice)).build());
    }

    // 실제 로그인과 같은 형식의 JWT를 jwt_token 쿠키로 발급
    public Cookie login(User user) {
        return new Cookie("jwt_token", jwtTokenProvider.createToken(user));
    }
}
