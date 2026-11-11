package bit.bitgroundspring.order.dto;

import bit.bitgroundspring.order.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderResponseDto {
    private Integer orderId;
    private String symbol;
    private String koreanName;
    private OrderType orderType;
    private BigDecimal amount;
    private BigDecimal tradePrice;
    private Long fee;           // 낸 거래 수수료 (원)
    private LocalDateTime createdAt;
}
