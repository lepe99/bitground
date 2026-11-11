// 거래 수수료 0.05% (업비트 원화마켓과 같음, 시장가·지정가 동일).
// 서버(TradingFee.java)와 같은 규칙: 원 단위 거래 대금 × 0.05%를 원 단위로 올림.
// 화면은 예상값을 보여 줄 뿐이고, 실제 수수료는 서버가 체결 때 계산해 주문에 남긴다.

export const FEE_RATE_LABEL = '0.05%';

/** 원 단위 대금의 수수료. 0.0005를 곱하면 부동소수 오차로 올림이 한 칸 틀어질 수 있어 정수로 계산한다 */
export const feeOf = (krw) => {
    const won = Math.floor(Number(krw));
    if (!(won > 0)) return 0;
    return Math.floor((won * 5 + 9999) / 10000);
};

/** 매수에 드는 현금 = 대금 + 수수료 */
export const buyCost = (krw) => Math.floor(Number(krw)) + feeOf(krw);

/** 매도로 받는 현금 = 대금 − 수수료 */
export const sellProceeds = (krw) => Math.floor(Number(krw)) - feeOf(krw);

/** 이 현금으로 낼 수 있는 가장 큰 매수 대금 (대금 + 수수료 ≤ 현금) */
export const maxBuyTotal = (cash) => {
    const won = Math.floor(Number(cash));
    if (!(won > 0)) return 0;
    let total = Math.floor(won / 1.0005);
    while (total > 0 && buyCost(total) > won) total -= 1;
    while (buyCost(total + 1) <= won) total += 1;
    return total;
};
