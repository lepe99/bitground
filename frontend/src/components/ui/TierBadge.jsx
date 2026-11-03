import {useId} from 'react';
import {TIER_EMBLEMS, TIER_NAMES} from '../../constants/tiers.js';

const HEX = 'M24 3 L42 13.5 V34.5 L24 45 L6 34.5 V13.5 Z';
const INNER_HEX = 'M24 9 L37 16.5 V31.5 L24 39 L11 31.5 V16.5 Z';

/**
 * 티어 배지 (육각형 + 티어별 엠블럼)
 * @param tier 1~7. 그 밖의 값(0 = 순위 없음)이면 그리지 않는다
 * @param size 픽셀 크기. 40 이상이면 안쪽 테두리를 더한다
 * @param outline 아바타 위에 겹칠 때 바탕과 떼어 놓는 테두리 색 클래스 (예: 'text-surface')
 */
export default function TierBadge({tier, size = 24, outline, className = ''}) {
    const gradientId = 'tier-' + useId().replace(/[^a-zA-Z0-9_-]/g, '');
    const emblem = TIER_EMBLEMS[tier];
    if (!emblem) return null;

    return (
        <svg width={size} height={size} viewBox="0 0 48 48" role="img" aria-label={`${TIER_NAMES[tier]} 티어`} className={className}>
            <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor={emblem.from}/>
                    <stop offset="1" stopColor={emblem.to}/>
                </linearGradient>
            </defs>
            {outline && (
                <path d={HEX} className={outline} fill="currentColor" stroke="currentColor" strokeWidth="7" strokeLinejoin="round"/>
            )}
            <path d={HEX} fill={`url(#${gradientId})`} stroke={emblem.rim ?? 'none'} strokeWidth={emblem.rim ? 3 : 0} strokeLinejoin="round"/>
            {size >= 40 && <path d={INNER_HEX} fill="none" stroke="#FFFFFF" strokeOpacity="0.35" strokeWidth="1"/>}
            <path d={emblem.fill} fill="#FFFFFF"/>
            <path d={emblem.soft} fill={emblem.dark} fillOpacity="0.12" fillRule="evenodd"/>
            <path d={emblem.mid} fill={emblem.dark} fillOpacity="0.22" fillRule="evenodd"/>
            <path d={emblem.deep} fill={emblem.dark} fillOpacity="0.34" fillRule="evenodd"/>
        </svg>
    );
}
