package bit.bitgroundspring.service;

import org.apache.tomcat.websocket.server.WsContextListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 업비트 WebSocket 연결·재연결: 테스트 안에 작은 가짜 업비트 서버를 띄워 실제 소켓으로 확인한다.
 * (통합 테스트에서는 이 서비스를 Mock으로 바꿔 둔다)
 */
class UpbitWebSocketReconnectTest {

    /** 가짜 업비트: 받은 구독 요청을 모으고, 요청이 오면 시세 하나를 바이너리로 보낸다 (업비트와 같은 방식) */
    static class FakeUpbit extends TextWebSocketHandler {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger handshakes = new AtomicInteger();
        /** 처음 이만큼의 연결 요청은 503으로 거절한다 (업비트 점검 중) */
        volatile int rejectFirst;
        final List<String> subscriptions = new CopyOnWriteArrayList<>();
        final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            connections.incrementAndGet();
            sessions.add(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
            subscriptions.add(message.getPayload());
            String ticker = "{\"cd\":\"KRW-BTC\",\"tp\":101000000.5,\"ttms\":1700000000000}";
            session.sendMessage(new BinaryMessage(ticker.getBytes(StandardCharsets.UTF_8)));
        }

        /** 서버 쪽에서 비정상 종료 (업비트 점검·네트워크 끊김) */
        void dropAll() throws IOException {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    s.close(CloseStatus.SERVER_ERROR);
                }
            }
        }
    }

    static FakeUpbit fake;
    static int port;

    @Configuration
    @EnableWebSocket
    static class FakeServerConfig implements WebSocketConfigurer {
        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(fake, "/websocket/v1").addInterceptors(new HandshakeInterceptor() {
                @Override
                public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler handler, Map<String, Object> attributes) {
                    if (fake.handshakes.incrementAndGet() <= fake.rejectFirst) {
                        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        return false;
                    }
                    return true;
                }

                @Override
                public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler handler, Exception exception) {
                }
            });
        }

        @Bean
        TomcatServletWebServerFactory webServerFactory() {
            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(port);
            // 자동 설정 없이 띄우므로 Tomcat WebSocket 서버 컨테이너를 직접 켠다 (Boot는 자동 설정이 해 준다)
            factory.addContextCustomizers(context -> context.addApplicationListener(WsContextListener.class.getName()));
            return factory;
        }

        @Bean
        DispatcherServlet dispatcherServlet() {
            return new DispatcherServlet();
        }
    }

    private AnnotationConfigServletWebServerApplicationContext server;
    private ThreadPoolTaskExecutor executor;
    private ThreadPoolTaskScheduler scheduler;
    private PriceUpdateService priceUpdateService;
    private UpbitWebSocketService service;

    @BeforeEach
    void setUp() throws IOException {
        fake = new FakeUpbit();
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.initialize();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();

        CoinService coinService = mock(CoinService.class);
        when(coinService.getActiveSymbols()).thenReturn(List.of("KRW-BTC", "KRW-ETH"));
        priceUpdateService = mock(PriceUpdateService.class);

        service = new UpbitWebSocketService(coinService, priceUpdateService, executor, scheduler);
        ReflectionTestUtils.setField(service, "websocketUrl", "ws://localhost:" + port + "/websocket/v1");
        ReflectionTestUtils.setField(service, "connectionTimeout", 2_000L);
        ReflectionTestUtils.setField(service, "initialReconnectDelay", 100L); // 운영: 1초부터 두 배씩
        ReflectionTestUtils.setField(service, "maxReconnectDelay", 400L);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        scheduler.shutdown();
        stopServer();
    }

    private void startServer() {
        server = new AnnotationConfigServletWebServerApplicationContext(FakeServerConfig.class);
    }

    private void stopServer() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    @DisplayName("연결되면 활성 코인을 구독하고, 받은 시세를 체결 시각과 함께 넘긴다")
    void connectSubscribeAndForwardTicker() {
        startServer();
        service.onApplicationReady();

        await(() -> !fake.subscriptions.isEmpty());
        assertThat(fake.subscriptions.getFirst()).contains("\"ticker\"").contains("KRW-BTC").contains("KRW-ETH");
        await(service::isConnected);
        verifyEventually(() -> verify(priceUpdateService, atLeastOnce())
                .updatePrice(eq("KRW-BTC"), eq(new BigDecimal("101000000.5")), eq(1_700_000_000_000L)));
    }

    @Test
    @DisplayName("서버가 연결을 비정상 종료하면 다시 연결해 구독을 다시 보낸다")
    void reconnectsAfterServerDrop() throws IOException {
        startServer();
        service.onApplicationReady();
        await(() -> fake.subscriptions.size() == 1);

        fake.dropAll();

        await(() -> fake.connections.get() == 2 && fake.subscriptions.size() == 2);
        await(service::isConnected);
    }

    @Test
    @DisplayName("연결 시도가 실패하면(업비트 점검 503) 간격을 늘려 가며 다시 시도하고, 받아 주면 붙는다")
    void retriesAfterFailedConnect() {
        fake.rejectFirst = 3;
        startServer();
        service.onApplicationReady();

        await(() -> fake.subscriptions.size() == 1);
        await(service::isConnected);
        assertThat(fake.handshakes.get()).isEqualTo(4); // 거절 3번 + 성공 1번
        assertThat(fake.connections.get()).isEqualTo(1); // 연결이 둘 생기지 않는다
    }

    @Test
    @DisplayName("연결된 뒤 끊겼다가 재연결마저 실패해도 계속 다시 시도한다")
    void keepsRetryingWhenReconnectFails() throws IOException {
        startServer();
        service.onApplicationReady();
        await(() -> fake.subscriptions.size() == 1);

        fake.rejectFirst = fake.handshakes.get() + 2; // 이제부터 두 번 거절
        fake.dropAll();

        await(() -> fake.connections.get() == 2 && fake.subscriptions.size() == 2);
        assertThat(fake.handshakes.get()).isEqualTo(4); // 첫 연결 + 거절 2번 + 재연결
        await(service::isConnected);
    }

    @Test
    @DisplayName("종료 중에는 끊겨도 다시 연결하지 않는다")
    void noReconnectAfterShutdown() throws Exception {
        startServer();
        service.onApplicationReady();
        await(() -> fake.subscriptions.size() == 1);

        service.shutdown();
        fake.dropAll();
        Thread.sleep(800); // 재연결 간격(최대 0.4초)보다 충분히 김

        assertThat(fake.connections.get()).isEqualTo(1);
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    @DisplayName("가격이 0 이하이거나 필드가 빠진 메시지는 버린다")
    void ignoresInvalidTicker() throws Exception {
        startServer();
        service.onApplicationReady();
        await(() -> fake.subscriptions.size() == 1);
        verifyEventually(() -> verify(priceUpdateService).updatePrice(any(), any(), anyLong()));

        WebSocketSession upbit = fake.sessions.getFirst();
        upbit.sendMessage(new BinaryMessage("{\"cd\":\"KRW-ETH\",\"tp\":0}".getBytes(StandardCharsets.UTF_8)));
        upbit.sendMessage(new BinaryMessage("{\"cd\":\"KRW-ETH\"}".getBytes(StandardCharsets.UTF_8)));
        upbit.sendMessage(new BinaryMessage("not json".getBytes(StandardCharsets.UTF_8)));
        Thread.sleep(300);

        verify(priceUpdateService, never()).updatePrice(eq("KRW-ETH"), any(), anyLong());
        assertThat(service.isConnected()).isTrue(); // 이상한 메시지로 연결이 끊기지 않는다
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("조건이 10초 안에 충족되지 않음");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static void verifyEventually(Runnable verification) {
        await(() -> {
            try {
                verification.run();
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
    }
}
