/**
 * 보유 자산 + 실시간 시세로 평가 금액·손익을 계산한다.
 * @param userAssets [{symbol, amount, avgPrice}]
 * @param tickerMap  {symbol: {price}}
 * @param names      {symbol: 한글명}
 * @returns {{rows, cash, buyTotal, evalTotal, pnl, pnlRate, total}}
 */
export function buildPortfolio(userAssets = [], tickerMap = {}, names = {}, cash = 0) {
    const rows = userAssets
        .map((asset) => {
            const symbol = asset.symbol.includes('KRW-') ? asset.symbol : `KRW-${asset.symbol}`;
            const amount = Number(asset.amount) || 0;
            const avgPrice = Number(asset.avgPrice) || 0;
            const price = tickerMap[symbol]?.price;
            const buy = amount * avgPrice;
            // 시세가 아직 안 왔으면 매수 금액으로 둔다 (0원으로 보이며 손실처럼 보이지 않게)
            const value = price != null ? amount * price : buy;
            const pnl = value - buy;
            return {symbol, name: names[symbol] ?? symbol.replace('KRW-', ''), amount, avgPrice, price, buy, value, pnl, rate: buy ? (pnl / buy) * 100 : 0};
        })
        .sort((a, b) => b.value - a.value);

    const buyTotal = rows.reduce((s, r) => s + r.buy, 0);
    const evalTotal = rows.reduce((s, r) => s + r.value, 0);
    const pnl = evalTotal - buyTotal;
    return {
        rows,
        cash: Number(cash) || 0,
        buyTotal,
        evalTotal,
        pnl,
        pnlRate: buyTotal ? (pnl / buyTotal) * 100 : 0,
        total: (Number(cash) || 0) + evalTotal,
    };
}
