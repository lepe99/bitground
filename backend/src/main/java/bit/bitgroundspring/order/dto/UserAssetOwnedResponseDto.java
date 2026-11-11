package bit.bitgroundspring.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAssetOwnedResponseDto {
    private Long cash;
    private List<UserAssetProjection> userAssets;
    private Long availableCash;
    private Long seasonFee;     // 이번 시즌에 낸 거래 수수료 합계 (원)
}