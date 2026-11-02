package bit.bitgroundspring.dto.projection;

import java.math.BigDecimal;

public interface UserAssetProjection {
    String getSymbol();
    BigDecimal getAmount();
    BigDecimal getAvgPrice();
}