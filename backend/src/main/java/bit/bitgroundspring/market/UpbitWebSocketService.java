package bit.bitgroundspring.market;

import bit.bitgroundspring.order.PriceUpdateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.event.Level;

@Service
@Slf4j
public class UpbitWebSocketService {

    private final CoinService coinService;
    private final PriceUpdateService priceUpdateService;

    // 추가: Config 클래스에서 생성한 TaskExecutor 주입
    @Qualifier("webSocketTaskExecutor")
    private final Executor webSocketTaskExecutor;

    // 추가: Spring의 스케줄러 주입
    private final TaskScheduler taskScheduler;

    @Value("${upbit.websocket.ticker-url}")
    private String websocketUrl;

    @Value("${upbit.websocket.connection-timeout}")
    private long connectionTimeout;

    @Value("${upbit.websocket.reconnect.initial-delay}")
    private long initialReconnectDelay;

    @Value("${upbit.websocket.reconnect.max-delay}")
    private long maxReconnectDelay;

    private volatile WebSocketSession session;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 재연결 관련 설정
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    /** 끊긴 시각 (다시 붙으면 얼마나 끊겼는지 남긴다) */
    private final AtomicReference<Instant> disconnectedAt = new AtomicReference<>();
    /** 연결을 시작해 결과를 기다리는 중 (상태 점검이 연결을 하나 더 만들지 않게) */
    private volatile boolean connecting;
    /** 지금 연결의 핸들러. 이보다 예전 연결의 이벤트는 무시하고 세션은 닫는다 */
    private volatile Object currentHandler;
    /** 재연결이 이만큼 연달아 실패하면 WARN (약 7초), 이만큼이면 ERROR (약 2분 이상 시세 없음) */
    private static final int WARN_AFTER_FAILURES = 3;
    private static final int ERROR_AFTER_FAILURES = 7;
    private volatile boolean isReconnecting = false;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public UpbitWebSocketService(
            CoinService coinService,
            PriceUpdateService priceUpdateService,
            @Qualifier("webSocketTaskExecutor") Executor webSocketTaskExecutor,
            TaskScheduler taskScheduler) {
        this.coinService = coinService;
        this.priceUpdateService = priceUpdateService;
        this.webSocketTaskExecutor = webSocketTaskExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 기동이 끝나면 한 번 연결한다. ContextRefreshedEvent는 액추에이터 전용 포트의 하위 컨텍스트 때문에 두 번 와서
     * 업비트 연결이 두 개 생기고 시세를 두 번씩 처리했다 → 한 번만 오는 ApplicationReadyEvent로 바꿈
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        connecting = true;
        webSocketTaskExecutor.execute(this::connectToUpbit);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down UpbitWebSocketService...");
        isShuttingDown.set(true); // 종료 플래그 설정

        // 1. 지금 연결을 닫는다 (플래그를 먼저 세워 닫힘 이벤트가 재연결하지 않는다)
        closeQuietly(session);

        // 2. 주입받은 Executor를 명시적으로 종료시킵니다.
        // TaskExecutor가 ThreadPoolTaskExecutor의 인스턴스일 경우에만 종료 가능
        if (webSocketTaskExecutor instanceof ThreadPoolTaskExecutor) {
            ((ThreadPoolTaskExecutor) webSocketTaskExecutor).shutdown();
        }

        log.info("UpbitWebSocketService has been shut down.");
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000) // 30초마다 연결 상태 체크
    public void checkConnectionHealth() {
        // 연결 중이거나 재연결을 기다리는 중이면 이미 알고 있다 (연결을 하나 더 만들거나 같은 끊김을 두 번 알리지 않음)
        if (!isShuttingDown.get() && !isConnected() && !isReconnecting && !connecting) {
            // 끊김 알림(종료·전송 오류) 없이 연결이 죽은 경우라 드물다
            log.warn("Upbit WebSocket found dead without a close event, reconnecting");
            markDisconnected();
            scheduleReconnect();
        }
    }

