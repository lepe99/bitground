import React, { useEffect, useRef, useState } from 'react';
import api from '../../api/axiosConfig.js';
import bronzeLine from '../../assets/images/bronze_line.png';
import silverLine from '../../assets/images/silver_line.png';
import goldLine from '../../assets/images/gold_line.png';
import platinumLine from '../../assets/images/platinum_line.png';
import diamondLine from '../../assets/images/diamond_line.png';
import masterLine from '../../assets/images/master_line.png';
import grandmasterLine from '../../assets/images/grandmaster_line.png';
import '../../styles/rank/RankingList.css';
import UserProfileTooltip from "./UserProfileTooltip.jsx";
import bronze from '../../assets/images/bronze.png';
import silver from '../../assets/images/silver.png';
import gold from '../../assets/images/gold.png';
import platinum from '../../assets/images/platinum.png';
import diamond from '../../assets/images/diamond.png';
import master from '../../assets/images/master.png';
import grandmaster from '../../assets/images/grandmaster.png';

const tierImageMap = {
    1: bronzeLine,
    2: silverLine,
    3: goldLine,
    4: platinumLine,
    5: diamondLine,
    6: masterLine,
    7: grandmasterLine
};

const tierLogoImageMap = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster,
};

export default function RankingList({
                                        data,
                                        highlightTop3 = false,
                                        disableHover = false,
                                    }) {
    const [hoverUser, setHoverUser] = useState(null);
    const [position, setPosition] = useState({ x: 0, y: 0 });
    const tooltipRef = useRef(null);
    const hoverTimeout = useRef(null);
    const fetchedDetailedCache = useRef({});

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (tooltipRef.current && !tooltipRef.current.contains(e.target)) {
                setHoverUser(null);
            }
        };
        document.addEventListener('click', handleClickOutside);
        return () => document.removeEventListener('click', handleClickOutside);
    }, []);

    const handleMouseEnter = async (e, item) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const x = rect.left;
        const y = rect.bottom + 8;

        hoverTimeout.current = setTimeout(async () => {
            try {

                if (!fetchedDetailedCache.current[item.userId]) {
                    const response = await api.get(`/rank/detail/${item.userId}`);
                    fetchedDetailedCache.current[item.userId] = response.data;
                }

                const detailed = fetchedDetailedCache.current[item.userId];
                console.log('툴팁 유저 정보:', detailed);

                const tooltipUser = {
                    profileImage: item.profileImage,
                    nickname: item.name,
                    highestTier: detailed?.highestTier ?? null,
                    pastSeasonTiers: detailed?.pastSeasonTiers ?? []
                };

                setPosition({ x, y });
                setHoverUser(tooltipUser);
            } catch (err) {
                console.error('툴팁 유저 정보 불러오기 실패:', err);
            }
        }, 300); // 300ms 이상 유지 시 실행
    };

    const handleMouseLeave = () => {
        clearTimeout(hoverTimeout.current); // 마우스 금방 떼면 호출 취소
        setHoverUser(null);
    };

    return (
        <div className="ranking-list">
            {!data || data.length === 0 ? <div className="no-data">참여한 유저가 없습니다.</div> : ""}
            {data.map((item, index) => {
                const userId = item?.userId ?? 0;
                const name = item?.name ?? 'Unknown User';
                const totalValue = Number(item?.totalValue ?? 0);
                const tier = item?.tier ?? 0;
                const profileImage = item?.profileImage;
                const tierImg = tierImageMap[tier] || bronzeLine;
                const rank = item?.ranks ?? index + 1;

                return (
                    <div
                        key={`${userId}-${index}`}
                        className={`ranking-item ${highlightTop3 && index < 3 ? 'top3' : ''}`}
                        onMouseEnter={(e) => handleMouseEnter(e, item)}
                        onMouseLeave={handleMouseLeave}
                    >
                        <div className="rank-position">{rank}</div>
                        <div className="user-icon">
                            <img src={tierImg} alt={`티어 ${tier}`} className="tier-image" />
                            {profileImage && (
                                <img src={profileImage} alt="프로필" className="rank-profile-image" />
                            )}
                        </div>
                        <div className="user-info">
                            {name}
                            {tierLogoImageMap[tier] && (
                                <img src={tierLogoImageMap[tier]} alt="티어로고" className="tier-logo-icon" />
                            )}
                        </div>
                        <div className="amount">{totalValue.toLocaleString()}원</div>
                    </div>
                );
            })}
            {!disableHover && hoverUser && (
                <div ref={tooltipRef}>
                    <UserProfileTooltip
                        user={hoverUser}
                        visible={!!hoverUser}
                        position={position}
                    />
                </div>
            )}
        </div>
    );
}