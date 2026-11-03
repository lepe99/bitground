import {useEffect, useMemo, useRef, useState} from 'react';
import {changeColor, formatCompactKRW, formatPrice} from '../../utils/format.js';
import StarIcon from '../ui/StarIcon.jsx';

const FILTERS = [
    {value: 'all', label: '원화'},
    {value: 'owned', label: '보유'},
    {value: 'fav', label: '관심'},
];

const COLUMNS = [
    {key: 'name', label: '한글명', align: 'text-left'},
    {key: 'price', label: '현재가', align: 'text-right'},
    {key: 'changeRate', label: '전일대비', align: 'text-right'},
    {key: 'volume', label: '거래대금', align: 'text-right'},
];

// 가격이 바뀐 행을 잠깐 표시하는 시간
const FLASH_MS = 500;

/**
 * 원화 마켓 코인 목록 (검색 · 원화/보유/관심 · 정렬). 거래소·투자내역 오른쪽, 모바일 거래 첫 화면
 * @param initialFilter 처음 고를 탭 ('all' | 'owned' | 'fav')
 */
export default function MarketList({
    markets,
    tickerMap,
    selectedMarket,
    onSelectMarket,
    ownedMarkets = [],
    favoriteMarkets = [],
    onToggleFav,
    initialFilter = 'all',
    className = '',
}) {
    const [query, setQuery] = useState('');
    const [filter, setFilter] = useState(initialFilter);
    const [sort, setSort] = useState({key: 'volume', desc: true});
    const [flash, setFlash] = useState({});
    const prevPrices = useRef({});

    // 직전 가격과 비교해 오르면 빨강, 내리면 파랑 테두리를 잠깐 보여준다
    useEffect(() => {
        const changed = {};
        for (const [market, t] of Object.entries(tickerMap)) {
            const prev = prevPrices.current[market];
            if (prev != null && t.price !== prev) changed[market] = t.price > prev ? 'up' : 'down';
            prevPrices.current[market] = t.price;
        }
        if (Object.keys(changed).length === 0) return;
        setFlash((f) => ({...f, ...changed}));
        const timer = setTimeout(() => {
            setFlash((f) => {
                const next = {...f};
                for (const m of Object.keys(changed)) delete next[m];
                return next;
            });
        }, FLASH_MS);
        return () => clearTimeout(timer);
    }, [tickerMap]);

    const rows = useMemo(() => {
        const term = query.trim().toLowerCase();
        const value = (m) => {
            if (sort.key === 'name') return m.name;
            return tickerMap[m.market]?.[sort.key] ?? 0;
        };
        return markets
            .filter((m) => filter !== 'owned' || ownedMarkets.includes(m.market))
            .filter((m) => filter !== 'fav' || favoriteMarkets.includes(m.market))
            .filter((m) => !term || m.name.toLowerCase().includes(term) || m.market.toLowerCase().includes(term))
            .sort((a, b) => {
                const va = value(a);
                const vb = value(b);
                const cmp = typeof va === 'string' ? va.localeCompare(vb, 'ko') : va - vb;
                return sort.desc ? -cmp : cmp;
            });
    }, [markets, tickerMap, query, filter, sort, ownedMarkets, favoriteMarkets]);

    const onSort = (key) => setSort((s) => (s.key === key ? {key, desc: !s.desc} : {key, desc: key !== 'name'}));

    return (
        <section aria-label="코인 목록" className={`flex min-h-0 flex-col bg-surface ${className}`}>
            <div className="flex flex-col gap-2.5 border-b border-line p-3">
                <label className="flex h-[38px] items-center gap-2 rounded-[10px] bg-surface-2 px-3 text-fg-3">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
                    <span className="sr-only">코인 검색</span>
                    <input
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="코인명/심볼 검색"
                        className="min-w-0 flex-1 text-[13px] text-fg outline-none placeholder:text-fg-3"
                    />
                </label>
                <div role="tablist" aria-label="코인 목록 보기" className="flex gap-1">
                    {FILTERS.map((f) => (
                        <button
                            key={f.value}
                            type="button"
                            role="tab"
                            aria-selected={filter === f.value}
                            onClick={() => setFilter(f.value)}
                            className={`h-[30px] rounded-lg px-3 text-xs ${filter === f.value ? 'bg-surface-2 font-bold text-fg' : 'font-semibold text-fg-3 hover:text-fg-2'}`}
                        >
                            {f.label}
                        </button>
                    ))}
                </div>
            </div>

            <div className="grid grid-cols-[1.5fr_1.2fr_0.8fr_0.9fr] gap-2 border-b border-line px-3 py-2 text-[11px] text-fg-3">
                {COLUMNS.map((c) => (
                    <button key={c.key} type="button" onClick={() => onSort(c.key)} className={`${c.align} hover:text-fg-2`} aria-label={`${c.label} 순 정렬`}>
                        {c.label}
                        {sort.key === c.key && <span aria-hidden="true"> {sort.desc ? '▼' : '▲'}</span>}
                    </button>
                ))}
            </div>

            <ul className="min-h-0 flex-1 overflow-y-auto">
                {rows.length === 0 && (
                    <li className="px-3 py-10 text-center text-sm text-fg-3">
                        {filter === 'owned' ? '보유한 코인이 없어요.' : filter === 'fav' ? '관심 코인을 별표로 추가해 보세요.' : '검색 결과가 없어요.'}
                    </li>
                )}
                {rows.map(({market, name}) => {
                    const t = tickerMap[market];
                    const color = changeColor(t?.changeRate);
                    const fav = favoriteMarkets.includes(market);
                    const selected = market === selectedMarket;
                    return (
                        <li key={market} className={`border-b border-line ${selected ? 'bg-brand-soft' : 'hover:bg-surface-2'}`}>
                            <div onClick={() => onSelectMarket(market)} className="grid cursor-pointer grid-cols-[1.5fr_1.2fr_0.8fr_0.9fr] items-center gap-2 px-3 py-2">
                                <span className="flex min-w-0 items-center gap-1.5">
                                    {onToggleFav && (
                                        <button
                                            type="button"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onToggleFav(market);
                                            }}
                                            aria-label={fav ? `${name} 관심 해제` : `${name} 관심 추가`}
                                            aria-pressed={fav}
                                            className={`-ml-1 flex size-7 shrink-0 items-center justify-center rounded-md ${fav ? 'text-brand' : 'text-fg-3 hover:text-fg-2'}`}
                                        >
                                            <StarIcon filled={fav}/>
                                        </button>
                                    )}
                                    <button type="button" aria-current={selected || undefined} className="flex min-w-0 flex-col text-left">
                                        <span className="truncate text-[13px] font-semibold">{name}</span>
                                        <span className="text-[11px] text-fg-3">{market.replace('KRW-', '')}/KRW</span>
                                    </button>
                                </span>
                                <span className={`num justify-self-end rounded px-1 text-right text-[13px] font-semibold ring-1 ${color} ${
                                    flash[market] === 'up' ? 'ring-up' : flash[market] === 'down' ? 'ring-down' : 'ring-transparent'
                                }`}>
                                    {formatPrice(t?.price)}
                                </span>
                                <span className={`num text-right text-xs font-semibold ${color}`}>
                                    {t ? `${t.changeRate > 0 ? '+' : ''}${(t.changeRate * 100).toFixed(2)}%` : '—'}
                                </span>
                                <span className="num text-right text-xs text-fg-2">{t ? formatCompactKRW(t.volume) : '—'}</span>
                            </div>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}
