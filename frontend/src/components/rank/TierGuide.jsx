import Card from '../ui/Card.jsx';
import TierBadge from '../ui/TierBadge.jsx';
import {TIER_NAMES, TIER_RULES} from '../../constants/tiers.js';

const ORDER = [7, 6, 5, 4, 3, 2, 1];

/** 티어 기준표 (시즌 종료 시점 순위로 정해진다). myTier를 굵게 표시 */
export default function TierGuide({myTier}) {
    return (
        <Card className="flex flex-col px-5 py-4 md:px-6">
            <h2 className="pb-1.5 text-base font-bold">티어 기준</h2>
            <p className="pb-2 text-xs leading-relaxed text-fg-3">매시 갱신되는 총 자산 순위로 정해지고, 시즌이 끝나면 확정돼요.</p>
            <ul>
                {ORDER.map((tier) => (
                    <li key={tier} className="flex items-center gap-3 py-1.5">
                        <TierBadge tier={tier} size={28}/>
                        <span className={`flex-1 text-sm ${tier === myTier ? 'font-bold text-fg' : 'text-fg-2'}`}>{TIER_NAMES[tier]}</span>
                        <span className="text-[13px] text-fg-3">{TIER_RULES[tier]}</span>
                    </li>
                ))}
            </ul>
        </Card>
    );
}
