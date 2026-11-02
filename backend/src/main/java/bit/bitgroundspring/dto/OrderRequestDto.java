package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.OrderType;
import lombok.Data;

@Data
public class OrderRequestDto {
    private String symbol;
    private OrderType orderType;
    private Double amount;        // SELL 또는 reserve 용도
    private Double reservePrice;  // 예약주문일 경우만 사용
    private Integer totalPrice;    // BUY + market인 경우 사용
}