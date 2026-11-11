import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {fetchTop5HighTradePriceCoins, fetchTop5PriceDecreaseCoins, fetchTop5PriceIncreaseCoins} from '../../api/coinApi.js';
import {useUpbitTickers} from '../../hooks/useUpbitTickers.js';
import {changeColor, formatNumber, formatPercent} from '../../utils/format.js';

const TABS = [
    {value: 'volume', label: '거래대금', load: fetchTop5HighTradePriceCoins},
    {value: 'rise', label: '상승', load: fetchTop5PriceIncreaseCoins},
    {value: 'fall', label: '하락', load: fetchTop5PriceDecreaseCoins},
];

// 원 → '4,812억'
const toEok = (won) => `${formatNumber(Math.round(Number(won ?? 0) / 1e8))}억`;

/** 거래대금·상승·하락 상위 5종목. 순위는 서버 집계를 따르고, 거래대금·등락률은 실시간 시세로 갱신한다 */
export default function TopCoins() {
    const [tab, setTab] = useState('volume');
    const [coins, setCoins] = useState(null); // null: 불러오는 중
    const live = useUpbitTickers((coins ?? []).map((c) => c.symbol));

    useEffect(() => {
        let alive = true;
        setCoins(null);
        TABS.find((t) => t.value === tab).load().then((list) => {
            if (alive) setCoins(list ?? []);
        });
        return () => {
            alive = false;
        };
    }, [tab]);

    return (
        <Card className="flex flex-col gap-2 p-5 md:p-6">
            <div className="flex items-center justify-between gap-2 pb-2">
                <h2 className="text-base font-bold md:text-[17px]">TOP 5</h2>
                <SegmentedTabs label="TOP 5 기준" items={TABS} value={tab} onChange={setTab} className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"/>
            </div>
            {coins === null && <p className="py-6 text-center text-sm text-fg-3">불러오는 중…</p>}
            {coins?.length === 0 && <p className="py-6 text-center text-sm text-fg-3">표시할 종목이 없어요.</p>}
            <ol>
                {coins?.map((coin, i) => {
                    const t = live[coin.symbol];
                    const changeRate = t?.changeRate ?? coin.changeRate;
                    return (
                        <li key={coin.symbol}>
                            <Link to={`/trade?market=${coin.symbol}`} className="flex items-center gap-3 py-2">
                                <span className="num w-4 text-[13px] font-bold text-fg-3">{i + 1}</span>
                                <span className="flex-1 truncate text-sm font-semibold">{coin.koreanName}</span>
                                <span className="num text-[13px] text-fg-2">{toEok(t?.volume ?? coin.tradePrice24h)}</span>
                                <span className={`num w-16 text-right text-[13px] font-semibold ${changeColor(changeRate)}`}>
                                    {formatPercent(changeRate)}
                                </span>
                            </Link>
                        </li>
                    );
                })}
            </ol>
        </Card>
    );
}
