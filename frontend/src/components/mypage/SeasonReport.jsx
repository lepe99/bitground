import {useEffect, useRef, useState} from 'react';
import Card from '../ui/Card.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import TierBadge from '../ui/TierBadge.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import {useAuth} from '../../auth/useAuth.js';
import {fetchSeasons} from '../../api/seasonApi.js';
import {fetchSeasonRanking} from '../../api/rankApi.js';
import {fetchAiAdvice, fetchMyTier, fetchTradeDetails, fetchTradeSummary, generateAiAdvice} from '../../api/mypageApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {useToast} from '../toastContext.js';
import Button from '../ui/Button.jsx';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {changeColor, formatKRW, formatNumber, formatPrice, formatSignedKRW} from '../../utils/format.js';

const DETAIL_TABS = [
    {value: 'coins', label: '종목별 성과'},
    {value: 'trades', label: '거래 내역'},
];

const RING = 2 * Math.PI * 50;

// '+15.2%' → 15.2
const rateOf = (text) => parseFloat(String(text ?? '').replace(/[^\d.-]/g, '')) || 0;

function ScoreRing({score}) {
    const value = Math.max(0, Math.min(100, Number(score) || 0));
    return (
        <div className="relative size-[132px] shrink-0 md:size-[150px]">
            <svg viewBox="0 0 120 120" className="size-full" role="img" aria-label={`AI 투자 점수 ${value}점`}>
                <circle cx="60" cy="60" r="50" fill="none" className="stroke-surface-2" strokeWidth="10"/>
                <circle cx="60" cy="60" r="50" fill="none" className="stroke-brand" strokeWidth="10" strokeLinecap="round"
                        strokeDasharray={`${(value / 100) * RING} ${RING}`} transform="rotate(-90 60 60)"/>
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="num text-[38px] font-extrabold leading-none tracking-[-0.03em]">{value}</span>
                <span className="pt-1 text-xs text-fg-3">/ 100점</span>
            </div>
        </div>
    );
}

