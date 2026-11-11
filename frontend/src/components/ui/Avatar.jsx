import TierBadge from './TierBadge.jsx';
import DefaultAvatar from './DefaultAvatar.jsx';
import {TIER_BORDER} from '../../constants/tiers.js';

// 크기별 치수. 배지는 사진 우측 하단에 겹친다
const SIZES = {
    xs: {box: 'size-8', ring: 'border-2 p-0.5', badge: 16, pos: '-right-1 -bottom-0.5'},
    sm: {box: 'size-9', ring: 'border-2 p-0.5', badge: 18, pos: '-right-1 -bottom-0.5'},
    md: {box: 'size-10', ring: 'border-2 p-0.5', badge: 20, pos: '-right-1.5 -bottom-1'},
    lg: {box: 'size-[60px]', ring: 'border-[2.5px] p-[3px]', badge: 26, pos: '-right-1.5 -bottom-1'},
    xl: {box: 'size-[72px]', ring: 'border-[3px] p-[3px]', badge: 28, pos: '-right-1.5 -bottom-1'},
    '2xl': {box: 'size-24', ring: 'border-[3px] p-[3px]', badge: 32, pos: '-right-1.5 -bottom-1'},
};

/**
 * 프로필 사진 + 티어 색 링 + 우측 하단 티어 배지. 사진이 없으면 기본 프로필 사진
 * 옆에 이름이 함께 나오므로 사진은 꾸밈으로 두고 화면 낭독기에 읽히지 않게 한다
 * @param outline 배지 테두리 색. 아바타가 놓인 바탕색과 맞춘다 (기본: 카드 바탕)
 */
export default function Avatar({src, tier = 0, size = 'md', badge = true, outline = 'text-surface', className = ''}) {
    const s = SIZES[size] ?? SIZES.md;
    const ring = TIER_BORDER[tier] ?? 'border-line';

    return (
        <span className={`relative inline-block shrink-0 ${s.box} ${className}`}>
            <span className={`block size-full rounded-full ${s.ring} ${ring}`}>
                {src ? (
                    <img src={src} alt="" className="size-full rounded-full object-cover"/>
                ) : (
                    // 사진이 없으면 기본 프로필 사진
                    <DefaultAvatar/>
                )}
            </span>
            {badge && (
                <TierBadge
                    tier={tier}
                    size={s.badge}
                    outline={outline}
                    className={`absolute ${s.pos} drop-shadow-[0_1px_1.5px_rgba(0,0,0,0.3)]`}
                />
            )}
        </span>
    );
}
