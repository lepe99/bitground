package bit.bitgroundspring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradeDetailDto {
    private String date;     // ex. 06-10
    private String type;     // 매수 / 매도
    private String qty;      // 0.08 BTC
    private double price;    // 단가
    private double total;    // 금액
    private String koreanName;
}