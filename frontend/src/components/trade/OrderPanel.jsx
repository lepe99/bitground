import {useEffect, useState} from 'react';
import {useCurrentSeason} from '../../hooks/useCurrentSeason.js';
import {placeOrder, placeReserveOrder} from '../../api/orderApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {useToast} from '../toastContext.js';
import Button from '../ui/Button.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {formatAmount, formatKRW} from '../../utils/format.js';
import {buyCost, feeOf, FEE_RATE_LABEL, maxBuyTotal, sellProceeds} from '../../utils/tradingFee.js';
import OrderHistory from './OrderHistory.jsx';

const MODES = [
    {value: 'market', label: '시장가'},
    {value: 'reserve', label: '지정가'}, // 서버·API 이름은 예약 주문(/orders/reserve)
];
const PERCENTS = [10, 25, 50, 100];
const SIDES = [
    ['BUY', '매수', 'border-up text-up'],
    ['SELL', '매도', 'border-down text-down'],
    ['HISTORY', '거래내역', 'border-fg text-fg'],
];

// '1,234.5' → 1234.5 (입력이 비었거나 숫자가 아니면 NaN)
const toNumber = (text) => parseFloat(String(text ?? '').replace(/,/g, ''));

// 입력 중에는 쉼표 없이 두고, 칸을 벗어나면 쉼표를 넣는다
const display = (value) => {
    const n = typeof value === 'number' ? value : toNumber(value);
    if (Number.isNaN(n)) return '';
    return n.toLocaleString('en-US', {maximumFractionDigits: 10});
};

const DECIMAL = /^(\d+\.?\d*|\.\d*)?$/;
const INTEGER = /^\d*$/;

/**
 * 주문 비율 버튼 (10·25·50·100%·직접입력).
 * 직접입력을 누르면 슬라이더로 바뀌고, 움직이는 대로 수량·총액에 바로 반영한다. 완료하면 고른 비율이 직접입력 버튼에 남는다
 */
function PercentPicker({custom, onPick}) {
    const [editing, setEditing] = useState(false);
    const [value, setValue] = useState(custom ?? 50);
    const [dragging, setDragging] = useState(false);

    if (editing) {
        const done = () => setEditing(false);
        return (
            <div className="flex items-center gap-2">
                <div className="relative flex h-8 flex-1 items-center">
                    {dragging && (
                        // 손잡이(16px) 중심 위에 말풍선을 띄운다
                        <span
                            className="num pointer-events-none absolute -top-8 -translate-x-1/2 rounded-md bg-fg px-2 py-1 text-xs font-bold text-bg after:absolute after:top-full after:left-1/2 after:-translate-x-1/2 after:border-4 after:border-transparent after:border-t-fg"
                            style={{left: `calc(${value}% + ${8 - value * 0.16}px)`}}
                            aria-hidden="true"
                        >
                            {value}%
                        </span>
                    )}
                    <input
                        type="range"
                        min={1}
                        max={100}
                        value={value}
                        onChange={(e) => {
                            const next = Number(e.target.value);
                            setValue(next);
                            onPick(next, true);
                        }}
                        onPointerDown={() => setDragging(true)}
                        onPointerUp={() => setDragging(false)}
                        onPointerCancel={() => setDragging(false)}
                        onKeyDown={() => setDragging(true)}
                        onKeyUp={() => setDragging(false)}
                        onBlur={() => setDragging(false)}
                        aria-label="주문 비율"
                        aria-valuetext={`${value}%`}
                        className="w-full cursor-pointer accent-brand"
                    />
                </div>
                <Button type="button" size="sm" onClick={done} className="h-8 shrink-0 px-3 text-xs">완료</Button>
            </div>
        );
    }

    return (
        <div className="grid grid-cols-5 gap-1.5">
            {PERCENTS.map((p) => (
                <button key={p} type="button" onClick={() => onPick(p, false)} className="h-8 rounded-lg border border-line text-xs font-semibold text-fg-2 hover:bg-surface-2">
                    {p}%
                </button>
            ))}
            <button
                type="button"
                onClick={() => {
                    // 슬라이더를 여는 순간부터 입력칸이 슬라이더 값과 맞도록 바로 적용한다
                    const start = custom ?? 50;
                    setValue(start);
                    setEditing(true);
                    onPick(start, true);
                }}
                className={`h-8 rounded-lg border text-xs font-semibold ${custom != null ? 'num border-brand text-brand-fg' : 'border-line text-fg-2 hover:bg-surface-2'}`}
            >
                {custom != null ? `${custom}%` : '직접입력'}
            </button>
        </div>
    );
}

