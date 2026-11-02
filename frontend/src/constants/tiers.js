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
