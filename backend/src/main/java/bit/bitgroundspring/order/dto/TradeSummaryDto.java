package bit.bitgroundspring.order.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TradeSummaryDto {
    private String coin;         // 코인 심볼 (예: BTC)
    private String koreanName;  // 한글 이름( 예:비트코인)
    private LocalDateTime firstBuyAt; // 최초 매수 시각 (매수 없이 매도만 있으면 null)
    private double buyAmount;    // 총 매수 금액
    private double sellAmount;   // 총 매도 금액
    private double avgBuy;       // 평균 매수가
    private double avgSell;      // 평균 매도가
    private double returnRate;   // 수익률 % (15.2 = +15.2%). 표시 형식은 화면이 정한다
    private double profit;       // 수익 금액 (매도 − 매수 − 수수료)
    private long fee;            // 낸 거래 수수료 합계 (원)
}
