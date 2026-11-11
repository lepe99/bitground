import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {toPoints} from '../../utils/chart.js';
import {fetchTodayMarketIndex, fetchYesterdayMarketIndex} from '../../api/marketIndexApi.js';
import {changeColor, formatNumber} from '../../utils/format.js';

// 그래프로 볼 지수 (나머지 하나는 아래에 값만 보여준다)
const INDEXES = {
    market: {key: 'marketIndex', name: 'BGMI', label: 'BGMI · 전체 시장 지수', tab: '전체 시장'},
    alt: {key: 'altIndex', name: 'BGAI', label: 'BGAI · 알트코인 지수', tab: '알트코인'},
};
const TABS = Object.entries(INDEXES).map(([value, i]) => ({value, label: i.tab}));

const HOURS = Array.from({length: 24}, (_, h) => String(h).padStart(2, '0') + ':00');

// 시간별 값 배열 (없는 시간은 null)
const byHour = (rows, key) => HOURS.map((hour) => rows.find((r) => r.hour === hour)?.[key] ?? null);

const lastOf = (values) => [...values].reverse().find((v) => v != null) ?? null;

function IndexValue({label, today, yesterday, large = false}) {
    const now = lastOf(today);
    const prev = lastOf(yesterday);
    const diff = now != null && prev != null ? now - prev : null;
    const rate = diff != null && prev ? (diff / prev) * 100 : null;
    const arrow = diff > 0 ? '▲' : diff < 0 ? '▼' : '';

    if (large) {
        return (
            <div className="flex flex-col gap-1">
                <span className="text-[13px] text-fg-3">{label}</span>
                <div className="flex items-baseline gap-2.5">
                    <span className="num text-[28px] font-extrabold">{now != null ? formatNumber(now) : '—'}</span>
                    {diff != null && (
                        <span className={`num text-sm font-semibold ${changeColor(diff)}`}>
                            {arrow} {formatNumber(Math.abs(diff))} ({Math.abs(rate).toFixed(2)}%)
                        </span>
                    )}
                </div>
            </div>
        );
    }
    return (
        <div className="flex items-center justify-between">
            <span className="text-[13px] text-fg-3">{label}</span>
            <span className="num text-sm font-semibold">
                {now != null ? formatNumber(now) : '—'}{' '}
                {rate != null && <span className={changeColor(diff)}>{arrow} {Math.abs(rate).toFixed(2)}%</span>}
            </span>
        </div>
    );
}

/** 자체 시장 지수(BGMI·BGAI): 탭으로 고른 지수의 오늘과 어제 시간별 추이 */
export default function MarketIndexCard() {
    const [tab, setTab] = useState('market');
    const [today, setToday] = useState([]);
    const [yesterday, setYesterday] = useState([]);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        Promise.all([fetchTodayMarketIndex(), fetchYesterdayMarketIndex()])
            .then(([t, y]) => {
                setToday(t ?? []);
                setYesterday(y ?? []);
            })
            .catch((error) => {
                console.error('시장 지수 조회 실패:', error);
                setFailed(true);
            });
    }, []);

    const shown = INDEXES[tab];
    const other = INDEXES[tab === 'market' ? 'alt' : 'market'];
    const shownToday = byHour(today, shown.key);
    const shownYesterday = byHour(yesterday, shown.key);
    const all = [...shownToday, ...shownYesterday].filter((v) => v != null);
    const min = all.length ? Math.min(...all) : 0;
    const max = all.length ? Math.max(...all) : 1;
    const todayLine = toPoints(shownToday, 300, 96, min, max);
    const yesterdayLine = toPoints(shownYesterday, 300, 96, min, max);
    const up = (lastOf(shownToday) ?? 0) >= (lastOf(shownYesterday) ?? 0);

    return (
        <Card className="flex flex-col gap-4 p-5 md:p-6">
            <div className="flex items-center justify-between gap-2">
                <h2 className="text-base font-bold md:text-[17px]">시장 지수</h2>
                <SegmentedTabs label="지수 선택" items={TABS} value={tab} onChange={setTab} className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"/>
            </div>
            {failed && <p className="text-sm text-fg-3">지수를 불러오지 못했어요.</p>}
            <IndexValue label={shown.label} today={shownToday} yesterday={shownYesterday} large/>
            <svg viewBox="0 0 300 96" preserveAspectRatio="none" className="h-24 w-full" role="img" aria-label={`${shown.name} 오늘(실선)과 어제(점선) 시간별 추이`}>
                {yesterdayLine && (
                    <polyline points={yesterdayLine} fill="none" className="stroke-fg-3" strokeWidth="1.5" strokeDasharray="4 4" vectorEffect="non-scaling-stroke"/>
                )}
                {todayLine && (
                    <polyline points={todayLine} fill="none" className={up ? 'stroke-up' : 'stroke-down'} strokeWidth="2.5" strokeLinejoin="round" vectorEffect="non-scaling-stroke"/>
                )}
            </svg>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-fg-3">
                <span className="flex items-center gap-1.5"><span className={`h-0.5 w-4 ${up ? 'bg-up' : 'bg-down'}`}/>오늘</span>
                <span className="flex items-center gap-1.5"><span className="h-0 w-4 border-t border-dashed border-fg-3"/>어제</span>
                <span className="ml-auto">시가총액 가중 · 매시 갱신</span>
            </div>
            <div className="border-t border-line pt-3">
                <IndexValue label={other.label} today={byHour(today, other.key)} yesterday={byHour(yesterday, other.key)}/>
            </div>
        </Card>
    );
}
