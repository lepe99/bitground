package bit.bitgroundspring.repository;

import java.time.LocalDateTime;

public interface PendingOrderProjection {
    Long getId();
    String getCoin();            // 코인 한글 이름
    String getSymbol();          // BTC, ETH
    String getOrderType();       // BUY / SELL
    Double getQuantity();        // 주문 수량
    Double getWatchPrice();      // 감시 가격 (reservePrice)
    Double getTradePrice();      // 실제 체결가 (null 가능)
    LocalDateTime getOrderTime(); // 주문 생성 시각
    Double getRemainingQuantity(); // 잔여 수량 (지금은 amount)
}
