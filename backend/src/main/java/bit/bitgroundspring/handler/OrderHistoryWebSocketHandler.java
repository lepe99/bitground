package bit.bitgroundspring.handler;

import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 종목별 실시간 체결 내역 WebSocket (/ws/trades)
 * 방송은 체결이 커밋된 뒤 이벤트 스트림을 거쳐, 구독자가 연결된 서버에서 실행된다({@link bit.bitgroundspring.event.RealtimeListener}).
 * WebSocketSession.sendMessage는 스레드 안전하지 않으므로 세션을 ConcurrentWebSocketSessionDecorator로 감싼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    /** 세션 ID → 동시 전송이 안전하도록 감싼 세션 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * symbol → 해당 symbol 을 구독(subscribe) 중인 세션 ID 집합
     * 예: subscribers.get("KRW-BTC") = {sessionId1, sessionId2, ...}
     */
    private final Map<String, Set<String>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscribers.values().forEach(set -> set.remove(session.getId()));
        subscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 클라이언트가 보내는 메시지(문자열)를 읽어 Map<String,String> 으로 파싱
        Map<String, String> payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {
        });
        String action = payload.get("action");   // "subscribe" 또는 "unsubscribe"
        String symbol = payload.get("symbol");   // ex) "KRW-BTC"
        WebSocketSession target = sessions.getOrDefault(session.getId(), session);

        if ("subscribe".equalsIgnoreCase(action) && symbol != null) {
            subscribers.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session.getId());

            // 구독 직후 최근 체결 내역을 초기 데이터로 보낸다
            List<TradeDto> recentList = orderService.getRecentTrades(symbol);
            Map<String, Object> initMsg = new HashMap<>();
            initMsg.put("type", "initial");
            initMsg.put("data", recentList);
            target.sendMessage(new TextMessage(objectMapper.writeValueAsString(initMsg)));
        } else if ("unsubscribe".equalsIgnoreCase(action) && symbol != null) {
            Set<String> set = subscribers.get(symbol);
            if (set != null) {
                set.remove(session.getId());
                if (set.isEmpty()) {
                    subscribers.remove(symbol);
                }
            }
        }
        // 그 외(action 잘못 들어오거나 symbol 누락 등)는 무시
    }

    /** 체결 내역을 이 서버에 연결된 해당 종목 구독자에게 보낸다 (RealtimeListener가 호출) */
    public void broadcast(TradeDto trade) {
        Set<String> sessionIds = subscribers.get(trade.getSymbol());
        if (sessionIds == null || sessionIds.isEmpty()) {
            return; // 해당 심볼을 구독 중인 세션이 없으면 끝
        }

        TextMessage text;
        try {
            text = new TextMessage(objectMapper.writeValueAsString(Map.of("type", "update", "data", trade)));
        } catch (IOException e) {
            log.error("Failed to serialize trade update", e);
            return;
        }

        for (String sessionId : sessionIds) {
            WebSocketSession session = sessions.get(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(text);
            } catch (IOException | SessionLimitExceededException e) {
                // 전송이 밀린 느린 클라이언트는 데코레이터가 세션을 닫는다
                log.debug("Failed to send trade update to session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}
