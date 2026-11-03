import Card from '../ui/Card.jsx';
import TierBadge from '../ui/TierBadge.jsx';
import {TIER_NAMES} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

const BINS = 9;

// 총 자산을 같은 폭 구간으로 나눠 인원수를 센다
function histogram(values, mine) {
    if (values.length === 0) return {bins: [], min: 0, max: 0};
    let min = Math.min(...values);
    let max = Math.max(...values);
    if (min === max) {
        min -= 1_000_000;
        max += 1_000_000;
    }
    const width = (max - min) / BINS;
    const bins = Array.from({length: BINS}, () => 0);
    for (const v of values) bins[Math.min(BINS - 1, Math.floor((v - min) / width))]++;
    const myBin = mine == null ? -1 : Math.min(BINS - 1, Math.floor((mine - min) / width));
    const peak = Math.max(...bins);
    return {bins: bins.map((count, i) => ({count, height: peak ? Math.max(4, (count / peak) * 100) : 0, mine: i === myBin})), min, max};
}

const toMan = (won) => `${formatKRW(Math.round(won / 10_000))}만`;

/** 내 순위·상위 % ·자산 분포에서 내 구간 */
export default function MyPosition({me, rankings}) {
    const total = rankings.length;
    const percentile = me ? Math.max(1, Math.round((me.ranks / total) * 100)) : null;
    const {bins, min, max} = histogram(rankings.map((r) => r.totalValue), me?.totalValue);

    return (
        <Card className="flex flex-col gap-4 p-5 md:p-6">
            <div className="flex items-baseline justify-between">
                <h2 className="text-base font-bold">내 위치</h2>
                <span className="text-xs text-fg-3">총 자산 분포</span>
            </div>
            {me ? (
                <div className="flex items-center gap-3.5">
                    <TierBadge tier={me.tier} size={48}/>
                    <div className="flex flex-col gap-0.5">
                        <span className="num text-2xl font-extrabold">상위 {percentile}%</span>
                        <span className="num text-sm text-fg-2">{me.ranks}위 / {total}명 · {TIER_NAMES[me.tier] ?? '집계 전'}</span>
                    </div>
                </div>
            ) : (
                <p className="text-sm text-fg-3">이번 시즌에 거래하면 다음 정시 집계부터 순위에 올라요.</p>
            )}
            {bins.length > 0 && (
                <>
                    <div className="flex h-28 items-end gap-1.5" role="img" aria-label={`참가자 ${total}명의 총 자산 분포${me ? `, 내 구간 강조` : ''}`}>
                        {bins.map((b, i) => (
                            <div key={i} className="flex h-full flex-1 flex-col items-center justify-end gap-1">
                                {b.mine && <span className="text-[10px] font-bold text-brand-fg">나</span>}
                                <div className={`w-full rounded-t ${b.mine ? 'bg-brand' : 'bg-line'}`} style={{height: `${b.height}%`}}/>
                            </div>
                        ))}
                    </div>
                    <div className="num flex justify-between text-[11px] text-fg-3">
                        <span>{toMan(min)}</span>
                        <span>{toMan((min + max) / 2)}</span>
                        <span>{toMan(max)}</span>
                    </div>
                </>
            )}
        </Card>
    );
}
