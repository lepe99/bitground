package bit.bitgroundspring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class UpbitWebSocketService {
    
    private final CoinService coinService;
    private final PriceUpdateService priceUpdateService;
    private final RedisTemplate<String, Object> redisTemplate;
    
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
    private WebSocketConnectionManager connectionManager;
    
    // 재연결 관련 설정
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile boolean isReconnecting = false;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    public UpbitWebSocketService(
            CoinService coinService,
            PriceUpdateService priceUpdateService,
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("webSocketTaskExecutor") Executor webSocketTaskExecutor,
            TaskScheduler taskScheduler) {
        this.coinService = coinService;
        this.priceUpdateService = priceUpdateService;
        this.redisTemplate = redisTemplate;
        this.webSocketTaskExecutor = webSocketTaskExecutor;
        this.taskScheduler = taskScheduler;
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        webSocketTaskExecutor.execute(this::connectToUpbit);
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down UpbitWebSocketService...");
        isShuttingDown.set(true); // 종료 플래그 설정
        
        // 1. ConnectionManager를 먼저 중지시켜 재연결 로직을 차단합니다.
        if (connectionManager != null) {
            connectionManager.stop();
        }
        
        // 2. 주입받은 Executor를 명시적으로 종료시킵니다.
        // TaskExecutor가 ThreadPoolTaskExecutor의 인스턴스일 경우에만 종료 가능
        if (webSocketTaskExecutor instanceof ThreadPoolTaskExecutor) {
            ((ThreadPoolTaskExecutor) webSocketTaskExecutor).shutdown();
        }
        
        log.info("UpbitWebSocketService has been shut down.");
    }
    
    @Scheduled(fixedDelay = 30000) // 30초마다 연결 상태 체크
    public void checkConnectionHealth() {
        if (!isShuttingDown.get() && !isConnected()) {
            log.warn("WebSocket connection is not healthy, attempting reconnection");
            scheduleReconnect();
        }
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
            // 기존 연결 정리
            if (connectionManager != null) {
                try {
                    connectionManager.stop();
                } catch (Exception e) {
                    log.warn("Error stopping previous connection manager", e);
                }
            }
            
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.setTaskExecutor((AsyncTaskExecutor) webSocketTaskExecutor);
            client.getUserProperties().put("org.apache.tomcat.websocket.IO_TIMEOUT_MS", String.valueOf(connectionTimeout));
            
            UpbitWebSocketHandler handler = new UpbitWebSocketHandler();
            connectionManager = new WebSocketConnectionManager(client, handler, websocketUrl);
            connectionManager.setAutoStartup(false);
            connectionManager.start();
            
            reconnectAttempts.set(0);
            isReconnecting = false;
            
            log.info("WebSocketConnectionManager started for Upbit WebSocket");
            
        } catch (Exception e) {
            log.error("Failed to connect to Upbit WebSocket (attempt {})",
                    reconnectAttempts.get() + 1, e);
            if (!isShuttingDown.get()) {
                scheduleReconnect();
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
            if (isShuttingDown.get()) {
                try {
                    session.close();
                } catch (Exception e) {
                    log.warn("Error closing session during shutdown", e);
                }
                return;
            }
            
            UpbitWebSocketService.this.session = session;
            log.info("WebSocket connection established to Upbit");
            
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
                double price = tickerData.get("tp").asDouble();
                
                if (symbol != null && !symbol.trim().isEmpty() && price > 0) {
                    // 변경: 개선된 updatePrice 메서드 호출
                    priceUpdateService.updatePrice(symbol, price);
                }
            } catch (Exception e) {
                if (!isShuttingDown.get()) {
                    log.error("Failed to process ticker message: {}", jsonString, e);
                }
            }
        }
        
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            if (!isShuttingDown.get()) {
                log.error("WebSocket transport error", exception);
            }
            UpbitWebSocketService.this.session = null;
            
            if (!isShuttingDown.get()) {
                scheduleReconnect();
            }
        }
        
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            if (!isShuttingDown.get()) {
                log.warn("WebSocket connection closed: code={}, reason={}",
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
        
        log.info("Scheduling reconnection attempt {} in {} ms", currentAttempt + 1, delay);
        
        taskScheduler.schedule(this::connectToUpbit, Instant.now().plusMillis(delay));
    }
    
    public boolean isConnected() {
        return !isShuttingDown.get() && session != null && session.isOpen() &&
                connectionManager != null && connectionManager.isRunning();
    }
}