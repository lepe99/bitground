import React from "react";
import '../../styles/community/PostProfile.css';
import {TIER_BADGES, TIER_NAMES} from '../../constants/tiers.js';


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
                                    src={TIER_BADGES[highestTier]}
                                    alt="티어"
                                    className={highestTier === 7 ? "tier-icon grandmaster-icon" : "tier-icon"}
                                />
                                <strong className={highestTier === 7 ? "tier-rank grandmaster" : "tier-rank"}>
                                    {TIER_NAMES[highestTier]}
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
                                    src={TIER_BADGES[tier.tier]}
                                    alt={TIER_NAMES[tier.tier]}
                                    className={tier.tier === 7 ? "grandmaster-icon" : "tier-icon"}
                                />
                                <div className={`tier-rank ${tier.tier === 7 ? "grandmaster" : ""}`}>
                                    {TIER_NAMES[tier.tier]}
                                </div>
                            </div>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
}