import {useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import TierBadge from '../ui/TierBadge.jsx';
import {useRankDetail} from './useRankDetail.js';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

// 커서에서 떨어뜨리는 거리와 카드 폭. 화면 끝에서는 커서 반대쪽으로 넘긴다
const GAP = 16;
const CARD_W = 300;
// 아래쪽 공간이 이보다 좁으면 커서 위로 띄운다 (지난 시즌 5개를 다 보여줄 때 높이 정도)
const FLIP_BELOW = 400;
// 순위표를 쓱 훑고 지나갈 때마다 요청하지 않도록 잠깐 머물렀을 때만 띄운다
const HOVER_DELAY_MS = 150;

// 12위 / 24명 → '상위 50%'
const topPercent = (ranks, participants) =>
    ranks && participants ? `상위 ${Math.max(1, Math.ceil((ranks / participants) * 100))}%` : null;

function TierName({tier, className = ''}) {
    return <span className={`font-semibold ${TIER_TEXT[tier] ?? 'text-fg-3'} ${className}`}>{TIER_NAMES[tier] ?? '—'}</span>;
}

function HistoryCard({user, participants, x, y}) {
    const detail = useRankDetail(user.userId);
    // 세로 스크롤바를 뺀 폭 (innerWidth는 스크롤바를 포함해 카드가 스크롤바 밑으로 들어갔다)
    const viewW = document.documentElement.clientWidth;
    const width = Math.min(CARD_W, viewW - 16);
    const preferred = x + GAP + width > viewW ? x - GAP - width : x + GAP;
    const left = Math.max(8, Math.min(preferred, viewW - width - 8)); // 좁은 창에서는 커서 양쪽 어디에도 다 들어가지 않는다
    const vertical = window.innerHeight - y < FLIP_BELOW ? {bottom: window.innerHeight - y + GAP} : {top: y + GAP};
    const seasons = detail?.pastSeasonTiers ?? [];
    const top = topPercent(user.ranks, participants);

    return (
        <div
            role="tooltip"
            style={{left, width, ...vertical}}
            className="pointer-events-none fixed z-50 flex flex-col gap-3 rounded-2xl border border-line bg-surface p-4 shadow-xl"
        >
            <div className="flex items-baseline justify-between gap-2">
                <span className="truncate text-sm font-bold">{user.name}</span>
                {detail?.seasonCount > 0 && <span className="num shrink-0 text-xs text-fg-3">{detail.seasonCount}시즌 참여</span>}
            </div>

            {/* 지금 보고 있는 시즌의 기록 (순위표 데이터) */}
            <div className="flex items-center gap-3 rounded-xl bg-surface-2 p-3">
                <TierBadge tier={user.tier} size={32}/>
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="truncate text-[11px] text-fg-3">{user.seasonName ?? '이번 시즌'}</span>
                    <TierName tier={user.tier} className="truncate text-sm"/>
                </div>
                <div className="num flex flex-col items-end gap-0.5 text-right">
                    <span className="text-sm font-bold">{user.ranks}위{top && <span className="ml-1 text-xs font-medium text-fg-3">{top}</span>}</span>
                    <span className="text-xs text-fg-2">{formatKRW(user.totalValue)}원</span>
                </div>
            </div>

            {detail === null && <p className="py-2 text-center text-xs text-fg-3">불러오는 중…</p>}

            {detail?.highestTier > 0 && (
                <div className="flex items-center gap-2 text-xs">
                    <span className="text-fg-3">최고 티어</span>
                    <TierBadge tier={detail.highestTier} size={18}/>
                    <TierName tier={detail.highestTier}/>
                </div>
            )}

            {detail && (
                <div className="flex flex-col">
                    <span className="pb-1 text-[11px] text-fg-3">최근 참여 시즌</span>
                    {seasons.length === 0 && <p className="py-2 text-xs text-fg-3">지난 시즌 기록이 없어요.</p>}
                    <ul>
                        {seasons.map((s) => (
                            <li key={s.name} className="flex items-center gap-2 border-t border-line py-1.5 text-xs first:border-t-0">
                                <TierBadge tier={s.tier} size={20}/>
                                <span className="flex min-w-0 flex-1 flex-col">
                                    <span className="truncate text-fg-2">{s.name}</span>
                                    <TierName tier={s.tier}/>
                                </span>
                                <span className="num flex flex-col items-end text-right">
                                    <span className="font-semibold">
                                        {s.ranks != null ? `${s.ranks}위` : '—'}
                                        {topPercent(s.ranks, s.participants) && <span className="ml-1 font-normal text-fg-3">{topPercent(s.ranks, s.participants)}</span>}
                                    </span>
                                    {s.totalValue != null && <span className="text-fg-3">{formatKRW(s.totalValue)}원</span>}
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}

/**
 * 감싼 프로필 사진에 마우스를 잠깐 올리면 커서 옆에 참가자 카드(현재 시즌·최고 티어·최근 참여 시즌)를 띄운다
 * @param user 순위표 행 (tier, ranks, totalValue, seasonName)
 * @param participants 그 시즌 참가자 수 (상위 % 계산용)
 */
export default function TierHistoryHover({user, participants, children, className = ''}) {
    const [pos, setPos] = useState(null);
    const last = useRef(null);
    const timer = useRef(null);

    useEffect(() => () => clearTimeout(timer.current), []);

    // 터치로 누를 때 생기는 가짜 호버는 무시한다 (터치 화면은 행을 눌러 시트로 본다)
    const onEnter = (e) => {
        if (e.pointerType !== 'mouse') return;
        last.current = {x: e.clientX, y: e.clientY};
        timer.current = setTimeout(() => setPos(last.current), HOVER_DELAY_MS);
    };
    const onMove = (e) => {
        if (e.pointerType !== 'mouse') return;
        last.current = {x: e.clientX, y: e.clientY};
        if (pos) setPos(last.current);
    };
    const onLeave = () => {
        clearTimeout(timer.current);
        setPos(null);
    };

    return (
        <span onPointerEnter={onEnter} onPointerMove={onMove} onPointerLeave={onLeave} className={`inline-flex ${className}`}>
            {children}
            {pos && createPortal(<HistoryCard user={user} participants={participants} x={pos.x} y={pos.y}/>, document.body)}
        </span>
    );
}
