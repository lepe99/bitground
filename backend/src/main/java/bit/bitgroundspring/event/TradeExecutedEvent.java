package bit.bitgroundspring.event;

import bit.bitgroundspring.dto.TradeDto;

/**
 * 시장가·예약 주문이 체결됨 (실시간 체결 내역 방송용)
 * 커밋 후 다른 스레드에서 읽으므로 엔티티가 아니라 값만 담는다.
 */
public record TradeExecutedEvent(TradeDto trade) {
}
