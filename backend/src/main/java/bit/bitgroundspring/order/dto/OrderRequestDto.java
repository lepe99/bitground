package bit.bitgroundspring.order.dto;

import bit.bitgroundspring.order.OrderType;
import lombok.Data;

@Data
public class OrderRequestDto {
    private String symbol;
    private OrderType orderType;
    private Double amount;        // 매도 수량
    private Double reservePrice;  // 받지 않음: 보내면 거절한다 (지정가는 예약 주문 API)
    private Long totalPrice;      // 매수 금액(원)
}