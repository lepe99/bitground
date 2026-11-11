import Card from '../ui/Card.jsx';
import {coinTint} from '../../utils/coin.js';
import {changeColor, formatKRW, formatPercent, formatSignedKRW} from '../../utils/format.js';

const KRW_COLOR = 'var(--c-line)';

// 리플(#23292F)처럼 아주 어두운 대표색은 다크 바탕에서 안 보이므로 회색으로 바꾼다
const visibleTint = (symbol) => {
    const hex = coinTint(symbol).slice(1);
    const [r, g, b] = [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16));
    return 0.299 * r + 0.587 * g + 0.114 * b < 60 ? '#8B93A1' : `#${hex}`;
};
const RADIUS = 52;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

// 원화 + 큰 코인 3개 + 나머지(기타)
function allocation(portfolio) {
    const {rows, cash, total} = portfolio;
    if (!total) return [];
    const top = rows.slice(0, 3).map((r) => ({label: r.name, value: r.value, color: visibleTint(r.symbol)}));
    const rest = rows.slice(3).reduce((s, r) => s + r.value, 0);
    return [
        {label: '원화', value: cash, color: KRW_COLOR},
        ...top,
        ...(rest > 0 ? [{label: '기타', value: rest, color: '#5B6472'}] : []),
    ]
        .filter((p) => p.value > 0)
        .map((p) => ({...p, ratio: p.value / total}));
}

/** 총 보유자산 · 손익 · 원화/코인 비중 */
export default function AssetOverview({portfolio, availableCash, reserved, seasonFee = 0}) {
    const parts = allocation(portfolio);
    const coinRatio = portfolio.total ? (portfolio.evalTotal / portfolio.total) * 100 : 0;
    const summary = [
        ['보유 KRW', `${formatKRW(portfolio.cash)}`],
        ['주문 가능', `${formatKRW(availableCash)}`],
        ['미체결 주문 묶임', `${formatKRW(reserved)}`],
        ['총 매수', `${formatKRW(portfolio.buyTotal)}`],
        ['총 평가', `${formatKRW(portfolio.evalTotal)}`],
        ['평가손익', formatSignedKRW(portfolio.pnl), changeColor(Math.round(portfolio.pnl))],
        ['이번 시즌 수수료', `${formatKRW(seasonFee)}`], // 매수·매도 0.05%. 평가손익(체결가 기준)에는 들어 있지 않다
    ];
    let offset = 0;

    return (
        <Card className="flex flex-col gap-5 p-5 md:flex-row md:flex-wrap md:items-center md:gap-8 md:rounded-xl md:px-6">
            <div className="flex min-w-0 flex-col gap-4 md:flex-[1_1_380px]">
                <div className="flex flex-col gap-1">
                    <span className="text-[13px] text-fg-3">총 보유자산</span>
                    <span className="num text-[clamp(22px,8vw,30px)] font-extrabold tracking-[-0.02em] [overflow-wrap:anywhere] md:text-[32px]">
                        {formatKRW(portfolio.total)} <span className="text-base font-semibold text-fg-3">KRW</span>
                    </span>
                    <span className={`num text-sm font-semibold ${changeColor(Math.round(portfolio.pnl))}`}>
                        평가손익 {formatSignedKRW(portfolio.pnl)} ({formatPercent(portfolio.pnlRate)})
                    </span>
                </div>
                <dl className="grid grid-cols-2 gap-x-6 gap-y-3.5 border-t border-line pt-4 sm:grid-cols-3">
                    {summary.map(([label, value, tone]) => (
                        <div key={label} className="flex min-w-0 flex-col gap-0.5">
                            <dt className="text-xs text-fg-3">{label}</dt>
                            <dd className={`num text-[15px] font-semibold [overflow-wrap:anywhere] ${tone ?? ''}`}>{value}</dd>
                        </div>
                    ))}
                </dl>
            </div>

            {parts.length > 0 && (
                <div className="flex items-center gap-6">
                    {/* PC: 도넛 */}
                    <div className="relative hidden size-[140px] md:block">
                        <svg width="140" height="140" viewBox="0 0 140 140" role="img" aria-label={`보유 비중: ${parts.map((p) => `${p.label} ${(p.ratio * 100).toFixed(0)}%`).join(', ')}`}>
                            {parts.map((p) => {
                                const dash = p.ratio * CIRCUMFERENCE;
                                const el = (
                                    <circle
                                        key={p.label}
                                        cx="70" cy="70" r={RADIUS} fill="none" stroke={p.color} strokeWidth="18"
                                        strokeDasharray={`${dash} ${CIRCUMFERENCE}`} strokeDashoffset={-offset}
                                        transform="rotate(-90 70 70)"
                                    />
                                );
                                offset += dash;
                                return el;
                            })}
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                            <span className="text-[11px] text-fg-3">코인 비중</span>
                            <span className="num text-lg font-extrabold">{coinRatio.toFixed(1)}%</span>
                        </div>
                    </div>
                    <ul className="hidden flex-col gap-2 md:flex">
                        {parts.map((p) => (
                            <li key={p.label} className="flex items-center gap-2 text-[13px]">
                                <span className="size-2.5 rounded-[3px]" style={{background: p.color}}/>
                                <span className="w-16 truncate text-fg-2">{p.label}</span>
                                <span className="num font-semibold">{(p.ratio * 100).toFixed(1)}%</span>
                            </li>
                        ))}
                    </ul>

                    {/* 모바일: 막대 */}
                    <div className="flex w-full flex-col gap-3 md:hidden">
                        <div className="flex h-3 gap-0.5 overflow-hidden rounded-md" aria-hidden="true">
                            {parts.map((p) => <span key={p.label} style={{width: `${p.ratio * 100}%`, background: p.color}}/>)}
                        </div>
                        <ul className="grid grid-cols-2 gap-x-4 gap-y-2">
                            {parts.map((p) => (
                                <li key={p.label} className="flex items-center gap-2 text-[13px]">
                                    <span className="size-2 rounded-full" style={{background: p.color}}/>
                                    <span className="flex-1 truncate text-fg-2">{p.label}</span>
                                    <span className="num font-semibold">{(p.ratio * 100).toFixed(1)}%</span>
                                </li>
                            ))}
                        </ul>
                    </div>
                </div>
            )}
        </Card>
    );
}
