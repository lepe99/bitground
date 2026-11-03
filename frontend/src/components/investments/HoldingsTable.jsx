import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import {tickerOf} from '../../utils/coin.js';
import {changeColor, formatAmount, formatKRW, formatPercent, formatPrice, formatSignedKRW} from '../../utils/format.js';

const COLS = 'md:grid-cols-[minmax(0,1.4fr)_repeat(4,minmax(0,1fr))_minmax(0,1.1fr)]';

/** 보유 코인: PC는 표, 모바일은 목록. 누르면 해당 종목 거래소로 */
export default function HoldingsTable({rows}) {
    return (
        <Card className="overflow-hidden md:rounded-xl">
            <div className="flex items-center justify-between px-5 py-3.5">
                <h2 className="text-[15px] font-bold">보유 코인</h2>
                <span className="text-xs text-fg-3">업비트 현재가 기준</span>
            </div>
            <div className={`hidden gap-3 border-y border-line px-5 py-2 text-xs text-fg-3 md:grid ${COLS}`}>
                <span>보유자산</span>
                <span className="text-right">보유수량</span>
                <span className="text-right">매수평균가</span>
                <span className="text-right">매수금액</span>
                <span className="text-right">평가금액</span>
                <span className="text-right">평가손익</span>
            </div>
            {rows.length === 0 && <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3 md:border-t-0">아직 보유한 코인이 없어요.</p>}
            <ul>
                {rows.map((r) => {
                    const ticker = tickerOf(r.symbol);
                    const tone = changeColor(Math.round(r.pnl)); // 1원 미만 손익은 보합으로
                    return (
                        <li key={r.symbol} className="border-t border-line first:border-t-0 md:first:border-t-0">
                            <Link to={`/trade?market=${r.symbol}`} className={`num flex items-center gap-3 px-5 py-3 text-sm hover:bg-surface-2 md:grid ${COLS}`}>
                                <span className="flex min-w-0 flex-1 items-center gap-3">
                                    <CoinIcon symbol={r.symbol} size={36}/>
                                    <span className="flex min-w-0 flex-col">
                                        <span className="truncate text-[15px] font-semibold md:text-sm">{r.name}</span>
                                        <span className="text-xs text-fg-3">
                                            <span className="md:hidden">{formatAmount(r.amount)} {ticker} · 평균 {formatPrice(r.avgPrice)}원</span>
                                            <span className="max-md:hidden">{ticker}</span>
                                        </span>
                                    </span>
                                </span>
                                <span className="text-right max-md:hidden">{formatAmount(r.amount)}</span>
                                <span className="text-right text-fg-2 max-md:hidden">{formatPrice(r.avgPrice)}</span>
                                <span className="text-right text-fg-2 max-md:hidden">{formatKRW(r.buy)}</span>
                                <span className="text-right font-semibold max-md:hidden">{formatKRW(r.value)}</span>
                                <span className={`flex flex-col items-end ${tone}`}>
                                    <span className="text-[15px] font-semibold text-fg md:hidden">{formatKRW(r.value)}원</span>
                                    <span className="font-bold max-md:text-[13px] max-md:font-semibold">{formatPercent(r.rate)}</span>
                                    <span className="text-xs max-md:hidden">{formatSignedKRW(r.pnl)}</span>
                                </span>
                            </Link>
                        </li>
                    );
                })}
            </ul>
        </Card>
    );
}
