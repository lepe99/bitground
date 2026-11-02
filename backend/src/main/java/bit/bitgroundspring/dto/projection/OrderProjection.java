package bit.bitgroundspring.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderProjection {
    Integer getId();
    String getSymbol();         // coin 테이블
    String getCoinName();       // coin 테이블
    BigDecimal getAmount();         // orders 테이블
    BigDecimal getTradePrice();     // orders 테이블
    String getStatus();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    String getOrderType();      // orders 테이블
}