/** 종료된 시즌 리포트: 요약 · AI 리뷰 · 종목 하이라이트 · 종목별 성과/거래 내역 */
export default function SeasonReport() {
    const {user} = useAuth();
    const {errorAlert} = useToast();
    const [seasons, setSeasons] = useState(null);
    const [seasonId, setSeasonId] = useState(null);
    const [report, setReport] = useState(null);
    const [advice, setAdvice] = useState(undefined); // undefined: 불러오는 중, null: 아직 안 만듦
    const [generating, setGenerating] = useState(false);
    const [detailTab, setDetailTab] = useState('coins');
    const currentSeason = useRef(null);
    currentSeason.current = seasonId;

    useEffect(() => {
        fetchSeasons()
            .then((list) => {
                const done = list.filter((s) => s.status === 'COMPLETED');
                setSeasons(done);
                setSeasonId(done[0]?.id ?? null);
            })
            .catch(() => setSeasons([]));
    }, []);

    useEffect(() => {
        if (!seasonId) return;
        let alive = true;
        setReport(null);
        Promise.all([
            fetchTradeSummary(seasonId).catch(() => []),
            fetchTradeDetails(seasonId),
            fetchMyTier(seasonId).catch(() => null),
            fetchSeasonRanking(seasonId).catch(() => []),
        ]).then(([summary, details, tier, ranking]) => {
            if (!alive) return;
            const mine = ranking.find((r) => r.userId === user?.id);
            setReport({summary, details, tier: tier?.tier ?? mine?.tier ?? 0, rank: mine?.ranks, total: ranking.length, totalValue: mine?.totalValue});
        });
        // AI 리뷰는 이미 만든 것만 불러온다 (새로 만드는 건 사용자가 버튼을 눌렀을 때만)
        setAdvice(undefined);
        fetchAiAdvice(seasonId)
            .then((a) => alive && setAdvice(a ?? null))
            .catch(() => alive && setAdvice(null));
        return () => {
            alive = false;
        };
    }, [seasonId, user?.id]);

    const requestAdvice = async () => {
        const target = seasonId;
        setGenerating(true);
        try {
            const created = await generateAiAdvice(target);
            // 생성하는 동안 다른 시즌으로 옮겼으면 그 화면에 덮어쓰지 않는다
            if (currentSeason.current === target) setAdvice(created);
        } catch (error) {
            errorAlert(getErrorMessage(error, 'AI 리뷰를 만들지 못했어요. 잠시 후 다시 시도해 주세요.'));
        } finally {
            setGenerating(false);
        }
    };

    if (seasons === null) return <p className="py-16 text-center text-sm text-fg-3">불러오는 중…</p>;
    if (seasons.length === 0) {
        return (
            <Card className="p-8 text-center">
                <p className="text-base font-bold">아직 끝난 시즌이 없어요</p>
                <p className="pt-1.5 text-sm text-fg-3">시즌이 끝나면 순위·티어와 AI 투자 리뷰가 여기에 정리돼요.</p>
            </Card>
        );
    }

    const summary = report?.summary ?? [];
    const pick = (better) => summary.reduce((best, cur) => (best == null || better(cur, best) ? cur : best), null);
    const top = pick((a, b) => a.buyAmount > b.buyAmount);
    const best = pick((a, b) => a.profit > b.profit);
    const worst = pick((a, b) => a.profit < b.profit);
    const percentile = report?.rank && report.total ? Math.max(1, Math.round((report.rank / report.total) * 100)) : null;
    const trades = report?.details ?? [];
    const buys = trades.filter((d) => d.type === '매수').length;

    const stats = [
        {label: '최종 순위', value: report?.rank ? `${report.rank}위` : '—', sub: report?.rank ? `${report.total}명 중 · 상위 ${percentile}%` : '순위 기록 없음'},
        {label: '시즌 티어', value: TIER_NAMES[report?.tier] ?? '—', sub: '시즌 종료 시점', tone: TIER_TEXT[report?.tier], tier: report?.tier},
        {label: '최종 자산', value: report?.totalValue ? `${formatKRW(report.totalValue)}원` : '—', sub: '시즌 종료 시점 총 자산'},
        {label: '총 거래', value: `${formatNumber(trades.length)}회`, sub: `매수 ${buys} · 매도 ${trades.length - buys}`},
    ];

    return (
        <div className="flex flex-col gap-3 md:gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-lg font-extrabold md:text-[22px]">시즌 리포트</h2>
                <div role="tablist" aria-label="시즌 선택" className="flex flex-wrap gap-1.5">
                    {seasons.map((s) => (
                        <button
                            key={s.id}
                            type="button"
                            role="tab"
                            aria-selected={s.id === seasonId}
                            onClick={() => setSeasonId(s.id)}
                            className={`h-9 whitespace-nowrap rounded-full border px-3.5 text-[13px] font-semibold ${
                                s.id === seasonId ? 'border-fg bg-fg text-bg' : 'border-line bg-surface text-fg-2 hover:text-fg'
                            }`}
                        >
                            {s.name}
                        </button>
                    ))}
                </div>
            </div>

            <div className="grid grid-cols-2 gap-2 md:grid-cols-4 md:gap-3">
                {stats.map((s) => (
                    <Card key={s.label} className="flex flex-col gap-1 rounded-2xl p-4 md:px-5">
                        <span className="text-xs text-fg-3 md:text-[13px]">{s.label}</span>
                        <span className={`num flex items-center gap-1.5 text-lg font-extrabold md:text-[22px] ${s.tone ?? ''}`}>
                            {s.tier > 0 && <TierBadge tier={s.tier} size={22}/>}
                            {report ? s.value : '…'}
                        </span>
                        <span className="num text-xs text-fg-3">{report ? s.sub : ''}</span>
                    </Card>
                ))}
            </div>

            <Card className="flex flex-col items-center gap-5 p-5 md:flex-row md:items-start md:gap-8 md:p-7">
                {advice ? (
                    <>
                        <div className="flex flex-col items-center gap-2">
                            <ScoreRing score={advice.score}/>
                            <span className="text-sm font-bold">AI 투자 점수</span>
                        </div>
                        <div className="flex min-w-0 flex-1 flex-col gap-3">
                            <h3 className="text-[17px] font-bold">AI 시즌 리뷰</h3>
                            <p className="whitespace-pre-wrap text-[15px] leading-[1.75] text-fg-2">{advice.advice}</p>
                            <p className="text-xs text-fg-3">AI가 이 시즌의 매매 기록만 보고 만든 분석이며, 투자 권유가 아닙니다.</p>
                        </div>
                    </>
                ) : advice === undefined || !report ? (
                    <p className="w-full py-6 text-center text-sm text-fg-3">불러오는 중…</p>
                ) : report.rank ? (
                    // 리뷰는 시즌 정산 때 일괄로 만들지 않고, 원하는 사용자만 버튼으로 요청한다
                    <div className="flex w-full flex-col items-center gap-3 py-4 text-center">
                        <h3 className="text-[17px] font-bold">AI 시즌 리뷰</h3>
                        <p className="text-sm leading-relaxed text-fg-2">
                            이 시즌의 매매 기록을 AI가 돌아보고 잘한 점과 다음 시즌에 바꿔 볼 점을 정리해 드려요.
                        </p>
                        <Button onClick={requestAdvice} disabled={generating} className="min-w-40">
                            {generating ? 'AI가 돌아보는 중… (최대 1분)' : 'AI 리뷰 받기'}
                        </Button>
                        <p className="text-xs text-fg-3">시즌마다 한 번 만들어지고, 다시 열면 같은 리뷰를 보여줘요.</p>
                    </div>
                ) : (
                    <p className="w-full py-6 text-center text-sm text-fg-3">이 시즌에는 거래 기록이 없어 AI 리뷰를 만들 수 없어요.</p>
                )}
            </Card>

            {summary.length > 0 && (
                <div className="grid gap-2 md:grid-cols-3 md:gap-3">
                    {[
                        ['최다 투자 종목', top, `매수 ${formatKRW(top?.buyAmount)}원`, 'text-fg-2'],
                        ['최고 수익 종목', best, best?.returnRate, changeColor(rateOf(best?.returnRate))],
                        ['최저 수익 종목', worst, worst?.returnRate, changeColor(rateOf(worst?.returnRate))],
                    ].map(([label, coin, value, tone]) => (
                        <Card key={label} className="flex items-center gap-3.5 rounded-2xl p-4 md:px-5">
                            <CoinIcon symbol={coin?.coin ?? ''} size={40}/>
                            <span className="flex min-w-0 flex-1 flex-col">
                                <span className="text-xs text-fg-3">{label}</span>
                                <span className="truncate text-base font-bold">{coin?.koreanName}</span>
                            </span>
                            <span className={`num text-[15px] font-bold ${tone}`}>{value}</span>
                        </Card>
                    ))}
                </div>
            )}

            <Card className="overflow-hidden">
                <div className="flex items-center justify-between gap-3 px-5 py-3.5">
                    <h3 className="text-base font-bold">상세</h3>
                    <SegmentedTabs label="상세 보기" items={DETAIL_TABS} value={detailTab} onChange={setDetailTab} className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"/>
                </div>
                {report === null && <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
                {report && detailTab === 'coins' && (
                    summary.length === 0 ? (
                        <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3">이 시즌의 거래가 없어요.</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="num w-full min-w-[640px] text-sm">
                                <thead className="text-xs text-fg-3">
                                    <tr className="border-t border-line">
                                        {['종목', '최초 매수', '총 매수', '총 매도', '평균 매수가', '평균 매도가', '수익률', '수익금'].map((h, i) => (
                                            <th key={h} scope="col" className={`px-5 py-2 font-medium ${i === 0 ? 'text-left' : 'text-right'}`}>{h}</th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {summary.map((s) => (
                                        <tr key={s.coin} className="border-t border-line">
                                            <th scope="row" className="px-5 py-3 text-left font-semibold">{s.koreanName}</th>
                                            <td className="px-5 py-3 text-right text-fg-3">{s.buyDate}</td>
                                            <td className="px-5 py-3 text-right">{formatKRW(s.buyAmount)}</td>
                                            <td className="px-5 py-3 text-right">{formatKRW(s.sellAmount)}</td>
                                            <td className="px-5 py-3 text-right text-fg-2">{formatPrice(s.avgBuy)}</td>
                                            <td className="px-5 py-3 text-right text-fg-2">{s.avgSell ? formatPrice(s.avgSell) : '—'}</td>
                                            <td className={`px-5 py-3 text-right font-bold ${changeColor(rateOf(s.returnRate))}`}>{s.returnRate}</td>
                                            <td className={`px-5 py-3 text-right ${changeColor(Math.round(s.profit))}`}>{formatSignedKRW(s.profit)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )
                )}
                {report && detailTab === 'trades' && (
                    trades.length === 0 ? (
                        <p className="border-t border-line px-5 py-10 text-center text-sm text-fg-3">이 시즌의 거래가 없어요.</p>
                    ) : (
                        <ul className="max-h-[480px] overflow-y-auto">
                            {trades.map((d, i) => (
                                <li key={i} className="num flex items-center gap-3 border-t border-line px-5 py-3 text-sm">
                                    <span className="w-12 text-xs text-fg-3">{d.date}</span>
                                    <span className={`w-9 font-bold ${d.type === '매수' ? 'text-up' : 'text-down'}`}>{d.type}</span>
                                    <span className="min-w-0 flex-1 truncate font-semibold">{d.koreanName}</span>
                                    <span className="text-right text-fg-2 max-sm:hidden">{d.qty}</span>
                                    <span className="w-28 text-right font-semibold">{formatKRW(d.total)}원</span>
                                </li>
                            ))}
                        </ul>
                    )
                )}
            </Card>
        </div>
    );
}
