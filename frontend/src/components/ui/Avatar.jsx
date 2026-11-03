import TierBadge from './TierBadge.jsx';
import {TIER_BORDER} from '../../constants/tiers.js';

// 크기별 치수. 배지는 사진 우측 하단에 겹친다
const SIZES = {
    xs: {box: 'size-8', ring: 'border-2 p-0.5', text: 'text-[11px]', badge: 16, pos: '-right-1 -bottom-0.5'},
    sm: {box: 'size-9', ring: 'border-2 p-0.5', text: 'text-xs', badge: 18, pos: '-right-1 -bottom-0.5'},
    md: {box: 'size-10', ring: 'border-2 p-0.5', text: 'text-xs', badge: 20, pos: '-right-1.5 -bottom-1'},
    lg: {box: 'size-[60px]', ring: 'border-[2.5px] p-[3px]', text: 'text-lg', badge: 26, pos: '-right-1.5 -bottom-1'},
    xl: {box: 'size-[72px]', ring: 'border-[3px] p-[3px]', text: 'text-xl', badge: 28, pos: '-right-1.5 -bottom-1'},
    '2xl': {box: 'size-24', ring: 'border-[3px] p-[3px]', text: 'text-2xl', badge: 32, pos: '-right-1.5 -bottom-1'},
};

/**
 * 프로필 사진 + 티어 색 링 + 우측 하단 티어 배지
 * @param outline 배지 테두리 색. 아바타가 놓인 바탕색과 맞춘다 (기본: 카드 바탕)
 */
export default function Avatar({src, name = '', tier = 0, size = 'md', badge = true, outline = 'text-surface', className = ''}) {
    const s = SIZES[size] ?? SIZES.md;
    const ring = TIER_BORDER[tier] ?? 'border-line';
    const initial = name.trim().charAt(0).toUpperCase() || '?';

    return (
        <span className={`relative inline-block shrink-0 ${s.box} ${className}`}>
            <span className={`block size-full rounded-full ${s.ring} ${ring}`}>
                {src ? (
                    <img src={src} alt="" className="size-full rounded-full object-cover"/>
                ) : (
                    <span className={`flex size-full items-center justify-center rounded-full bg-surface-2 font-bold text-fg-2 ${s.text}`} aria-hidden="true">
                        {initial}
                    </span>
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
