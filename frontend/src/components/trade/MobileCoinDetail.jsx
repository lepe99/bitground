import {useState} from 'react';
import {Link} from 'react-router-dom';
import StarIcon from '../ui/StarIcon.jsx';
import Button from '../ui/Button.jsx';
import TradingViewWidget from './TradingViewWidget.jsx';
import OrderPanel from './OrderPanel.jsx';
import Sheet from '../ui/Sheet.jsx';
import {changeColor, formatAmount, formatKRW, formatPercent, formatPrice, formatSignedKRW} from '../../utils/format.js';

/** 모바일 코인 상세 (토스증권식): 현재가 · 차트 · 내 보유 · 아래 고정 구매/판매 버튼 */
export default function MobileCoinDetail({market, name, ticker, isFav, onToggleFav, cash, holding, onOrderPlaced}) {
    const [sheetSide, setSheetSide] = useState(null);
    const color = changeColor(ticker?.changeRate);
    const symbol = market.split('-')[1];

    const amount = Number(holding?.amount ?? 0);
    const avgPrice = Number(holding?.avgPrice ?? 0);
    const value = ticker ? amount * ticker.price : 0;
    const pnl = value - amount * avgPrice;
    const pnlRate = avgPrice ? (pnl / (amount * avgPrice)) * 100 : 0;

    return (
        <div className="flex flex-col pb-24">
            <div className="flex h-14 items-center justify-between px-2">
                <Link to="/trade" aria-label="코인 목록으로" className="flex size-11 items-center justify-center">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>
                </Link>
                <h1 className="text-base font-bold">{name ?? symbol}</h1>
                <button
                    type="button"
                    onClick={() => onToggleFav(market)}
                    aria-label={isFav ? '관심 코인 해제' : '관심 코인에 추가'}
                    aria-pressed={isFav}
                    className={`flex size-11 items-center justify-center ${isFav ? 'text-brand' : 'text-fg-3'}`}
                >
                    <StarIcon filled={isFav} className="size-[22px]"/>
                </button>
            </div>

            <section className="flex flex-col gap-1 px-5 pt-2">
                <span className="text-sm text-fg-3">{symbol} · 업비트 실시간</span>
                <span className="num text-[32px] font-extrabold tracking-[-0.02em]">{formatPrice(ticker?.price)}원</span>
                <span className={`num text-[15px] font-semibold ${color}`}>
                    {ticker ? `어제보다 ${ticker.changeAmt > 0 ? '+' : ''}${formatPrice(ticker.changeAmt)}원 (${formatPercent(ticker.changeRate * 100)})` : '—'}
                </span>
            </section>

            <div className="mt-4">
                <TradingViewWidget market={market} height={300} compact/>
            </div>

            <section className="mx-5 mt-4 rounded-[20px] bg-surface px-5 py-1.5">
                {amount > 0 ? (
                    <dl className="divide-y divide-line">
                        {[
                            ['보유 수량', `${formatAmount(amount)} ${symbol}`],
                            ['평균 매수가', `${formatPrice(avgPrice)}원`],
                            ['평가 금액', `${formatKRW(value)}원`],
                            ['평가 손익', `${formatSignedKRW(pnl)}원 (${formatPercent(pnlRate)})`, changeColor(pnl)],
                        ].map(([label, text, tone]) => (
                            <div key={label} className="flex items-center justify-between py-3">
                                <dt className="text-sm text-fg-2">{label}</dt>
                                <dd className={`num text-[15px] font-semibold ${tone ?? ''}`}>{text}</dd>
                            </div>
                        ))}
                    </dl>
                ) : (
                    <p className="py-4 text-center text-sm text-fg-3">아직 보유하지 않은 코인이에요. 주문 가능 {formatKRW(cash)}원</p>
                )}
            </section>

            <div className="fixed inset-x-0 bottom-[calc(56px+env(safe-area-inset-bottom))] z-30 grid grid-cols-2 gap-2.5 bg-bg px-5 pt-3 pb-3">
                <Button variant="sell" size="lg" onClick={() => setSheetSide('SELL')} disabled={amount <= 0}>판매하기</Button>
                <Button variant="buy" size="lg" onClick={() => setSheetSide('BUY')}>구매하기</Button>
            </div>

            <Sheet open={sheetSide != null} onClose={() => setSheetSide(null)} title={`${name ?? symbol} ${sheetSide === 'SELL' ? '판매' : '구매'}`}>
                <OrderPanel
                    market={market}
                    currentPrice={ticker?.price ?? 0}
                    cash={cash}
                    holdings={amount}
                    initialSide={sheetSide ?? 'BUY'}
                    onOrderPlaced={(result) => {
                        onOrderPlaced(result);
                        setSheetSide(null);
                    }}
                />
            </Sheet>
        </div>
    );
}
