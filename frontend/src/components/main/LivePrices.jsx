import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import Sparkline from '../ui/Sparkline.jsx';
import {useUpbitTickers} from '../../hooks/useUpbitTickers.js';
import {fetchHourlyCandles} from '../../api/upbitApi.js';
import {changeColor, formatKRW, formatPercent} from '../../utils/format.js';

// 메인에 보여줄 대표 종목
const COINS = [
    {market: 'KRW-BTC', name: '비트코인'},
    {market: 'KRW-ETH', name: '이더리움'},
    {market: 'KRW-XRP', name: '리플'},
    {market: 'KRW-SOL', name: '솔라나'},
    {market: 'KRW-DOGE', name: '도지코인'},
];
const MARKETS = COINS.map((c) => c.market);

/** 대표 종목 실시간 시세 + 24시간 추이 */
export default function LivePrices() {
    const tickers = useUpbitTickers(MARKETS);
    const [sparks, setSparks] = useState({});

    useEffect(() => {
        Promise.all(
            MARKETS.map((market) =>
                fetchHourlyCandles(market, 24)
                    .then((candles) => [market, candles.map((c) => c.trade_price).reverse()])
                    .catch(() => [market, []]),
            ),
        ).then((entries) => setSparks(Object.fromEntries(entries)));
    }, []);

    return (
        <Card className="flex flex-col p-5 md:p-6">
            <div className="flex items-center justify-between pb-3">
                <h2 className="text-base font-bold md:text-[17px]">실시간 시세</h2>
                <span className="flex items-center gap-1.5 text-xs text-fg-3">
                    <span className="size-2 rounded-full bg-[#16A34A]" aria-hidden="true"/>
                    업비트 KRW 마켓
                </span>
            </div>
            <ul>
                {COINS.map((coin) => {
                    const t = tickers[coin.market];
                    return (
                        <li key={coin.market} className="border-t border-line first:border-t-0 md:first:border-t">
                            <Link
                                to={`/trade?market=${coin.market}`}
                                className="grid grid-cols-[36px_1fr_auto_64px] items-center gap-3 py-3 md:grid-cols-[36px_1fr_120px_130px_76px]"
                            >
                                <CoinIcon symbol={coin.market}/>
                                <span className="flex min-w-0 flex-col">
                                    <span className="text-[15px] font-semibold">{coin.name}</span>
                                    <span className="text-xs text-fg-3">{coin.market}</span>
                                </span>
                                <Sparkline values={sparks[coin.market]} className={`hidden md:block ${changeColor(t?.changeRate)}`}/>
                                <span className="num text-right text-[15px] font-semibold">{t ? formatKRW(t.price) : '—'}</span>
                                <span className={`num text-right text-sm font-semibold ${changeColor(t?.changeRate)}`}>
                                    {t ? formatPercent(t.changeRate) : '—'}
                                </span>
                            </Link>
                        </li>
                    );
                })}
            </ul>
        </Card>
    );
}
