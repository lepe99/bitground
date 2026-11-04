import {useState} from 'react';
import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {useToast} from '../toastContext.js';
import {cancelReserveOrder} from '../../api/orderApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {tickerOf} from '../../utils/coin.js';
import {formatAmount, formatKRW, formatPrice} from '../../utils/format.js';

const TYPES = [
    {value: 'ALL', label: '전체'},
    {value: 'BUY', label: '매수'},
    {value: 'SELL', label: '매도'},
];

const dateTime = (iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

/**
 * 체결을 기다리는 예약 주문. 현재가와 예약 가격을 나란히 보여주고 하나씩 취소한다
 * @param orders null이면 불러오는 중
 * @param onChanged 취소 후 목록을 다시 읽도록 부모에 알림
 */
export default function PendingOrderList({orders, tickerMap, onChanged}) {
    const {infoAlert, errorAlert} = useToast();
    const [type, setType] = useState('ALL');
    const [cancelling, setCancelling] = useState(null);

    const rows = (orders ?? []).filter((o) => type === 'ALL' || o.orderType === type);

    const cancel = async (order) => {
        setCancelling(order.id);
        try {
            await cancelReserveOrder(order.id);
            infoAlert('예약 주문이 취소되었습니다.');
            onChanged?.();
        } catch (err) {
            errorAlert(getErrorMessage(err, '주문 취소에 실패했습니다.'));
        } finally {
            setCancelling(null);
        }
    };

    return (
        <Card className="overflow-hidden md:rounded-xl">
            <div className="flex items-center justify-between gap-3 px-5 py-3.5">
                <h2 className="text-[15px] font-bold">예약 주문</h2>
                <SegmentedTabs label="주문 종류" items={TYPES} value={type} onChange={setType} className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"/>
            </div>
            {orders === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
            {orders !== null && rows.length === 0 && <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3">대기 중인 예약 주문이 없어요.</p>}
            <ul>
                {rows.map((o) => {
                    const symbol = o.symbol.includes('KRW-') ? o.symbol : `KRW-${o.symbol}`;
                    const buy = o.orderType === 'BUY';
                    const current = tickerMap[symbol]?.price;
                    const total = Number(o.amount) * Number(o.reservePrice ?? 0);
                    return (
                        <li key={o.id} className="num flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line px-5 py-3.5 text-sm">
                            <span className="flex min-w-[180px] flex-1 items-center gap-3">
                                <CoinIcon symbol={symbol} size={32}/>
                                <span className="flex min-w-0 flex-col">
                                    <span className="font-semibold">
                                        <span className={buy ? 'text-up' : 'text-down'}>{buy ? '매수' : '매도'}</span> · {o.koreanName ?? tickerOf(symbol)}
                                    </span>
                                    <span className="text-xs text-fg-3">{dateTime(o.createdAt)} 접수</span>
                                </span>
                            </span>
                            <dl className="grid flex-[2_1_320px] grid-cols-3 gap-3 text-right">
                                <div><dt className="text-xs text-fg-3">수량</dt><dd className="font-semibold">{formatAmount(o.amount)}</dd></div>
                                <div><dt className="text-xs text-fg-3">예약가 / 현재가</dt><dd className="font-semibold">{formatPrice(o.reservePrice)} <span className="font-normal text-fg-3">/ {formatPrice(current)}</span></dd></div>
                                <div><dt className="text-xs text-fg-3">주문 금액</dt><dd className="font-semibold">{formatKRW(total)}원</dd></div>
                            </dl>
                            <Button variant="secondary" size="sm" onClick={() => cancel(o)} disabled={cancelling === o.id} className="ml-auto">
                                {cancelling === o.id ? '취소 중…' : '취소'}
                            </Button>
                        </li>
                    );
                })}
            </ul>
        </Card>
    );
}
