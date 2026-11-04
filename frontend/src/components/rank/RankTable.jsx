import {useState} from 'react';
import Card from '../ui/Card.jsx';
import Avatar from '../ui/Avatar.jsx';
import TierHistoryHover from './TierHistoryHover.jsx';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

const PAGE = 20;
const COLS = 'md:grid-cols-[56px_minmax(0,1fr)_120px_160px]';

function RankRow({u, mine, onSelect, participants}) {
    const Row = onSelect ? 'button' : 'div';
    return (
        <li className="border-t border-line first:border-t-0 md:first:border-t">
            <Row
                {...(onSelect && {type: 'button', onClick: () => onSelect(u)})}
                aria-current={mine || undefined}
                className={`flex w-full items-center gap-3 px-4 py-2.5 text-left md:grid md:px-5 ${COLS} ${
                    mine ? 'bg-brand-soft shadow-[inset_3px_0_0_var(--color-brand)]' : 'hover:bg-surface-2'
                }`}
            >
                <span className="num w-6 text-[15px] font-bold text-fg-2 md:w-auto md:text-fg">{u.ranks}</span>
                <span className="flex min-w-0 flex-1 items-center gap-3">
                    <TierHistoryHover user={u} participants={participants}>
                        <Avatar src={u.profileImage} name={u.name} tier={u.tier} size="md" outline={mine ? 'text-brand-soft' : 'text-surface'}/>
                    </TierHistoryHover>
                    <span className="flex min-w-0 flex-col">
                        <span className="flex items-center gap-1.5">
                            <span className="truncate text-[15px] font-semibold md:text-sm">{u.name}</span>
                            {mine && <span className="shrink-0 rounded-md border border-brand-fg px-1.5 text-[11px] font-bold text-brand-fg">나</span>}
                        </span>
                        <span className={`text-xs font-semibold md:hidden ${TIER_TEXT[u.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[u.tier]}</span>
                    </span>
                </span>
                <span className={`text-[13px] font-semibold max-md:hidden ${TIER_TEXT[u.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[u.tier]}</span>
                <span className="num text-right text-sm font-semibold">{formatKRW(u.totalValue)}원</span>
            </Row>
        </li>
    );
}

/**
 * 4위부터의 순위표. 내 행은 강조하고(목록 밖이면 끝에 고정), 20명씩 더 보기
 * @param participants 시즌 전체 참가자 수 (호버 카드의 상위 %)
 * @param onSelect 행을 눌렀을 때 (터치 화면용. 없으면 행은 누를 수 없고 사진 호버로 티어 이력을 본다)
 */
export default function RankTable({rows, myUserId, onSelect, participants}) {
    const [shown, setShown] = useState(PAGE);
    const visible = rows.slice(0, shown);
    // 내 순위가 아직 펼치지 않은 쪽에 있으면 보이는 목록 끝에 붙여 둔다 (더 보기로 닿으면 제자리에 나온다)
    const myIndex = myUserId == null ? -1 : rows.findIndex((u) => u.userId === myUserId);
    const pinned = myIndex >= shown ? rows[myIndex] : null;

    return (
        <Card className="overflow-hidden">
            <div className={`hidden gap-3 px-5 py-3 text-xs font-semibold text-fg-3 md:grid ${COLS}`}>
                <span>순위</span>
                <span>참가자</span>
                <span>티어</span>
                <span className="text-right">총 자산</span>
            </div>
            {rows.length === 0 && <p className="px-5 py-10 text-center text-sm text-fg-3">4위 이하 참가자가 아직 없어요.</p>}
            <ol>
                {visible.map((u) => <RankRow key={u.userId} u={u} mine={u.userId === myUserId} onSelect={onSelect} participants={participants}/>)}
                {pinned && (
                    <>
                        <li className="border-t border-line py-1 text-center text-fg-3" aria-hidden="true">⋯</li>
                        <RankRow u={pinned} mine onSelect={onSelect} participants={participants}/>
                    </>
                )}
            </ol>
            {rows.length > shown && (
                <div className="flex justify-center border-t border-line p-3.5">
                    <button type="button" onClick={() => setShown((n) => n + PAGE)} className="h-10 rounded-[10px] border border-line px-5 text-sm font-semibold text-fg-2 hover:bg-surface-2">
                        더 보기 ({rows.length - shown}명)
                    </button>
                </div>
            )}
        </Card>
    );
}