function NumberField({label, unit, value, onChange, onBlur, readOnly = false, pattern = DECIMAL}) {
    return (
        <label className="flex flex-col gap-1.5 text-xs text-fg-3">
            {label}
            <span className={`flex h-11 items-center rounded-[10px] border border-line px-3 ${readOnly ? 'bg-surface-2' : 'bg-surface focus-within:border-fg-3'}`}>
                <input
                    inputMode="decimal"
                    value={value}
                    readOnly={readOnly}
                    onChange={(e) => {
                        const raw = e.target.value.replace(/,/g, '');
                        if (pattern.test(raw)) onChange?.(raw);
                    }}
                    onBlur={onBlur}
                    className="num min-w-0 flex-1 text-right text-[15px] font-semibold text-fg outline-none"
                />
                <span className="ml-2 text-[13px] text-fg-3">{unit}</span>
            </span>
        </label>
    );
}

/**
 * 주문 금액 아래의 수수료 안내 (0.05%, 원 단위 올림. 실제 금액은 서버가 체결 때 정한다)
 * 매수: 대금 + 수수료 = 총 사용 금액 / 매도: 대금 − 수수료 = 받을 금액
 */
function FeeSummary({isBuy, tradeKrw, estimated}) {
    if (!(tradeKrw > 0)) return null;
    const fee = feeOf(tradeKrw);
    const prefix = estimated ? '≈ ' : '';
    const rows = isBuy
        ? [[`수수료 (${FEE_RATE_LABEL})`, `+${formatKRW(fee)}`], ['총 사용 금액', `${prefix}${formatKRW(buyCost(tradeKrw))}`]]
        : [[`예상 매도 금액`, `${prefix}${formatKRW(tradeKrw)}`], [`수수료 (${FEE_RATE_LABEL})`, `−${formatKRW(fee)}`],
            ['받을 금액', `${prefix}${formatKRW(sellProceeds(tradeKrw))}`]];
    return (
        <dl className="flex flex-col gap-1 rounded-[10px] bg-surface-2 px-3 py-2.5 text-[13px]">
            {rows.map(([label, value], i) => (
                <div key={label} className="flex justify-between">
                    <dt className="text-fg-3">{label}</dt>
                    <dd className={`num ${i === rows.length - 1 ? 'font-bold' : 'font-semibold'}`}>{value}원</dd>
                </div>
            ))}
        </dl>
    );
}

/**
 * 매수·매도 주문 (시장가 / 지정가)
 * 시장가 매수는 주문 총액, 시장가 매도는 수량, 지정가는 가격과 수량으로 낸다. 모든 주문에 수수료 0.05%
 */
// 시즌 종료 시각 전후의 거래 마감 안내 (서버도 종료 시각부터 정산이 끝날 때까지 주문을 받지 않는다)
function SettlementNotice({closed, closingSoon, endsAt, now}) {
    if (closed) {
        return (
            <p role="status" className="rounded-[10px] bg-up/10 px-3 py-2.5 text-[13px] font-semibold text-up">
                시즌 정산 중이라 거래할 수 없어요. 새 시즌이 시작되면 바로 다시 열려요 (보통 1분 안).
            </p>
        );
    }
    if (closingSoon) {
        const minutes = Math.max(1, Math.ceil((endsAt - now) / 60_000));
        return (
            <p role="status" className="rounded-[10px] bg-brand-soft px-3 py-2.5 text-[13px] font-semibold text-brand-fg">
                {minutes}분 뒤 시즌이 끝나요. 종료 시각부터 정산이 끝날 때까지 거래가 멈추고, 체결되지 않은 지정가 주문은 취소돼요.
            </p>
        );
    }
    return null;
}

