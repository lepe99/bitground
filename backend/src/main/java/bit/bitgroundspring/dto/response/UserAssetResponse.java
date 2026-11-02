package bit.bitgroundspring.dto.response;

import bit.bitgroundspring.dto.projection.UserAssetProjection;
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
    private Integer cash;
    private List<UserAssetProjection> userAssets;
}