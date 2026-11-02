package bit.bitgroundspring.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderDto {
    private Integer id;
    private String symbol;
    private String koreanName;
    private BigDecimal amount;
    private BigDecimal tradePrice;
    private BigDecimal reservePrice;        // 감시 가격 (예약 주문 핵심)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String orderType;
}
