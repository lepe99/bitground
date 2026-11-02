package bit.bitgroundspring.handler;

import bit.bitgroundspring.dto.TradeDto;
import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.entity.OrderType;
import bit.bitgroundspring.event.OrderCreatedEvent;
import bit.bitgroundspring.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OrderHistoryWebSocketHandler
        extends TextWebSocketHandler
        implements ApplicationListener<OrderCreatedEvent> {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    /**
     * symbol → 해당 symbol 을 구독(subscribe) 중인 WebSocketSession 집합
     * 예: subscribers.get("KRW-BTC") = {session1, session2, ...}
     */
    private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 클라이언트와 연결만 맺어둡니다. 실제 구독 메시지는 handleTextMessage 에서 처리.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 세션이 닫힐 때, 모든 심볼 구독 집합에서 이 세션을 제거합니다.
        subscribers.values().forEach(set -> set.remove(session));
        // 빈 집합은 맵에서 지워도 무방
        subscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 클라이언트가 보내는 메시지(문자열)를 읽어 Map<String,String> 으로 파싱
        Map<String, String> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String action = payload.get("action");   // "subscribe" 또는 "unsubscribe"
        String symbol = payload.get("symbol");   // ex) "KRW-BTC"

        if ("subscribe".equalsIgnoreCase(action) && symbol != null) {
            // ▶ 해당 심볼 구독자 집합에 이 세션을 등록
            subscribers
                    .computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet())
                    .add(session);

            // ▶ 연결 직후, DB 에서 최근 100건(또는 원하는 개수)만큼 가져와서 초기 데이터 보내기
            List<TradeDto> recentList = orderService.getRecentTrades(symbol);
            Map<String, Object> initMsg = new HashMap<>();
            initMsg.put("type", "initial");
            initMsg.put("data", recentList);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initMsg)));
        }
        else if ("unsubscribe".equalsIgnoreCase(action) && symbol != null) {
            // ▶ 구독 해제: 해당 세션을 심볼 구독 집합에서 제거
            Set<WebSocketSession> set = subscribers.get(symbol);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    subscribers.remove(symbol);
                }
            }
        }
        // 그 외(action 잘못 들어오거나 symbol 누락 등)는 무시해도 됩니다.
    }

    @Override
    public void onApplicationEvent(OrderCreatedEvent event) {
        // 주문이 저장된 뒤에 이벤트를 발행하면 이 메서드 호출
        Order saved = event.getOrder();

        String symbol = saved.getCoin().getSymbol();
        String koreanName = saved.getCoin().getKoreanName();
        OrderType orderType = saved.getOrderType();
        Double amount = saved.getAmount();
        Double tradePrice = saved.getTradePrice();
        LocalDateTime createdAt = saved.getCreatedAt();
        LocalDateTime updatedAt = saved.getUpdatedAt();

        Set<WebSocketSession> sessions = subscribers.get(symbol);
        if (sessions == null || sessions.isEmpty()) {
            return; // 해당 심볼을 구독 중인 세션이 없으면 끝
        }

        // DTO 로 변환 (TradeDto)
        TradeDto dto = new TradeDto(
                symbol,
                koreanName,
                orderType,
                amount,
                tradePrice,
                createdAt,
                updatedAt
        );

        Map<String, Object> updateMsg = new HashMap<>();
        updateMsg.put("type", "update");
        updateMsg.put("data", dto);

        String json;
        try {
            json = objectMapper.writeValueAsString(updateMsg);
        } catch (IOException e) {
            return;
        }
        TextMessage text = new TextMessage(json);

        // 각 WebSocketSession 에 실시간 푸시
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen()) {
                try {
                    sess.sendMessage(text);
                } catch (IOException ignored) { }
            }
        }
    }
}
