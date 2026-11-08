package bit.bitgroundspring.order.dto;

import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.order.Order;
import bit.bitgroundspring.order.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 주문 접수 응답 (기존: Order 엔티티를 그대로 반환해 사용자 정보까지 직렬화됨)
 */
public record ReserveOrderResponse(
        Integer orderId,
        String symbol,
        OrderType orderType,
        BigDecimal amount,
        BigDecimal reservePrice,
        Status status,
        LocalDateTime createdAt
) {
    public static ReserveOrderResponse from(Order order) {
        return new ReserveOrderResponse(order.getId(), order.getCoin().getSymbol(), order.getOrderType(),
                order.getAmount(), order.getReservePrice(), order.getStatus(), order.getCreatedAt());
    }
}