    /**
     * 끊김 로그 수준: 업비트와의 장기 연결은 서버 점검·네트워크로 가끔 끊기고 곧 다시 붙으므로 한 번은 INFO.
     * 재연결이 연달아 실패하면 WARN, 오래 못 붙으면 ERROR (시세가 멈춰 예약 주문이 체결되지 않는다)
     */
    private Level disconnectLevel() {
        int failures = reconnectAttempts.get();
        if (failures >= ERROR_AFTER_FAILURES) {
            return Level.ERROR;
        }
        return failures >= WARN_AFTER_FAILURES ? Level.WARN : Level.INFO;
    }

    private void markDisconnected() {
        disconnectedAt.compareAndSet(null, Instant.now());
    }

    @Scheduled(cron = "0 3 0 * * ?") // 매일 0시 3분
    public void updateSymbolList() {
        if (isShuttingDown.get()) {
            return;
        }
        List<String> activeSymbols = coinService.getActiveSymbols();
        sendSymbolRequest(activeSymbols);
    }

    private synchronized void connectToUpbit() {
        if (isShuttingDown.get()) {
            return;
        }

        try {
            // 기존 연결 정리 (새 핸들러로 바꾼 뒤라 닫힘 이벤트는 무시된다)
            WebSocketSession previous = session;
            session = null;

            StandardWebSocketClient client = new StandardWebSocketClient();
            client.setTaskExecutor((AsyncTaskExecutor) webSocketTaskExecutor);
            client.getUserProperties().put("org.apache.tomcat.websocket.IO_TIMEOUT_MS", String.valueOf(connectionTimeout));

            UpbitWebSocketHandler handler = new UpbitWebSocketHandler();
            currentHandler = handler;
            connecting = true;
            closeQuietly(previous);
            // 시도 횟수는 실제로 연결된 뒤에 초기화한다 (연결은 비동기라 여기서 초기화하면 재시도 간격이 늘지 않음)
            isReconnecting = false;

            // 연결 실패도 비동기로 온다. 예전에는 WebSocketConnectionManager가 실패를 로그로만 남겨
            // 재연결 시도가 한 번 실패하면(업비트 점검 중 등) 다시 시도하지 않고 시세가 끊긴 채로 남았다
            client.execute(handler, websocketUrl).whenComplete((connected, error) -> {
                if (error != null) {
                    handler.connectFailed(error);
                }
            });

        } catch (Exception e) {
            connecting = false;
            log.atLevel(disconnectLevel()).log("Failed to connect to Upbit WebSocket (attempt {}): {}",
                    reconnectAttempts.get(), e.toString());
            if (!isShuttingDown.get()) {
                scheduleReconnect();
            }
        }
    }

    private static void closeQuietly(WebSocketSession s) {
        if (s != null && s.isOpen()) {
            try {
                s.close();
            } catch (Exception e) {
                log.debug("Error closing Upbit WebSocket session", e);
            }
        }
    }

    public synchronized void sendSymbolRequest(List<String> symbols) {
        if (isShuttingDown.get() || !isConnected()) {
            log.warn("Cannot send symbol request - service is shutting down or WebSocket session is not available");
            return;
        }

        try {
            List<Object> request = Arrays.asList(
                    Map.of("ticket", "bitground"),
                    Map.of("type", "ticker", "codes", symbols),
                    Map.of("format", "SIMPLE")
            );

            String jsonRequest = objectMapper.writeValueAsString(request);
            session.sendMessage(new TextMessage(jsonRequest));
            log.info("Sent symbol request for {} symbols", symbols.size());

        } catch (Exception e) {
            log.error("Failed to send symbol request", e);
            if (!isShuttingDown.get()) {
                scheduleReconnect();
            }
        }
    }

    private class UpbitWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            // 종료 중이거나, 이 연결을 시작한 뒤 새 연결을 시작했으면 닫는다 (연결이 두 개 남아 시세를 두 번 처리하지 않게)
            if (isShuttingDown.get() || this != currentHandler) {
                try {
                    session.close();
                } catch (Exception e) {
                    log.debug("Error closing superseded session", e);
                }
                return;
            }

            connecting = false;
            UpbitWebSocketService.this.session = session;
            Instant downSince = disconnectedAt.getAndSet(null);
            int attempts = reconnectAttempts.getAndSet(0);
            if (downSince == null) {
                log.info("WebSocket connection established to Upbit");
            } else {
                log.info("WebSocket reconnected to Upbit after {} attempt(s), down {} s",
                        attempts, Duration.between(downSince, Instant.now()).toSeconds());
            }

