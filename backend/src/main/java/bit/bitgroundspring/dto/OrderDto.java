package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderDto {
    private Integer id;
    private String symbol;
    private String koreanName;
    private Double amount;
    private Double tradePrice;
    private Float reservePrice;        // 감시 가격 (예약 주문 핵심)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String orderType;
}
