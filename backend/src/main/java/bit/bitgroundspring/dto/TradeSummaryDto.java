package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TradeSummaryDto {
    private String coin;         // 코인 심볼 (예: BTC)
    private String koreanName;  // 한글 이름( 예:비트코인)
    private String buyDate;      // 최초 매수일 (MM-DD)
    private double buyAmount;    // 총 매수 금액
    private double sellAmount;   // 총 매도 금액
    private double avgBuy;       // 평균 매수가
    private double avgSell;      // 평균 매도가
    private String returnRate;   // 수익률 (ex: +15%)
    private double profit;       // 수익 금액
}
