package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.OrderType;
import bit.bitgroundspring.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRedisDto { // 주문 정보를 Redis에 저장하기 위한 DTO (Jackson 직렬화/역직렬화 용도)
    private Integer id;
    private Integer userId;
    private Integer symbolId;
    private String symbol;
    private OrderType orderType;
    private Double amount;
    private Float reservePrice;
    private Status status;
    private LocalDateTime createdAt;
    
}
