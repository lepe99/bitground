import {useEffect, useMemo, useState} from 'react';
import Card from '../ui/Card.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {fetchSeasons} from '../../api/seasonApi.js';
import {fetchSeasonOrders} from '../../api/orderApi.js';
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

const COLS = 'md:grid-cols-[minmax(0,1.4fr)_70px_repeat(3,minmax(0,1fr))_minmax(0,0.7fr)_110px]';

/** 시즌별 체결된 주문 내역과 낸 수수료 (체결을 기다리는 지정가 주문은 미체결 탭에서) */
export default function TradeHistory() {
    const [seasons, setSeasons] = useState([]);
    const [seasonId, setSeasonId] = useState(null);
    const [type, setType] = useState('ALL');
    const [query, setQuery] = useState('');
    const [orders, setOrders] = useState(null); // null: 불러오는 중

    useEffect(() => {
        fetchSeasons()
            .then((list) => {
                setSeasons(list);
                setSeasonId(list[0]?.id ?? null);
                if (!list.length) setOrders([]);
            })
            .catch((error) => {
                console.error('시즌 목록 조회 실패:', error);
                setOrders([]);
            });
    }, []);

    useEffect(() => {
        if (!seasonId) return;
        let alive = true;
        setOrders(null);
        fetchSeasonOrders(seasonId)
            .then((list) => alive && setOrders(list ?? []))
            .catch((error) => {
                console.error('주문 내역 조회 실패:', error);
                if (alive) setOrders([]);
            });
        return () => {
            alive = false;
        };
    }, [seasonId]);

    const rows = useMemo(() => {
        const term = query.trim().toLowerCase();
        return (orders ?? [])
            .filter((o) => o.status !== 'PENDING')
            .filter((o) => type === 'ALL' || o.orderType === type)
            .filter((o) => !term || o.coinName?.toLowerCase().includes(term) || o.symbol?.toLowerCase().includes(term))
            .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
    }, [orders, type, query]);

    const season = seasons.find((s) => s.id === seasonId);

    return (
        <Card className="overflow-hidden md:rounded-xl">
            <div className="flex flex-wrap items-center gap-2.5 px-5 py-3.5">
                <label className="flex items-center gap-2 text-sm">
                    <span className="sr-only">시즌</span>
                    <select
                        value={seasonId ?? ''}
                        onChange={(e) => setSeasonId(Number(e.target.value))}
                        className="h-9 rounded-[10px] border border-line bg-surface px-3 text-sm font-semibold"
                    >
                        {seasons.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                    {season?.startAt && (
                        <span className="num text-xs text-fg-3">{season.startAt.slice(5, 10)} ~ {season.endAt?.slice(5, 10)}</span>
                    )}
                </label>
                <SegmentedTabs label="주문 종류" items={TYPES} value={type} onChange={setType} className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"/>
                <label className="flex h-9 min-w-[160px] flex-1 items-center gap-2 rounded-[10px] bg-surface-2 px-3 text-fg-3 md:max-w-[240px]">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
                    <span className="sr-only">코인 검색</span>
                    <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="비트코인, BTC" className="min-w-0 flex-1 text-[13px] text-fg outline-none placeholder:text-fg-3"/>
                </label>
            </div>

            <div className={`hidden gap-3 border-y border-line px-5 py-2 text-xs text-fg-3 md:grid ${COLS}`}>
                <span>코인</span>
                <span>종류</span>
                <span className="text-right">수량</span>
                <span className="text-right">체결가</span>
                <span className="text-right">체결 금액</span>
                <span className="text-right">수수료</span>
                <span className="text-right">체결 시간</span>
            </div>

            {orders === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
            {orders !== null && rows.length === 0 && <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3 md:border-t-0">표시할 거래가 없어요.</p>}
            <ul>
                {rows.map((o) => {
                    const buy = o.orderType === 'BUY';
                    const amount = Number(o.amount ?? 0);
                    const price = Number(o.tradePrice ?? 0);
                    const total = buy ? Math.ceil(amount * price) : Math.floor(amount * price);
                    return (
                        <li key={o.id ?? `${o.symbol}-${o.updatedAt}`} className={`num flex items-center gap-3 border-t border-line px-5 py-3 text-sm first:border-t-0 md:grid ${COLS}`}>
                            <span className="flex min-w-0 flex-1 items-center gap-3">
                                <CoinIcon symbol={o.symbol} size={32}/>
                                <span className="flex min-w-0 flex-col">
                                    <span className="truncate font-semibold">{o.coinName ?? tickerOf(o.symbol)}</span>
                                    <span className="text-xs text-fg-3">
                                        <span className="md:hidden">{dateTime(o.updatedAt)} · {formatAmount(amount)} {tickerOf(o.symbol)}</span>
                                        <span className="max-md:hidden">{tickerOf(o.symbol)}</span>
                                    </span>
                                </span>
                            </span>
                            <span className={`font-bold max-md:hidden ${buy ? 'text-up' : 'text-down'}`}>{buy ? '매수' : '매도'}</span>
                            <span className="text-right max-md:hidden">{formatAmount(amount)}</span>
                            <span className="text-right text-fg-2 max-md:hidden">{price > 0 ? formatPrice(price) : '—'}</span>
                            <span className="flex flex-col items-end md:block md:text-right">
                                <span className={`text-[13px] font-bold md:hidden ${buy ? 'text-up' : 'text-down'}`}>{buy ? '매수' : '매도'}</span>
                                <span className="font-semibold">{price > 0 ? `${formatKRW(total)}원` : '—'}</span>
                                <span className="text-xs text-fg-3 md:hidden">수수료 {formatKRW(o.fee ?? 0)}원</span>
                            </span>
                            <span className="text-right text-fg-2 max-md:hidden">{formatKRW(o.fee ?? 0)}원</span>
                            <span className="text-right text-fg-3 max-md:hidden">{dateTime(o.updatedAt)}</span>
                        </li>
                    );
                })}
            </ul>
        </Card>
    );
}
