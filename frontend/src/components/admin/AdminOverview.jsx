import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import {fetchAdminOverview} from '../../api/adminApi.js';
import {formatNumber} from '../../utils/format.js';
import {shortTime} from './adminFormat.js';

// 모니터링 도구 주소 (운영에서는 내부망 주소로 바꾼다)
const TOOLS = [
    {label: 'Grafana', desc: '백엔드·스케줄러 로그', href: import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3000'},
    {label: 'Pinpoint', desc: '요청 추적·성능', href: import.meta.env.VITE_PINPOINT_URL ?? 'http://localhost:8080'},
];

function Stat({label, value, sub}) {
    return (
        <div className="flex flex-col gap-0.5 rounded-xl bg-surface-2 px-4 py-3">
            <span className="text-xs text-fg-3">{label}</span>
            <span className="num text-xl font-extrabold">{value}</span>
            {sub && <span className="text-xs text-fg-3">{sub}</span>}
        </div>
    );
}

function Section({title, children, aside}) {
    return (
        <Card className="flex flex-col gap-3 p-5">
            <div className="flex items-baseline justify-between gap-2">
                <h3 className="text-[15px] font-bold">{title}</h3>
                {aside}
            </div>
            {children}
        </Card>
    );
}

/** 운영 현황: 사용자·거래·시즌·스케줄러·AI */
export default function AdminOverview() {
    const [data, setData] = useState(null);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        fetchAdminOverview().then(setData).catch(() => setFailed(true));
    }, []);

    if (failed) return <p className="py-10 text-center text-sm text-fg-3">현황을 불러오지 못했어요.</p>;
    if (!data) return <p className="py-10 text-center text-sm text-fg-3">불러오는 중…</p>;
    const {users, trades, season, participants, schedulerRuns, ai, pendingInquiries} = data;

    return (
        <div className="flex flex-col gap-4">
            <Section title="사용자">
                <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
                    <Stat label="가입자" value={formatNumber(users.total)} sub={`오늘 +${formatNumber(users.joinedToday)}`}/>
                    <Stat label="오늘 거래한 사용자" value={formatNumber(users.activeToday)}/>
                    <Stat label="이용 정지" value={formatNumber(users.suspended)}/>
                    <Stat label="탈퇴" value={formatNumber(users.withdrawn)}/>
                </div>
            </Section>

            <Section title="문의">
                <Link to="/community?category=INQUIRY&answered=false" className="block rounded-xl hover:bg-surface-2">
                    <Stat label="답변 대기" value={formatNumber(pendingInquiries ?? 0)} sub="눌러서 답변하기"/>
                </Link>
            </Section>

            <Section title="거래 (오늘)">
                <div className="grid grid-cols-3 gap-2">
                    <Stat label="시장가 체결" value={formatNumber(trades.marketToday)}/>
                    <Stat label="예약 체결" value={formatNumber(trades.reservedToday)}/>
                    <Stat label="대기 중인 예약" value={formatNumber(trades.pendingReserved)}/>
                </div>
            </Section>

            <div className="grid gap-4 md:grid-cols-2">
                <Section title="시즌">
                    {season ? (
                        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                            <dt className="text-fg-3">이름</dt><dd className="font-semibold">{season.name} · 스플릿 {season.split}</dd>
                            <dt className="text-fg-3">스플릿 2 시작</dt><dd className="num">{shortTime(season.splitStartsAt)}</dd>
                            <dt className="text-fg-3">시즌 종료</dt><dd className="num">{shortTime(season.endsAt)}</dd>
                            <dt className="text-fg-3">참가자</dt><dd className="num">{formatNumber(participants)}명</dd>
                        </dl>
                    ) : (
                        <p className="text-sm text-fg-3">진행 중인 시즌이 없어요.</p>
                    )}
                </Section>

                <Section title="스케줄러 실행 기록" aside={<span className="text-xs text-fg-3">시즌 정산·스플릿</span>}>
                    {schedulerRuns.length === 0 ? (
                        <p className="text-sm text-fg-3">아직 기록이 없어요.</p>
                    ) : (
                        <ul className="flex flex-col text-sm">
                            {schedulerRuns.map((r) => (
                                <li key={`${r.kind}-${r.period}`} className="num flex items-center justify-between gap-2 border-b border-line py-1.5 last:border-b-0">
                                    <span className="font-semibold">{r.kind === 'season' ? '시즌 정산' : '스플릿'}</span>
                                    <span className="text-fg-2">{shortTime(r.ranAt)}</span>
                                    <span className={`text-xs ${r.notifiedAt ? 'text-fg-3' : 'text-up'}`}>{r.notifiedAt ? '알림 보냄' : '알림 안 감'}</span>
                                </li>
                            ))}
                        </ul>
                    )}
                </Section>
            </div>

            <Section title="AI 운영">
                <div className="grid gap-3 md:grid-cols-2">
                    <div className="flex flex-col gap-1.5 rounded-xl bg-surface-2 px-4 py-3 text-sm">
                        <span className="text-xs text-fg-3">오늘의 AI 인사이트</span>
                        <span className="num text-xl font-extrabold">{ai.insightsToday}건</span>
                        <span className="text-xs text-fg-2">{ai.insightSymbols.map((s) => (s === 'MARKET_OVERALL' ? '시장 전체' : s.replace('KRW-', ''))).join(' · ') || '아직 없음'}</span>
                        {ai.insightModel && <span className="text-xs text-fg-3">{ai.insightModel} · {ai.insightPromptVersion}</span>}
                    </div>
                    <div className="flex flex-col gap-1.5 rounded-xl bg-surface-2 px-4 py-3 text-sm">
                        <span className="text-xs text-fg-3">AI 시즌 리뷰</span>
                        <span className="num text-xl font-extrabold">{formatNumber(ai.reviewsTotal)}건</span>
                        <span className="text-xs text-fg-2">오늘 {formatNumber(ai.reviewsToday)}건 생성</span>
                        {ai.reviewModel && <span className="text-xs text-fg-3">{ai.reviewModel} · {ai.reviewPromptVersion}</span>}
                    </div>
                </div>
            </Section>

            <Section title="모니터링 도구">
                <div className="grid gap-2 md:grid-cols-2">
                    {TOOLS.map((t) => (
                        <a key={t.label} href={t.href} target="_blank" rel="noopener noreferrer"
                           className="flex items-center justify-between rounded-xl border border-line px-4 py-3 hover:bg-surface-2">
                            <span className="flex flex-col">
                                <span className="text-sm font-semibold">{t.label}</span>
                                <span className="text-xs text-fg-3">{t.desc}</span>
                            </span>
                            <span className="text-fg-3" aria-hidden="true">↗</span>
                        </a>
                    ))}
                </div>
            </Section>
        </div>
    );
}
