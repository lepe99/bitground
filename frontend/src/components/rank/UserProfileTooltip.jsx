import React, { useEffect, useState } from 'react';
import '../../styles/rank/UserProfileTooltip.css';
import {TIER_BADGES, TIER_NAMES} from '../../constants/tiers.js';

//티어 번호 (1~7) 이미지
export default function UserProfileTooltip({ user, position, isCommunity = false}) {

    const [isVisible, setIsVisible] = useState(false);
    useEffect(() => {
        // 컴포넌트가 마운트되고 나서야 visible true
        const timeout = setTimeout(() => setIsVisible(true), 10); // 1프레임 지연
        return () => clearTimeout(timeout);
    }, []);

    if (!user) return null;

    const highestTier = user.highestTier;
    const highestTierImg = TIER_BADGES[highestTier];
    const tooltipClass = `user-tooltip ${isCommunity ? 'community-tooltip' : ''} ${isVisible ? 'visible' : ''}`;

    return (
        <div
            className={tooltipClass}
            style={{
                top: `${position.y}px`,
                left: `${position.x}px`,
                zIndex: 9999
            }}
        >
            <img src={user.profileImage} alt="프로필" className="tooltip-avatar" />
            <div className="tooltip-info">
                <div className="nickname">{user.nickname}</div>

                <div className={`highest-tier tier-${highestTier || 'none'}`}>
                    {highestTier ? (
                        <div>
                            최고 티어&nbsp;
                            {highestTierImg && (
                                <img
                                    src={highestTierImg}
                                    alt={`티어 ${highestTier}`}
                                    className="tier-icon"
                                />
                            )}
                            &nbsp;{TIER_NAMES[highestTier]}
                        </div>
                    ) : (
                        <p>참여한 기록이 없습니다</p>
                    )}
                </div>

                {/* 지난 시즌 티어 표시 */}
                {Array.isArray(user.pastSeasonTiers) && user.pastSeasonTiers.length > 0 && (
                    <div className="past-tiers">
                        {user.pastSeasonTiers.map((item, idx) => (
                                <div key={idx} className="tier-row">
                                    <img
                                        src={TIER_BADGES[item.tier]}
                                        alt={`티어 ${item.tier}`}
                                        className="tier-icon"
                                    />
                                    <span className="season-text">
                                        {item.name}: {TIER_NAMES[item.tier]}
                                    </span>
                                </div>
                            ))}
                    </div>
                )}
            </div>
        </div>
    );
}