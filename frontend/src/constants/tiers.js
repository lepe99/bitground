// 티어(1~7) 이름과 이미지. 0은 랭킹에 없는 사용자(Unranked)
import bronze from '../assets/images/bronze.webp';
import silver from '../assets/images/silver.webp';
import gold from '../assets/images/gold.webp';
import platinum from '../assets/images/platinum.webp';
import diamond from '../assets/images/diamond.webp';
import master from '../assets/images/master.webp';
import grandmaster from '../assets/images/grandmaster.webp';
import bronzeLine from '../assets/images/bronze_line.webp';
import silverLine from '../assets/images/silver_line.webp';
import goldLine from '../assets/images/gold_line.webp';
import platinumLine from '../assets/images/platinum_line.webp';
import diamondLine from '../assets/images/diamond_line.webp';
import masterLine from '../assets/images/master_line.webp';
import grandmasterLine from '../assets/images/grandmaster_line.webp';

export const TIER_NAMES = {
    1: 'Bronze',
    2: 'Silver',
    3: 'Gold',
    4: 'Platinum',
    5: 'Diamond',
    6: 'Master',
    7: 'Grandmaster',
};

// 티어 배지 (랭킹·툴팁·마이페이지)
export const TIER_BADGES = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster,
};

// 프로필 사진 테두리 (게시글·랭킹 목록)
export const TIER_FRAMES = {
    1: bronzeLine,
    2: silverLine,
    3: goldLine,
    4: platinumLine,
    5: diamondLine,
    6: masterLine,
    7: grandmasterLine,
};

// 티어 기준 (랭킹 페이지 안내)
export const TIER_RULES = {
    1: '하위 30%',
    2: '상위 70%',
    3: '상위 45%',
    4: '상위 25%',
    5: '상위 10%',
    6: '2~3위',
    7: '1위',
};

/*
 * SVG 티어 배지 (TierBadge). 48×48 기준.
 * from/to: 육각형 그라데이션, dark: 엠블럼 음영 색.
 * 엠블럼은 흰 면(fill) 위에 면 분할 음영 3단계(soft < mid < deep)를 겹친다. 빛은 왼쪽 위에서.
 * 동전 → 동전 두 닢 → 골드바 → 크리스털 → 다이아몬드 → 별 → 왕관 순으로 가치가 올라간다.
 */
export const TIER_EMBLEMS = {
    1: {from: '#E3A774', to: '#9A5A2B', dark: '#9A5A2B',
        fill: 'M24 14.5 A9.5 9.5 0 1 1 23.99 14.5 Z',
        soft: 'M24 16.4 A7.6 7.6 0 1 1 23.99 16.4 Z',
        mid: 'M29.37 18.63 A7.6 7.6 0 0 1 18.63 29.37 Z',
        deep: 'M30.72 17.28 A9.5 9.5 0 0 1 17.28 30.72 L18.63 29.37 A7.6 7.6 0 0 0 29.37 18.63 Z'},
    2: {from: '#E4E9EF', to: '#8C96A4', dark: '#6B7584',
        fill: 'M20 19 A7 7 0 1 1 19.99 19 Z M28 15 A7 7 0 1 1 27.99 15 Z',
        soft: 'M28 16.6 A5.4 5.4 0 1 1 27.99 16.6 Z',
        mid: 'M21.59 19.19 A7 7 0 1 0 26.41 28.81 A7 7 0 0 1 21.59 19.19 Z',
        deep: 'M32.95 17.05 A7 7 0 0 1 23.05 26.95 L24.18 25.82 A5.4 5.4 0 0 0 31.82 18.18 Z'},
    3: {from: '#FAD86B', to: '#C08A0E', dark: '#A87708',
        fill: 'M13.5 31 L18 18 H30 L34.5 31 Z',
        soft: 'M13.5 31 L18 18 L20 23 L17 31 Z',
        mid: 'M17 31 L20 23 H28 L31 31 Z',
        deep: 'M34.5 31 L30 18 L28 23 L31 31 Z'},
    4: {from: '#8FE3D6', to: '#1E8C80', dark: '#1E8C80',
        fill: 'M24 11 L30 16 V32 L24 37 L18 32 V16 Z',
        soft: 'M18 16 L24 20 V37 L18 32 Z',
        mid: 'M24 11 L30 16 L24 20 Z',
        deep: 'M24 20 L30 16 V32 L24 37 Z'},
    5: {from: '#9CC2FF', to: '#2F64D6', dark: '#2F64D6',
        fill: 'M14 20 L19 14 H29 L34 20 L24 35 Z',
        soft: 'M14 20 L19 14 L21.5 20 Z M21.5 20 H26.5 L24 35 Z',
        mid: 'M29 14 L34 20 H26.5 Z M14 20 H21.5 L24 35 Z',
        deep: 'M26.5 20 H34 L24 35 Z'},
    6: {from: '#C9B6FF', to: '#6D3FE0', dark: '#6D3FE0',
        fill: 'M24 12 L27.1 20.2 L35.8 20.5 L29 25.9 L31.4 34.3 L24 29.5 L16.6 34.3 L19 25.9 L12.2 20.5 L20.9 20.2 Z',
        soft: 'M24 24.5 L24 12 L27.1 20.2 Z M24 24.5 L12.2 20.5 L20.9 20.2 Z',
        mid: 'M24 24.5 L35.8 20.5 L29 25.9 Z M24 24.5 L16.6 34.3 L19 25.9 Z',
        deep: 'M24 24.5 L31.4 34.3 L24 29.5 Z'},
    7: {from: '#E8566F', to: '#8E1430', dark: '#8E1430', rim: '#F5C542',
        fill: 'M13 32 L11.5 17.5 L18.5 23 L24 12.5 L29.5 23 L36.5 17.5 L35 32 Z M11.5 14 A1.7 1.7 0 1 1 11.49 14 Z M24 9 A1.7 1.7 0 1 1 23.99 9 Z M36.5 14 A1.7 1.7 0 1 1 36.49 14 Z',
        soft: 'M24 12.5 L29.5 23 L24 28 Z M36.5 17.5 L35.4 28 H31.2 Z',
        mid: 'M12.6 28 H35.4 L35 32 H13 Z',
        deep: 'M29.5 28 H35.4 L35 32 H29.5 Z'},
};

// 티어 색 Tailwind 클래스 (프로필 링·티어 이름). 클래스 이름을 그대로 써야 빌드에 포함된다.
export const TIER_TEXT = {
    1: 'text-tier-1', 2: 'text-tier-2', 3: 'text-tier-3', 4: 'text-tier-4',
    5: 'text-tier-5', 6: 'text-tier-6', 7: 'text-tier-7',
};
export const TIER_BORDER = {
    1: 'border-tier-1', 2: 'border-tier-2', 3: 'border-tier-3', 4: 'border-tier-4',
    5: 'border-tier-5', 6: 'border-tier-6', 7: 'border-tier-7',
};
