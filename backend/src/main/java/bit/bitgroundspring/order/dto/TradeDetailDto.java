package bit.bitgroundspring.order.dto;

import bit.bitgroundspring.order.OrderType;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TradeDetailDto {
    private LocalDateTime createdAt; // 주문 시각
    private OrderType orderType;     // BUY / SELL
    private String symbol;           // KRW-BTC
    private BigDecimal amount;       // 수량
    private BigDecimal price; // 단가
    private long total;       // 금액 (원)
    private String koreanName;
}