package bit.bitgroundspring.config;

import bit.bitgroundspring.handler.OrderHistoryWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderHistoryWebSocketHandler orderHistoryWebSocketHandler;

    public WebSocketConfig(OrderHistoryWebSocketHandler handler) {
        this.orderHistoryWebSocketHandler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // "/ws/trade/history" 경로로 들어오는 클라이언트 연결을 핸들러에 매핑
        registry
                .addHandler(orderHistoryWebSocketHandler, "/ws/trade/history")
                .setAllowedOriginPatterns("https://*.bitground.kr", "https://bitground.kr", "http://localhost:5173");
    }
}
