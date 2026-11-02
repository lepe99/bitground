package bit.bitgroundspring.dto.request;

import bit.bitgroundspring.entity.OrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    @NotNull
    private String symbol;

    @NotNull
    private OrderType orderType;
    
    @NotNull
    @Positive
    private Double amount;
    
    @NotNull
    @Positive
    private Double reservePrice;
}