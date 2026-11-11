import {useCallback, useEffect, useMemo, useState} from 'react';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {useToast} from '../toastContext.js';
import {useNotifications} from '../../notifications/useNotifications.js';
import {cancelReserveOrder, fetchPendingOrders, fetchSeasonOrders} from '../../api/orderApi.js';
import {fetchCurrentSeasonId} from '../../api/seasonApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {tickerOf} from '../../utils/coin.js';
import {formatAmount, formatKRW, formatPrice} from '../../utils/format.js';

const TABS = [
    {value: 'pending', label: '미체결'},
    {value: 'done', label: '체결'},
];

const toMarket = (symbol = '') => (symbol.includes('KRW-') ? symbol : `KRW-${symbol}`);

const dateTime = (iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

/**
 * 거래소 주문 칸의 '거래내역' 탭. 이번 시즌의 미체결(예약)·체결 주문을 보고 미체결 주문은 바로 취소한다
 * @param onChanged 주문을 취소해 묶여 있던 현금·수량이 풀렸을 때 부모가 자산을 다시 읽도록 알림
 */
export default function OrderHistory({market, onChanged}) {
    const {infoAlert, errorAlert} = useToast();
    const {userCash} = useNotifications();
    const [tab, setTab] = useState('pending');
    const [onlyThis, setOnlyThis] = useState(true);
    const [pending, setPending] = useState(null); // null: 불러오는 중
    const [done, setDone] = useState(null);
    const [cancelling, setCancelling] = useState(null);

    const load = useCallback(async () => {
        try {
            const [pendingList, seasonId] = await Promise.all([fetchPendingOrders(), fetchCurrentSeasonId()]);
            setPending(pendingList ?? []);
            const orders = seasonId ? await fetchSeasonOrders(seasonId) : [];
            setDone((orders ?? []).filter((o) => o.status !== 'PENDING'));
        } catch (error) {
            console.error('거래내역 조회 실패:', error);
            setPending((prev) => prev ?? []);
            setDone((prev) => prev ?? []);
        }
    }, []);

    // 처음 열 때, 그리고 예약 주문이 체결돼 현금이 바뀌었을 때 다시 읽는다
    useEffect(() => {
        void load();
    }, [load, userCash]);

    const rows = useMemo(() => {
        const list = tab === 'pending' ? pending : done;
        if (!list) return null;
        const filtered = onlyThis ? list.filter((o) => toMarket(o.symbol) === market) : list;
        const time = (o) => new Date(tab === 'pending' ? o.createdAt : o.updatedAt);
        return [...filtered].sort((a, b) => time(b) - time(a));
    }, [tab, pending, done, onlyThis, market]);

    const cancel = async (order) => {
        setCancelling(order.id);
        try {
            await cancelReserveOrder(order.id);
            infoAlert('지정가 주문이 취소되었습니다.');
            setPending((prev) => prev.filter((o) => o.id !== order.id));
            onChanged?.();
        } catch (err) {
            errorAlert(getErrorMessage(err, '주문 취소에 실패했습니다.'));
        } finally {
            setCancelling(null);
        }
    };

    return (
        <div className="flex min-h-0 flex-1 flex-col">
            <div className="flex flex-col gap-2.5 p-4 pb-3">
                <SegmentedTabs label="거래내역 종류" items={TABS} value={tab} onChange={setTab} fill className="[&>button]:h-8 [&>button]:text-[13px]"/>
                <label className="flex items-center gap-2 self-end text-xs text-fg-2">
                    <input type="checkbox" checked={onlyThis} onChange={(e) => setOnlyThis(e.target.checked)} className="size-3.5 accent-brand"/>
                    {tickerOf(market)}만 보기
                </label>
            </div>

            <ul className="min-h-[240px] flex-1 overflow-y-auto border-t border-line md:max-h-[380px]">
                {rows === null && <li className="py-10 text-center text-sm text-fg-3">불러오는 중…</li>}
                {rows?.length === 0 && (
                    <li className="py-10 text-center text-sm text-fg-3">{tab === 'pending' ? '미체결 주문이 없어요.' : '체결된 주문이 없어요.'}</li>
                )}
                {rows?.map((o) => {
                    const buy = o.orderType === 'BUY';
                    const amount = Number(o.amount ?? 0);
                    const isPending = tab === 'pending';
                    const price = Number((isPending ? o.reservePrice : o.tradePrice) ?? 0);
                    const total = buy ? Math.ceil(amount * price) : Math.floor(amount * price);
                    return (
                        <li key={`${tab}-${o.id ?? `${o.symbol}-${o.updatedAt}`}`} className="num flex items-center gap-3 border-b border-line px-4 py-2.5 text-[13px]">
                            <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                                <span className="truncate font-semibold">
                                    <span className={buy ? 'text-up' : 'text-down'}>{buy ? '매수' : '매도'}</span>
                                    {' · '}{(isPending ? o.koreanName : o.coinName) ?? tickerOf(o.symbol)}
                                </span>
                                <span className="text-[11px] text-fg-3">{dateTime(isPending ? o.createdAt : o.updatedAt)}</span>
                            </span>
                            <span className="flex flex-col items-end gap-0.5 text-right">
                                <span className="font-semibold">{price > 0 ? formatPrice(price) : '—'}<span className="ml-0.5 text-[11px] font-normal text-fg-3">원</span></span>
                                <span className="text-[11px] text-fg-3">
                                    {formatAmount(amount)} {tickerOf(o.symbol)} · {formatKRW(total)}원
                                    {!isPending && ` · 수수료 ${formatKRW(o.fee ?? 0)}원`}
                                </span>
                            </span>
                            {isPending && (
                                <button
                                    type="button"
                                    onClick={() => cancel(o)}
                                    disabled={cancelling === o.id}
                                    className="h-7 shrink-0 rounded-md border border-line px-2.5 text-xs font-semibold text-fg-2 hover:bg-surface-2 disabled:opacity-50"
                                >
                                    {cancelling === o.id ? '취소 중' : '취소'}
                                </button>
                            )}
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}
