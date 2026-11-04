import Avatar from '../ui/Avatar.jsx';
import TierHistoryHover from './TierHistoryHover.jsx';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

/** 1~3위 단상: 가운데 1위를 크게. onSelect가 없으면 누를 수 없고 사진 호버로 티어 이력을 본다 */
export default function RankPodium({top, onSelect, participants}) {
    if (top.length === 0) return null;
    const order = [top[1], top[0], top[2]].filter(Boolean);
    const Item = onSelect ? 'button' : 'div';

    return (
        <section aria-label="상위 3명" className="grid grid-cols-3 items-end gap-2 md:gap-5">
            {order.map((u) => {
                const first = u.ranks === 1;
                return (
                    <Item
                        key={u.userId}
                        {...(onSelect && {type: 'button', onClick: () => onSelect(u)})}
                        className={`flex min-w-0 flex-col items-center gap-1 rounded-2xl bg-surface px-2 pb-3.5 text-center md:gap-2 md:rounded-[20px] md:border md:px-6 md:pb-6 ${
                            first ? 'pt-5 md:border-[#F5C542] md:pt-9' : 'pt-3 md:border-line md:pt-6'
                        }`}
                    >
                        <span className={`num text-xs font-extrabold md:text-sm ${first ? 'text-brand-fg' : 'text-fg-2'}`}>{u.ranks}위</span>
                        <TierHistoryHover user={u} participants={participants} className="my-1">
                            <Avatar src={u.profileImage} name={u.name} tier={u.tier} size={first ? '2xl' : 'xl'} className={first ? 'max-md:size-16' : 'max-md:size-[54px]'}/>
                        </TierHistoryHover>
                        <span className="w-full truncate text-[13px] font-semibold md:text-base md:font-bold">{u.name}</span>
                        <span className={`text-xs font-semibold max-md:hidden md:text-[13px] ${TIER_TEXT[u.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[u.tier]}</span>
                        <span className="num text-[13px] font-extrabold md:text-xl">{formatKRW(u.totalValue)}<span className="text-xs font-semibold text-fg-3">원</span></span>
                    </Item>
                );
            })}
        </section>
    );
}
