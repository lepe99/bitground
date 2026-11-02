import React from "react";
import '../../styles/community/PostProfile.css';

import bronze from '../../assets/images/bronze.webp';
import silver from '../../assets/images/silver.webp';
import gold from '../../assets/images/gold.webp';
import platinum from '../../assets/images/platinum.webp';
import diamond from '../../assets/images/diamond.webp';
import master from '../../assets/images/master.webp';
import grandmaster from '../../assets/images/grandmaster.webp';

const tierLogoImageMap = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster
};

const tierNameMap = {
    1: 'Bronze',
    2: 'Silver',
    3: 'Gold',
    4: 'Platinum',
    5: 'Diamond',
    6: 'Master',
    7: 'Grandmaster'
};

export default function PostProfile({ profileImage, name, highestTier, pastSeasonTiers }) {
    return (
        <div className="post-profile-card">
            <div className="post-profile-header">
                <img src={profileImage} alt={`${name}의 프로필`} className="profile-image" />
                <div className="profile-info">
                    <div className="profile-name">{name}</div>
                    <div className="profile-tier">
                        {highestTier ? (
                            <>
                                최고 티어:
                                <img
                                    src={tierLogoImageMap[highestTier]}
                                    alt="티어"
                                    className={highestTier === 7 ? "tier-icon grandmaster-icon" : "tier-icon"}
                                />
                                <strong className={highestTier === 7 ? "tier-rank grandmaster" : "tier-rank"}>
                                    {tierNameMap[highestTier]}
                                </strong>
                            </>
                        ) : (
                            <strong className="tier-rank">참여한 시즌이 없습니다.</strong>
                        )}
                    </div>
                </div>
            </div>

            <div className="post-profile-tiers">
                <ul className="tiers-list">
                    {pastSeasonTiers?.map((tier, idx) => (
                        <li key={idx} className="tier-item">
                            <div className="tier-season">{tier.seasonName}</div>
                            <div className="tier-rank-wrapper">
                                <img
                                    src={tierLogoImageMap[tier.tier]}
                                    alt={tierNameMap[tier.tier]}
                                    className={tier.tier === 7 ? "grandmaster-icon" : "tier-icon"}
                                />
                                <div className={`tier-rank ${tier.tier === 7 ? "grandmaster" : ""}`}>
                                    {tierNameMap[tier.tier]}
                                </div>
                            </div>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
}