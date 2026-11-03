import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import {toPoints} from '../../utils/chart.js';
import {fetchTodayMarketIndex, fetchYesterdayMarketIndex} from '../../api/marketIndexApi.js';
import {changeColor, formatNumber} from '../../utils/format.js';

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

/** 자체 시장 지수(BGMI·BGAI): 오늘과 어제 시간별 추이 */
export default function MarketIndexCard() {
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

    const bgmiToday = byHour(today, 'marketIndex');
    const bgmiYesterday = byHour(yesterday, 'marketIndex');
    const all = [...bgmiToday, ...bgmiYesterday].filter((v) => v != null);
    const min = all.length ? Math.min(...all) : 0;
    const max = all.length ? Math.max(...all) : 1;
    const todayLine = toPoints(bgmiToday, 300, 96, min, max);
    const yesterdayLine = toPoints(bgmiYesterday, 300, 96, min, max);
    const up = (lastOf(bgmiToday) ?? 0) >= (lastOf(bgmiYesterday) ?? 0);

    return (
        <Card className="flex flex-col gap-4 p-5 md:p-6">
            <div className="flex items-baseline justify-between gap-2">
                <h2 className="text-base font-bold md:text-[17px]">시장 지수</h2>
                <span className="text-xs text-fg-3">시가총액 가중 · 매시 갱신</span>
            </div>
            {failed && <p className="text-sm text-fg-3">지수를 불러오지 못했어요.</p>}
            <IndexValue label="BGMI · 전체 시장 지수" today={bgmiToday} yesterday={bgmiYesterday} large/>
            <svg viewBox="0 0 300 96" preserveAspectRatio="none" className="h-24 w-full" role="img" aria-label="BGMI 오늘(실선)과 어제(점선) 시간별 추이">
                {yesterdayLine && (
                    <polyline points={yesterdayLine} fill="none" className="stroke-fg-3" strokeWidth="1.5" strokeDasharray="4 4" vectorEffect="non-scaling-stroke"/>
                )}
                {todayLine && (
                    <polyline points={todayLine} fill="none" className={up ? 'stroke-up' : 'stroke-down'} strokeWidth="2.5" strokeLinejoin="round" vectorEffect="non-scaling-stroke"/>
                )}
            </svg>
            <div className="flex gap-4 text-xs text-fg-3">
                <span className="flex items-center gap-1.5"><span className={`h-0.5 w-4 ${up ? 'bg-up' : 'bg-down'}`}/>오늘</span>
                <span className="flex items-center gap-1.5"><span className="h-0 w-4 border-t border-dashed border-fg-3"/>어제</span>
            </div>
            <div className="border-t border-line pt-3">
                <IndexValue label="BGAI · 알트코인 지수" today={byHour(today, 'altIndex')} yesterday={byHour(yesterday, 'altIndex')}/>
            </div>
        </Card>
    );
}
