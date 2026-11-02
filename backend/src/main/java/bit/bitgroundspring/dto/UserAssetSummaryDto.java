package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserAssetSummaryDto {
    private float cash;             // 보유 KRW
    private float totalAsset;       // 총 보유자산 (KRW + 평가금액)
    private float totalBuy;         // 총 매수
    private float totalEval;        // 총 평가
    private float profit;           // 평가손익
    private float profitRate;       // 수익률
    private float totalAmount;      // 수량기준 (보유 코인 수량 총합)
}
