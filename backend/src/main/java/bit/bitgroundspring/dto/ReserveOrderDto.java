package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class ReserveOrderDto {
    private Integer id;
    private String coinName;
    private String symbol;
    private BigDecimal amount;
    private BigDecimal reservePrice;
    private BigDecimal tradePrice;
    private LocalDateTime orderTime;
    private BigDecimal remainingQuantity;
    private String orderType;

    public static ReserveOrderDto from(Order order) {
        return ReserveOrderDto.builder()
                .id(order.getId())
                .coinName(order.getCoin().getKoreanName())
                .symbol(order.getCoin().getSymbol())
                .amount(order.getAmount())
                .reservePrice(order.getReservePrice())
                .tradePrice(order.getTradePrice())
                .orderTime(order.getCreatedAt())
                .remainingQuantity(order.getAmount()) // 체결 수량 없다면 전체 = 남은 수량
                .orderType(order.getOrderType().name().toLowerCase())
                .build();
    }
}
