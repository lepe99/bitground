package bit.bitgroundspring.event;

import bit.bitgroundspring.entity.OrderType;

/**
 * 예약 주문이 체결됨 (사용자 알림용)
 */
public record ReserveOrderExecutedEvent(int orderId, int userId, String symbol, OrderType orderType,
                                        double amount, double tradePrice) {
}
