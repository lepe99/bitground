import Avatar from '../ui/Avatar.jsx';
import TierHistoryHover from './TierHistoryHover.jsx';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

/** 1~3위 단상: 가운데 1위를 크게. onSelect가 없으면 누를 수 없고 사진 호버로 티어 이력을 본다 */
export default function RankPodium({top, myUserId, onSelect, participants}) {
    if (top.length === 0) return null;
    const order = [top[1], top[0], top[2]].filter(Boolean);
    const Item = onSelect ? 'button' : 'div';

    return (
        <section aria-label="상위 3명" className="grid grid-cols-3 items-end gap-2 md:gap-5">
            {order.map((u) => {
                const first = u.ranks === 1;
                const mine = u.userId === myUserId;
                return (
                    <Item
                        key={u.userId}
                        {...(onSelect && {type: 'button', onClick: () => onSelect(u)})}
                        aria-current={mine || undefined}
                        className={`flex min-w-0 flex-col items-center gap-1 rounded-2xl px-2 pb-3.5 text-center md:gap-2 md:rounded-[20px] md:border md:px-6 md:pb-6 ${
                            first ? 'pt-5 md:border-[#F5C542] md:pt-9' : 'pt-3 md:border-line md:pt-6'
                        } ${mine ? 'bg-brand-soft outline-2 outline-brand' : 'bg-surface'}`}
                    >
                        <span className={`num text-xs font-extrabold md:text-sm ${first ? 'text-brand-fg' : 'text-fg-2'}`}>{u.ranks}위</span>
                        <TierHistoryHover user={u} participants={participants} className="my-1">
                            <Avatar src={u.profileImage} name={u.name} tier={u.tier} size={first ? '2xl' : 'xl'} outline={mine ? 'text-brand-soft' : 'text-surface'} className={first ? 'max-md:size-16' : 'max-md:size-[54px]'}/>
                        </TierHistoryHover>
                        <span className="flex w-full min-w-0 items-center justify-center gap-1">
                            <span className="truncate text-[13px] font-semibold md:text-base md:font-bold">{u.name}</span>
                            {mine && <span className="shrink-0 rounded-md border border-brand-fg px-1 text-[10px] font-bold text-brand-fg md:px-1.5 md:text-[11px]">나</span>}
                        </span>
                        <span className={`text-xs font-semibold max-md:hidden md:text-[13px] ${TIER_TEXT[u.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[u.tier]}</span>
                        <span className="num text-[clamp(11px,3.5vw,13px)] font-extrabold md:text-xl">{formatKRW(u.totalValue)}<span className="text-xs font-semibold text-fg-3">원</span></span>
                    </Item>
                );
            })}
        </section>
    );
}
