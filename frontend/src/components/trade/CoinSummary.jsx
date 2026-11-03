import CoinIcon from '../ui/CoinIcon.jsx';
import StarIcon from '../ui/StarIcon.jsx';
import {changeColor, formatCompactKRW, formatPrice} from '../../utils/format.js';

/** 거래소 위쪽: 종목 이름 · 현재가 · 등락 · 24시간 고가/저가/거래대금 */
export default function CoinSummary({market, name, ticker, isFav, onToggleFav}) {
    const color = changeColor(ticker?.changeRate);
    const arrow = ticker?.changeAmt > 0 ? '▲' : ticker?.changeAmt < 0 ? '▼' : '';

    return (
        <section className="flex flex-wrap items-center gap-x-7 gap-y-3 rounded-xl border border-line bg-surface px-5 py-4">
            <div className="flex items-center gap-3">
                <CoinIcon symbol={market}/>
                <div className="flex flex-col">
                    <h1 className="text-[17px] font-bold">{name ?? market}</h1>
                    <span className="text-xs text-fg-3">{market?.replace('KRW-', '')}/KRW</span>
                </div>
                <button
                    type="button"
                    onClick={() => onToggleFav(market)}
                    aria-label={isFav ? '관심 코인 해제' : '관심 코인에 추가'}
                    aria-pressed={isFav}
                    className={`flex size-8 items-center justify-center rounded-lg ${isFav ? 'text-brand' : 'text-fg-3 hover:text-fg-2'}`}
                >
                    <StarIcon filled={isFav} className="size-[18px]"/>
                </button>
            </div>

            <div className="flex flex-col">
                <span className={`num text-[28px] font-extrabold leading-tight ${color}`}>{formatPrice(ticker?.price)}</span>
                <span className={`num text-[13px] font-semibold ${color}`}>
                    {ticker ? `${ticker.changeRate > 0 ? '+' : ''}${(ticker.changeRate * 100).toFixed(2)}% ${arrow}${formatPrice(Math.abs(ticker.changeAmt))}` : '—'}
                </span>
            </div>

            <dl className="grid grid-flow-col grid-rows-2 gap-x-7 gap-y-1 text-xs">
                <dt className="text-fg-3">고가</dt>
                <dd className="num font-semibold text-up">{formatPrice(ticker?.high)}</dd>
                <dt className="text-fg-3">저가</dt>
                <dd className="num font-semibold text-down">{formatPrice(ticker?.low)}</dd>
                <dt className="text-fg-3">거래대금(24H)</dt>
                <dd className="num font-semibold">{ticker ? formatCompactKRW(ticker.volume) : '—'}</dd>
            </dl>
        </section>
    );
}
