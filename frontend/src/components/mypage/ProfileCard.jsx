import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import Avatar from '../ui/Avatar.jsx';
import {useAuth} from '../../auth/useAuth.js';
import {fetchCurrentRanking} from '../../api/rankApi.js';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';

const PROVIDERS = {google: '구글', naver: '네이버', kakao: '카카오'};

/** 프로필 + 이번 시즌 티어·순위 */
export default function ProfileCard() {
    const {user} = useAuth();
    const [mine, setMine] = useState(undefined);

    useEffect(() => {
        if (!user?.id) return;
        fetchCurrentRanking()
            .then((list) => {
                const me = list.find((r) => r.userId === user.id);
                setMine(me ? {...me, total: list.length} : null);
            })
            .catch(() => setMine(null));
    }, [user?.id]);

    const tier = mine?.tier ?? user?.currentTier ?? user?.tier ?? 0;
    const percentile = mine ? Math.max(1, Math.round((mine.ranks / mine.total) * 100)) : null;

    return (
        <Card className="flex flex-wrap items-center gap-4 p-5 md:gap-5 md:p-6">
            <Avatar src={user?.profileImage} name={user?.name} tier={tier} size="xl" outline="text-surface"/>
            <div className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate text-xl font-extrabold">{user?.name}</span>
                <span className="truncate text-[13px] text-fg-3">
                    {PROVIDERS[user?.provider] ?? user?.provider} 로그인{user?.email ? ` · ${user.email}` : ''}
                </span>
                <span className="num text-sm text-fg-2">
                    {mine
                        ? <>{mine.seasonName} · <strong className={TIER_TEXT[tier]}>{TIER_NAMES[tier]}</strong> <span className="whitespace-nowrap">{mine.ranks}위 (상위 {percentile}%)</span></>
                        : mine === null ? '이번 시즌 순위 집계 전이에요' : ' '}
                </span>
            </div>
            <Link to="/mypage?tab=profile" className="h-10 w-full rounded-xl bg-surface-2 text-center text-sm font-semibold leading-10 text-fg-2 md:hidden">
                프로필 수정
            </Link>
        </Card>
    );
}
