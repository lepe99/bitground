import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import Sparkline from '../ui/Sparkline.jsx';
import {useUpbitTickers} from '../../hooks/useUpbitTickers.js';
import {fetchHourlyCandles, fetchTopVolumeMarkets} from '../../api/upbitApi.js';
import {changeColor, formatKRW, formatPercent} from '../../utils/format.js';

// 비트코인은 고정, 나머지는 원화 마켓 24시간 거래대금 상위 (스테이블코인 제외)
const BITCOIN = {market: 'KRW-BTC', name: '비트코인'};
const TOP_COUNT = 4;

/** 비트코인 + 거래대금 상위 종목 실시간 시세 + 24시간 추이. 종목은 화면을 열 때 한 번 정하고 보는 동안 바꾸지 않는다 */
export default function LivePrices() {
    const [coins, setCoins] = useState([BITCOIN]);
    const markets = coins.map((c) => c.market);
    const tickers = useUpbitTickers(markets);
    const [sparks, setSparks] = useState({});

    useEffect(() => {
        fetchTopVolumeMarkets(TOP_COUNT, [BITCOIN.market])
            .then((top) => setCoins([BITCOIN, ...top]))
            .catch((error) => console.warn('거래대금 상위 조회 실패:', error.message));
    }, []);

    const marketKey = markets.join(',');
    useEffect(() => {
        Promise.all(
            marketKey.split(',').map((market) =>
                fetchHourlyCandles(market, 24)
                    .then((candles) => [market, candles.map((c) => c.trade_price).reverse()])
                    .catch(() => [market, []]),
            ),
        ).then((entries) => setSparks((prev) => ({...prev, ...Object.fromEntries(entries)})));
    }, [marketKey]);

    return (
        <Card className="@container flex flex-col p-5 md:p-6">
            <div className="flex items-center justify-between pb-3">
                <h2 className="text-base font-bold md:text-[17px]">실시간 시세</h2>
                <span className="flex items-center gap-1.5 text-xs text-fg-3">
                    <span className="size-2 rounded-full bg-[#16A34A]" aria-hidden="true"/>
                    업비트 KRW 마켓
                </span>
            </div>
            <ul>
                {coins.map((coin) => {
                    const t = tickers[coin.market];
                    return (
                        <li key={coin.market} className="border-t border-line first:border-t-0 @xl:first:border-t">
                            <Link
                                to={`/trade?market=${coin.market}`}
                                className="grid grid-cols-[36px_minmax(0,1fr)_auto] items-center gap-x-3 py-3 @xl:grid-cols-[36px_minmax(0,1fr)_120px_130px_76px]"
                            >
                                {/* 카드가 좁으면 가격 아래에 등락률, 넓으면 그래프·가격·등락률을 한 줄에 */}
                                <span className="row-span-2 @xl:row-span-1"><CoinIcon symbol={coin.market}/></span>
                                <span className="row-span-2 flex min-w-0 flex-col @xl:row-span-1">
                                    <span className="truncate text-[15px] font-semibold">{coin.name}</span>
                                    <span className="truncate text-xs text-fg-3">{coin.market}</span>
                                </span>
                                <Sparkline values={sparks[coin.market]} className={`hidden @xl:block ${changeColor(t?.changeRate)}`}/>
                                <span className="num text-right text-[15px] font-semibold whitespace-nowrap">{t ? formatKRW(t.price) : '—'}</span>
                                <span className={`num col-start-3 text-right text-sm font-semibold whitespace-nowrap @xl:col-start-auto ${changeColor(t?.changeRate)}`}>
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
