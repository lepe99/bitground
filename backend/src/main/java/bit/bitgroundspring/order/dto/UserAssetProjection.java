package bit.bitgroundspring.order.dto;

import java.math.BigDecimal;

public interface UserAssetProjection {
    String getSymbol();
    BigDecimal getAmount();
    BigDecimal getAvgPrice();
}