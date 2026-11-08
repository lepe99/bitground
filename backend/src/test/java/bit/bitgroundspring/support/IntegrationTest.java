package bit.bitgroundspring.support;

import bit.bitgroundspring.market.UpbitWebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 통합 테스트 공통 설정.
 * - MySQL·Redis는 Testcontainers로 실제 컨테이너를 띄우고, 스키마는 운영과 같은 Flyway 마이그레이션으로 생성
 * - 외부 연동(Upbit 시세 REST·WebSocket, S3)은 Mock으로 대체
 * - 컨테이너는 테스트 클래스 간에 재사용 (싱글턴 컨테이너 패턴)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
@TestPropertySource(properties = {
        "spring.config.import=", // 로컬 backend/.env 를 읽지 않음
        "jwt.secret=test-secret-key-for-integration-tests-must-be-at-least-64-bytes-long-0123456789",
        "season.update.key=test-season-key",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.security.oauth2.client.registration.naver.client-id=test",
        "spring.security.oauth2.client.registration.naver.client-secret=test",
        "spring.security.oauth2.client.registration.kakao.client-id=test",
        "spring.security.oauth2.client.registration.kakao.client-secret=test",
        "storage.endpoint=http://storage.test",
        "storage.access-key=test",
        "storage.secret-key=test",
        "management.server.port=", // 액추에이터 별도 포트 비활성화
        "upbit.order.execution.reclaim-idle=1s", // 확인 못 한 체결 요청 넘겨받기를 테스트에서 오래 기다리지 않게
        "order-book.watchdog.enabled=false" // Redis 감시·대조는 테스트가 직접 부른다 (주기 실행이 테스트 중 대기열을 바꾸지 않게)
})
public abstract class IntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bitground")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean
    protected UpbitWebSocketService upbitWebSocketService; // 실제 Upbit WebSocket 연결 방지

    @MockitoBean
    protected RestTemplate restTemplate; // Upbit 시세·네이버 뉴스 등 외부 REST 호출

    @MockitoBean
    protected S3Client s3Client;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestFixtures fixtures;

    @BeforeEach
    void resetData() {
        fixtures.clear();
    }
}
