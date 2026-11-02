package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TradeDto {
    private String symbol;
    private String koreanName;
    private OrderType orderType;
    private BigDecimal amount;
    private BigDecimal tradePrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