export default function OrderPanel({market, currentPrice, cash, holdings, onOrderPlaced, initialSide = 'BUY', className = ''}) {
    const {closed, closingSoon, endsAt, now} = useCurrentSeason();
    const {infoAlert, errorAlert} = useToast();
    const [side, setSide] = useState(initialSide);
    const [mode, setMode] = useState('market');
    const [price, setPrice] = useState('');
    const [amount, setAmount] = useState('');
    const [total, setTotal] = useState('');
    const [loading, setLoading] = useState(false);
    const [customPercent, setCustomPercent] = useState(null); // 직접입력으로 고른 비율 (버튼 글자)

    const symbol = market.split('-')[1];
    const isBuy = side === 'BUY';
    const isHistory = side === 'HISTORY';
    const isReserve = mode === 'reserve';

    // 종목이 바뀌면 처음 상태로
    useEffect(() => {
        setSide(initialSide);
        setMode('market');
    }, [market, initialSide]);

    // 매수/매도·주문 방식을 바꾸면 입력을 비우고 가격은 현재가로 채운다
    useEffect(() => {
        setPrice(display(currentPrice));
        setAmount('');
        setTotal('');
        setCustomPercent(null);
        // 현재가가 바뀔 때마다 입력을 지우지 않도록 currentPrice는 의존성에서 뺀다
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [side, mode, market]);

    // 지정가는 가격 × 수량으로 총액을 계산한다 (매수는 올림, 매도는 내림). 수수료는 아래에 따로 보여 준다
    useEffect(() => {
        if (!isReserve) return;
        const p = toNumber(price);
        const a = toNumber(amount);
        if (Number.isNaN(p) || Number.isNaN(a)) {
            setTotal('');
            return;
        }
        setTotal(display(isBuy ? Math.ceil(p * a) : Math.floor(p * a)));
    }, [price, amount, isReserve, isBuy]);

    // 소수 8자리에서 내림. 0.00048359 × 1e8 = 48358.999… 같은 부동소수 오차로 한 단위가 빠지지 않게
    // 유효숫자 15자리로 먼저 정리한다 (고정값을 더하는 방식은 수량이 크면 오차보다 작아 효과가 없다)
    const floor8 = (n) => Math.floor(Number((n * 1e8).toPrecision(15))) / 1e8;

    const applyPercent = (percent, custom) => {
        setCustomPercent(custom ? percent : null);
        const ratio = percent / 100;
        if (isBuy) {
            // 대금 + 수수료가 비율만큼의 현금을 넘지 않는 가장 큰 대금 (100%를 눌러도 수수료 때문에 잔액이 모자라지 않게)
            const budget = maxBuyTotal(Math.floor(cash * ratio));
            const unitPrice = isReserve ? toNumber(price) : currentPrice;
            if (!isReserve) setTotal(display(budget));
            setAmount(unitPrice > 0 ? display(floor8(budget / unitPrice)) : '');
        } else {
            // 최대는 보유 수량 그대로 (계산하면 끝자리가 남을 수 있음)
            setAmount(display(ratio === 1 ? holdings : floor8(holdings * ratio)));
        }
    };

    const submit = async () => {
        const rawAmount = toNumber(amount);
        const rawPrice = toNumber(price);
        const rawTotal = toNumber(total);

        if (isReserve) {
            if (!(rawAmount > 0)) return errorAlert('주문 수량을 입력해주세요.');
            if (!(rawPrice > 0)) return errorAlert('주문 가격을 입력해주세요.');
            if (isBuy && cash < buyCost(Math.ceil(rawAmount * rawPrice))) {
                setAmount(display(floor8(maxBuyTotal(cash) / rawPrice)));
                errorAlert('잔액이 부족합니다. (수수료 포함)');
                return infoAlert('보유 현금에 맞게 수량이 자동 조정되었습니다.');
            }
            if (!isBuy && holdings < rawAmount) {
                setAmount(display(holdings));
                errorAlert('보유 수량이 부족합니다.');
                return infoAlert('보유 수량에 맞게 수량이 자동 조정되었습니다.');
            }
        } else if (isBuy) {
            if (!(rawTotal > 0)) return errorAlert('주문 총액을 입력해주세요.');
            if (cash < buyCost(rawTotal)) return errorAlert(`잔액이 부족합니다. 수수료 ${formatKRW(feeOf(rawTotal))}원을 포함해 ${formatKRW(buyCost(rawTotal))}원이 필요해요.`);
        } else if (!(rawAmount > 0)) {
            return errorAlert('매도 수량을 입력해주세요.');
        }

        const payload = {
            symbol: market,
            orderType: side,
            ...(isReserve
                ? {amount: rawAmount, reservePrice: rawPrice}
                : isBuy ? {totalPrice: rawTotal} : {amount: rawAmount}),
        };

        setLoading(true);
        try {
            const result = isReserve ? await placeReserveOrder(payload) : await placeOrder(payload);
            // 시장가는 서버가 실제로 받은 수수료를 그대로 보여 준다
            const fee = formatKRW(result.fee ?? 0);
            if (isReserve) infoAlert('지정가 주문이 접수되었습니다. 체결될 때 수수료 0.05%가 함께 정산돼요.');
            else if (isBuy) infoAlert(`${formatAmount(result.amount)} ${symbol} 매수 주문이 체결되었습니다. (수수료 ${fee}원)`);
            else infoAlert(`매도 주문이 체결되어 ${formatKRW(Math.floor(result.tradePrice * result.amount) - (result.fee ?? 0))}원을 받았어요. (수수료 ${fee}원)`);
            onOrderPlaced?.(result);
            setAmount('');
            setTotal('');
            setCustomPercent(null);
        } catch (err) {
            errorAlert(getErrorMessage(err, '주문 처리 중 오류가 발생했습니다.'));
        } finally {
            setLoading(false);
        }
    };

    const estimatedQty = !isReserve && isBuy && currentPrice > 0 && toNumber(total) > 0 ? toNumber(total) / currentPrice : null;
    // 수수료를 매길 대금: 지정가는 계산된 총액, 시장가 매수는 입력한 총액, 시장가 매도는 현재가 기준 예상 대금
    const tradeKrw = isReserve || isBuy
        ? toNumber(total)
        : currentPrice > 0 && toNumber(amount) > 0 ? Math.floor(currentPrice * toNumber(amount)) : 0;

    return (
        <section className={`flex flex-col overflow-hidden rounded-xl border border-line bg-surface ${className}`}>
            <div role="tablist" aria-label="매수·매도" className="grid grid-cols-3 border-b border-line">
                {SIDES.map(([value, label, active]) => (
                    <button
                        key={value}
                        type="button"
                        role="tab"
                        aria-selected={side === value}
                        onClick={() => setSide(value)}
                        className={`h-11 border-b-2 text-sm ${side === value ? `${active} font-bold` : 'border-transparent font-semibold text-fg-3'}`}
                    >
                        {label}
                    </button>
                ))}
            </div>

            {isHistory ? (
                <OrderHistory market={market} onChanged={onOrderPlaced}/>
            ) : (
                <form
                    className="flex flex-col gap-3 p-4"
                    onSubmit={(e) => {
                        e.preventDefault();
                        void submit();
                    }}
                >
                    <SegmentedTabs label="주문 방식" items={MODES} value={mode} onChange={setMode} fill className="[&>button]:h-8 [&>button]:text-[13px]"/>

                    <SettlementNotice closed={closed} closingSoon={closingSoon} endsAt={endsAt} now={now}/>

                    <div className="flex items-center justify-between text-[13px]">
                        <span className="text-fg-3">주문 가능</span>
                        <span className="num font-semibold">{isBuy ? `${formatKRW(cash)} KRW` : `${formatAmount(holdings)} ${symbol}`}</span>
                    </div>

                    {isReserve && (
                        <NumberField
                            label={isBuy ? '매수 가격' : '매도 가격'}
                            unit="KRW"
                            value={price}
                            onChange={setPrice}
                            onBlur={() => setPrice(display(price))}
                        />
                    )}

                    {(!isBuy || isReserve) && (
                        <NumberField label="주문 수량" unit={symbol} value={amount} onChange={setAmount} onBlur={() => setAmount(display(amount))}/>
                    )}

                    <PercentPicker key={`${side}-${mode}-${market}`} custom={customPercent} onPick={applyPercent}/>

                    {(isBuy || isReserve) && (
                        <NumberField
                            label="주문 총액"
                            unit="KRW"
                            value={total}
                            pattern={INTEGER}
                            onChange={setTotal}
                            onBlur={() => setTotal(display(total))}
                            readOnly={isReserve}
                        />
                    )}

                    {estimatedQty != null && (
                        <div className="flex justify-between text-[13px]">
                            <span className="text-fg-3">예상 수량</span>
                            <span className="num font-semibold">≈ {formatAmount(estimatedQty)} {symbol}</span>
                        </div>
                    )}

                    <FeeSummary isBuy={isBuy} tradeKrw={tradeKrw} estimated={!isReserve && !isBuy}/>

                    <Button type="submit" variant={isBuy ? 'buy' : 'sell'} disabled={loading || closed} className="mt-1 h-12 rounded-[10px]">
                        {closed ? '정산 중 · 거래 마감' : loading ? '주문 처리 중…' : `${isReserve ? '지정가 ' : '시장가 '}${isBuy ? '매수' : '매도'}`}
                    </Button>
                    <p className="text-center text-xs text-fg-3">거래 수수료 {FEE_RATE_LABEL} (시장가·지정가 동일)</p>
                </form>
            )}
        </section>
    );
}
