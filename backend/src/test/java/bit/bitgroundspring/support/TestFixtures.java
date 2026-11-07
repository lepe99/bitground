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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Statement;

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

    // FK 순서와 무관하게 비우기 위해 FOREIGN_KEY_CHECKS 를 끄고 DELETE
    private static final List<String> TABLES = List.of(
            "admin_audit_logs", "outbox_events", "reactions", "comments", "posts", "notices", "notification_settings", "notifications", "favorite_coins", "orders",
            "user_assets", "user_rankings", "user_daily_balances", "ai_advices", "ai_insights",
            "coin_price_history", "market_indices", "coins", "seasons", "users");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final CoinRepository coinRepository;
    private final SeasonRepository seasonRepository;
    private final UserAssetRepository userAssetRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 테스트마다 DB·Redis를 비운다.
     * TRUNCATE는 테이블을 새로 만들어 표마다 0.1초 이상 걸리고, 백그라운드 작업(스트림 소비·아웃박스 전달)의 조회와
     * 메타데이터 잠금을 기다려 테스트 하나가 5~12초씩 걸렸다. 몇 행뿐인 테스트 데이터는 DELETE가 훨씬 빠르다.
     * FK 검사 끄기는 연결 단위 설정이라 한 연결에서 함께 실행한다
     */
    public void clear() {
        jdbc.execute((ConnectionCallback<Object>) connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : TABLES) {
                    st.execute("DELETE FROM " + table);
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
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
