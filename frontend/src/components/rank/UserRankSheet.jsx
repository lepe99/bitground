import {useEffect, useState} from 'react';
import Sheet from '../ui/Sheet.jsx';
import Avatar from '../ui/Avatar.jsx';
import TierBadge from '../ui/TierBadge.jsx';
import {fetchUserRankDetail} from '../../api/rankApi.js';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

// 같은 사용자를 다시 열면 서버에 다시 묻지 않는다
const cache = new Map();

/** 순위표에서 누른 참가자: 최고 티어와 지난 시즌 티어 */
export default function UserRankSheet({user, onClose}) {
    const [detail, setDetail] = useState(null);

    useEffect(() => {
        if (!user) return;
        if (cache.has(user.userId)) {
            setDetail(cache.get(user.userId));
            return;
        }
        setDetail(null);
        fetchUserRankDetail(user.userId)
            .then((d) => {
                cache.set(user.userId, d);
                setDetail(d);
            })
            .catch((error) => {
                console.error('참가자 정보 조회 실패:', error);
                setDetail({highestTier: null, pastSeasonTiers: []});
            });
    }, [user]);

    return (
        <Sheet open={user != null} onClose={onClose} title="참가자 정보">
            {user && (
                <div className="flex flex-col gap-4 px-2 pb-2">
                    <div className="flex items-center gap-3.5">
                        <Avatar src={user.profileImage} name={user.name} tier={user.tier} size="lg" outline="text-bg"/>
                        <div className="flex min-w-0 flex-col gap-0.5">
                            <span className="truncate text-lg font-extrabold">{user.name}</span>
                            <span className="num text-sm text-fg-2">
                                {user.ranks}위 · <span className={TIER_TEXT[user.tier]}>{TIER_NAMES[user.tier]}</span> · {formatKRW(user.totalValue)}원
                            </span>
                        </div>
                    </div>
                    <div className="rounded-2xl bg-surface p-4">
                        <span className="text-xs text-fg-3">최고 티어</span>
                        {detail === null ? (
                            <p className="py-2 text-sm text-fg-3">불러오는 중…</p>
                        ) : detail.highestTier ? (
                            <div className="flex items-center gap-2 pt-1.5">
                                <TierBadge tier={detail.highestTier} size={28}/>
                                <span className={`font-bold ${TIER_TEXT[detail.highestTier]}`}>{TIER_NAMES[detail.highestTier]}</span>
                            </div>
                        ) : (
                            <p className="pt-1 text-sm text-fg-3">지난 시즌 기록이 없어요.</p>
                        )}
                    </div>
                    {detail?.pastSeasonTiers?.length > 0 && (
                        <div className="rounded-2xl bg-surface p-4">
                            <span className="text-xs text-fg-3">지난 시즌</span>
                            <ul className="pt-1">
                                {detail.pastSeasonTiers.map((s) => (
                                    <li key={s.name} className="flex items-center gap-2.5 py-1.5">
                                        <TierBadge tier={s.tier} size={22}/>
                                        <span className="flex-1 text-sm text-fg-2">{s.name}</span>
                                        <span className={`text-sm font-semibold ${TIER_TEXT[s.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[s.tier] ?? '—'}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}
        </Sheet>
    );
}