            taskScheduler.schedule(() -> {
                if (!isShuttingDown.get()) {
                    List<String> activeSymbols = coinService.getActiveSymbols();
                    sendSymbolRequest(activeSymbols);
                }
            }, Instant.now().plusSeconds(1));
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            // 종료 중이면 메시지 처리하지 않음
            if (isShuttingDown.get()) {
                return;
            }

            try {
                ByteBuffer buffer = message.getPayload();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                String jsonString = new String(bytes, StandardCharsets.UTF_8);

                processTickerMessage(jsonString);

            } catch (Exception e) {
                if (!isShuttingDown.get()) {
                    log.error("Failed to process binary message", e);
                }
            }
        }

        private void processTickerMessage(String jsonString) {
            if (isShuttingDown.get()) {
                return;
            }

            try {
                JsonNode tickerData = objectMapper.readTree(jsonString);

                if (!tickerData.has("cd") || !tickerData.has("tp")) {
                    return;
                }

                String symbol = tickerData.get("cd").asText();
                BigDecimal price = tickerData.get("tp").decimalValue();
                // 체결 시각 (늦게 처리된 예전 시세가 새 시세를 덮지 않게). 없으면 받은 시각
                long tradedAt = tickerData.has("ttms") ? tickerData.get("ttms").asLong() : System.currentTimeMillis();

                if (symbol != null && !symbol.trim().isEmpty() && price.signum() > 0) {
                    priceUpdateService.updatePrice(symbol, price, tradedAt);
                }
            } catch (Exception e) {
                if (!isShuttingDown.get()) {
                    log.error("Failed to process ticker message: {}", jsonString, e);
                }
            }
        }

        /** 연결 자체가 실패함 (서버 응답 없음·업그레이드 거절·타임아웃) */
        void connectFailed(Throwable error) {
            if (this != currentHandler || isShuttingDown.get()) {
                return;
            }
            connecting = false;
            markDisconnected();
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            log.atLevel(disconnectLevel()).log("Failed to connect to Upbit WebSocket (attempt {}): {}",
                    reconnectAttempts.get(), cause.toString());
            scheduleReconnect();
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            if (this != currentHandler) {
                return; // 이미 새 연결로 바꾼 예전 연결
            }
            connecting = false;
            if (!isShuttingDown.get()) {
                // 네트워크·TLS 오류로 끊김. 곧 다시 붙으므로 끊김과 같은 수준으로 남긴다 (스택은 DEBUG)
                markDisconnected();
                log.atLevel(disconnectLevel()).log("WebSocket transport error: {}", exception.toString());
                log.debug("WebSocket transport error detail", exception);
            }
            UpbitWebSocketService.this.session = null;

            if (!isShuttingDown.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            if (this != currentHandler) {
                return; // 이미 새 연결로 바꾼 예전 연결 (닫아도 재연결하지 않음)
            }
            connecting = false;
            if (!isShuttingDown.get()) {
                markDisconnected();
                log.atLevel(disconnectLevel()).log("WebSocket connection closed: code={}, reason={}",
                        status.getCode(), status.getReason());
            }
            UpbitWebSocketService.this.session = null;

            if (!status.equals(CloseStatus.NORMAL) && !isShuttingDown.get()) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        if (isShuttingDown.get()) {
            return;
        }

        synchronized (this) {
            if (isReconnecting || isShuttingDown.get()) {
                return;
            }
            isReconnecting = true;
        }

        int currentAttempt = reconnectAttempts.getAndIncrement();

        long delay = Math.min(
                initialReconnectDelay * (1L << Math.min(currentAttempt, 6)),
                maxReconnectDelay
        );

        log.atLevel(currentAttempt == 0 ? Level.INFO : Level.DEBUG)
                .log("Scheduling reconnection attempt {} in {} ms", currentAttempt + 1, delay);

        taskScheduler.schedule(this::connectToUpbit, Instant.now().plusMillis(delay));
    }

    public boolean isConnected() {
        WebSocketSession current = session;
        return !isShuttingDown.get() && current != null && current.isOpen();
    }
}