package bit.bitgroundspring.config;

import bit.bitgroundspring.handler.OrderHistoryWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderHistoryWebSocketHandler orderHistoryWebSocketHandler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(OrderHistoryWebSocketHandler handler,
                           @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.orderHistoryWebSocketHandler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // "/ws/trade/history" 경로로 들어오는 클라이언트 연결을 핸들러에 매핑
        // 허용 주소는 CORS와 같은 설정을 쓴다 (기존: *.bitground.kr, localhost 하드코딩)
        registry
                .addHandler(orderHistoryWebSocketHandler, "/ws/trade/history")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
