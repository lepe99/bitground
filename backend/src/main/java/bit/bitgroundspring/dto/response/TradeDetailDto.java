package bit.bitgroundspring.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TradeDetailDto {
    private String date;     // ex. 06-10
    private String type;     // 매수 / 매도
    private String qty;      // 0.08 BTC
    private BigDecimal price; // 단가
    private long total;       // 금액 (원)
    private String koreanName;
}