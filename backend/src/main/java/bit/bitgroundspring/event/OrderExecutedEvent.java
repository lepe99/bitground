package bit.bitgroundspring.event;

import bit.bitgroundspring.market.Coin;
import bit.bitgroundspring.order.Order;
import bit.bitgroundspring.order.OrderType;
import bit.bitgroundspring.order.dto.TradeDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문이 체결됨 (시장가·예약 모두). 아웃박스에 남겨 커밋 뒤 이벤트 스트림으로 전달된다.
 * 받는 쪽: 체결 내역 실시간 방송, 예약 주문이면 체결 알림 저장·전송
 *
 * @param reserved 예약 주문 체결이면 true (알림 대상)
 */
public record OrderExecutedEvent(int orderId, int userId, String symbol, String koreanName, OrderType orderType,
                                 BigDecimal amount, BigDecimal tradePrice, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, boolean reserved) {

    public static final String TYPE = "ORDER_EXECUTED";

    public static OrderExecutedEvent of(Order order, boolean reserved) {
        Coin coin = order.getCoin();
        return new OrderExecutedEvent(order.getId(), order.getUser().getId(), coin.getSymbol(), coin.getKoreanName(),
                order.getOrderType(), order.getAmount(), order.getTradePrice(), order.getCreatedAt(),
                order.getUpdatedAt(), reserved);
    }

    public TradeDto toTrade() {
        return new TradeDto(symbol, koreanName, orderType, amount, tradePrice, createdAt, updatedAt);
    }
}
