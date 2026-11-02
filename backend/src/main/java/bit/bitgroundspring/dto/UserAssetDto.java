package bit.bitgroundspring.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAssetDto {
    private String symbol;
    private Double amount;
    private Double avgPrice;
}
