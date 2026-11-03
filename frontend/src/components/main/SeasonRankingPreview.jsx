import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import Avatar from '../ui/Avatar.jsx';
import {TIER_NAMES, TIER_TEXT} from '../../constants/tiers.js';
import {formatKRW} from '../../utils/format.js';

/** 시즌 상위 5명 (로그인 없이 조회하는 공개 랭킹). ranking: undefined 불러오는 중, null 실패 */
export default function SeasonRankingPreview({ranking}) {
    const rows = ranking?.rankings ?? [];

    return (
        <Card className="flex flex-col gap-2 p-5 md:p-6">
            <div className="flex items-center justify-between pb-2">
                <h2 className="text-base font-bold md:text-[17px]">{ranking?.seasonName ?? '시즌'} 랭킹</h2>
                <Link to="/rank" className="text-[13px] font-semibold text-brand-fg">전체 보기</Link>
            </div>
            {ranking === undefined && <p className="py-6 text-center text-sm text-fg-3">불러오는 중…</p>}
            {ranking === null && <p className="py-6 text-center text-sm text-fg-3">랭킹을 불러오지 못했어요.</p>}
            {ranking && rows.length === 0 && (
                <p className="py-6 text-center text-sm leading-relaxed text-fg-3">아직 시즌에 참여한 사람이 없어요.<br/>첫 순위의 주인공이 되어 보세요!</p>
            )}
            <ol>
                {rows.map((user, i) => (
                    <li key={`${user.ranks}-${user.name}`} className="flex items-center gap-3 py-[7px]">
                        <span className={`num w-4 text-[13px] font-bold ${i < 3 ? 'text-brand-fg' : 'text-fg-3'}`}>{user.ranks ?? i + 1}</span>
                        <Avatar src={user.profileImage} name={user.name} tier={user.tier} size="md"/>
                        <span className="flex min-w-0 flex-1 flex-col">
                            <span className="truncate text-sm font-semibold">{user.name}</span>
                            <span className={`text-xs font-semibold ${TIER_TEXT[user.tier] ?? 'text-fg-3'}`}>{TIER_NAMES[user.tier] ?? 'Unranked'}</span>
                        </span>
                        <span className="num text-[13px] font-semibold">{formatKRW(user.totalValue)}원</span>
                    </li>
                ))}
            </ol>
            {ranking?.updatedAtText && <p className="pt-1.5 text-xs text-fg-3">{ranking.updatedAtText} · {ranking.minutesLeftText}</p>}
        </Card>
    );
}
