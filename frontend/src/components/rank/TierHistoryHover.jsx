import {useState} from 'react';
import {createPortal} from 'react-dom';
import TierBadge from '../ui/TierBadge.jsx';
import {useRankDetail} from './useRankDetail.js';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';

// 커서에서 떨어뜨리는 거리와 카드 크기 (화면 끝에서는 반대쪽으로 넘긴다)
const GAP = 16;
const CARD_W = 240;
const CARD_H = 230;

function HistoryCard({user, x, y}) {
    const detail = useRankDetail(user.userId);
    const left = x + GAP + CARD_W > window.innerWidth ? x - GAP - CARD_W : x + GAP;
    const top = Math.max(8, Math.min(y + GAP, window.innerHeight - CARD_H - 8));
    const seasons = detail?.pastSeasonTiers ?? [];

    return (
        <div
            role="tooltip"
            style={{left, top, width: CARD_W}}
            className="pointer-events-none fixed z-50 rounded-2xl border border-line bg-surface p-4 shadow-xl"
        >
            <div className="flex items-baseline justify-between gap-2 pb-2">
                <span className="truncate text-sm font-bold">{user.name}</span>
                <span className="shrink-0 text-xs text-fg-3">지난 5시즌</span>
            </div>
            {detail === null && <p className="py-4 text-center text-xs text-fg-3">불러오는 중…</p>}
            {detail && seasons.length === 0 && <p className="py-4 text-center text-xs text-fg-3">지난 시즌 기록이 없어요.</p>}
            <ul>
                {seasons.map((s) => (
                    <li key={s.name} className="flex items-center gap-2 py-1">
                        <TierBadge tier={s.tier} size={20}/>
                        <span className="min-w-0 flex-1 truncate text-xs text-fg-2">{s.name}</span>
                        <span className={`text-xs font-semibold ${TIER_TEXT[s.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[s.tier] ?? '—'}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

/** 감싼 프로필 사진에 마우스를 올리면 커서 옆에 그 참가자의 지난 5시즌 티어를 띄운다 */
export default function TierHistoryHover({user, children, className = ''}) {
    const [pos, setPos] = useState(null);
    // 터치로 누를 때 생기는 가짜 호버는 무시한다 (터치 화면은 행을 눌러 시트로 본다)
    const follow = (e) => {
        if (e.pointerType === 'mouse') setPos({x: e.clientX, y: e.clientY});
    };

    return (
        <span
            onPointerEnter={follow}
            onPointerMove={follow}
            onPointerLeave={() => setPos(null)}
            className={`inline-flex ${className}`}
        >
            {children}
            {pos && createPortal(<HistoryCard user={user} x={pos.x} y={pos.y}/>, document.body)}
        </span>
    );
}
