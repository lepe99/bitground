package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class ReserveOrderDto {
    private Integer id;
    private String coinName;
    private String symbol;
    private Double amount;
    private Double reservePrice;
    private Double tradePrice;
    private LocalDateTime orderTime;
    private Double remainingQuantity;
    private String orderType;

    public static ReserveOrderDto from(Order order) {
        return ReserveOrderDto.builder()
                .id(order.getId())
                .coinName(order.getCoin().getKoreanName())
                .symbol(order.getCoin().getSymbol())
                .amount(order.getAmount())
                .reservePrice(order.getReservePrice() != null ? (double) order.getReservePrice() : null)
                .tradePrice(order.getTradePrice())
                .orderTime(order.getCreatedAt())
                .remainingQuantity(order.getAmount()) // 체결 수량 없다면 전체 = 남은 수량
                .orderType(order.getOrderType().name().toLowerCase())
                .build();
    }
}
