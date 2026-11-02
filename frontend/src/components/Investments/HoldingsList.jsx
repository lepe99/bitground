import { useContext, useMemo, useState } from 'react';
import { TickerContext } from '../../ticker/TickerProvider'; // 📈 실시간 시세 데이터 제공 컨텍스트

// 🔢 숫자 포맷 함수: 소수점 자리수 자동 조정 및 천단위 구분
function formatNumber(value, digits = undefined, trimZeros = true) {
    if (isNaN(value)) return '-';
    const num = Number(value);
    const fractionDigits = digits ?? (num < 1 ? 8 : 2);
    return num.toLocaleString(undefined, {
        minimumFractionDigits: trimZeros ? 0 : fractionDigits,
        maximumFractionDigits: fractionDigits,
    });
}

export default function HoldingsList({ userAssets = [] }) {
    const { tickerMap, markets } = useContext(TickerContext); // 실시간 가격 + 코인 시장 정보

    // 📊 정렬 상태 관리
    const [sortKey, setSortKey] = useState('evaluation'); // 초기 정렬 기준: 평가금액
    const [sortOrder, setSortOrder] = useState('desc');   // 초기 정렬 방향: 내림차순

    // 🔀 정렬 버튼 클릭 핸들러
    const onSort = (key) => {
        if (sortKey === key) {
            setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'));
        } else {
            setSortKey(key);
            setSortOrder('desc');
        }
    };

    // 📦 보유 자산 계산 및 정렬
    const processedHoldings = useMemo(() => {
        const result = userAssets.map(asset => {
            const symbol = asset.symbol;
            const symbolShort = symbol.replace('KRW-', ''); // 마켓명 축약 (KRW-BTC → BTC)
            const coinName = asset.coinName ?? markets.find(m => m.market === symbol)?.name ?? symbolShort;
            const quantity = Number(asset.amount);
            const avgPrice = Number(asset.avgPrice);
            const currentPrice = tickerMap[symbol]?.price ?? 0;

            const evaluation = quantity * currentPrice;  // 평가금액
            const buyAmount = quantity * avgPrice;       // 매수금액
            const profitAmount = evaluation - buyAmount; // 손익
            const profitRate = buyAmount !== 0
                ? ((profitAmount / buyAmount) * 100).toFixed(2)
                : '0.00';
            const isPositive = profitAmount >= 0;

            return {
                coinName,
                symbol,
                symbolShort,
                quantity,
                avgPrice,
                currentPrice,
                buyAmount,
                evaluation,
                profitAmount,
                profitRate,
                isPositive,
            };
        });

        // 📌 정렬 기준에 따라 배열 정렬
        result.sort((a, b) => {
            let va = a[sortKey];
            let vb = b[sortKey];
            if (sortKey === 'coinName') {
                return va.localeCompare(vb) * (sortOrder === 'asc' ? 1 : -1);
            }
            return (vb - va) * (sortOrder === 'asc' ? -1 : 1);
        });

        return result;
    }, [userAssets, tickerMap, sortKey, sortOrder]);

    return (
        <div className="holdings-list">
            {/* 🔰 타이틀 영역 */}
            <div className="holdings-header">
                <h3>보유자산 목록</h3>
            </div>

            {/* 📋 보유자산 테이블 */}
            <div className="holdings-table">
                {/* 📌 헤더: 정렬 가능 컬럼 표시 */}
                <div className="table-header">
                    <div className="col" onClick={() => onSort('coinName')}>
                        보유자산 {sortKey === 'coinName' && (sortOrder === 'asc' ? '▲' : '▼')}
                    </div>
                    <div className="col">보유수량</div>
                    <div className="col">매수평균가 <small>KRW</small></div>
                    <div className="col profit-info" onClick={() => onSort('buyAmount')}>
                        매수금액 <small>KRW</small>{sortKey === 'buyAmount' && (sortOrder === 'asc' ? '▲' : '▼')}
                    </div>
                    <div className="col profit-info" onClick={() => onSort('evaluation')}>
                        평가금액 <small>KRW</small>{sortKey === 'evaluation' && (sortOrder === 'asc' ? '▲' : '▼')}
                    </div>
                    <div className="col profit-info" onClick={() => onSort('profitAmount')}>
                        평가손익 {sortKey === 'profitAmount' && (sortOrder === 'asc' ? '▲' : '▼')}
                    </div>
                </div>

                {/* 📌 자산이 없을 때 메시지 */}
                {userAssets.length === 0 ? (
                    <div className="table-row">
                        <div className="col trade-no-data">보유 자산이 없습니다.</div>
                    </div>
                ) : (
                    // 📦 자산 목록 출력
                    processedHoldings.map((item, index) => (
                        <div key={index} className="table-row">
                            {/* 코인 이름 및 심볼 */}
                            <div className="col coin-info">
                                <div>
                                    <div className="coin-name">{item.coinName}</div>
                                    <div className="coin-symbol">{item.symbolShort}</div>
                                </div>
                            </div>
                            <div className="col">{formatNumber(item.quantity, 10)}</div>
                            <div className="col">{formatNumber(item.avgPrice, 8)}</div>
                            <div className="col profit-info">{formatNumber(item.buyAmount)}</div>
                            <div className="col profit-info">{formatNumber(item.evaluation, 0)}</div>

                            {/* 🔺 손익률 및 손익금액 (색상 조건부 처리) */}
                            <div className="col profit-info">
                                <div className={`profit-rate ${item.isPositive ? 'positive' : 'negative'}`}>
                                    {item.profitRate} %
                                </div>
                                <div className={`profit-amount ${item.isPositive ? 'positive' : 'negative'}`}>
                                    {formatNumber(item.profitAmount, 0)} <small>KRW</small>
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}
