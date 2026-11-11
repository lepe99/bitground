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
public class UserAssetResponse {
    private Long cash;
    private List<UserAssetProjection> userAssets;
}