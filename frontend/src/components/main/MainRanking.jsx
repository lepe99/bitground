import React, { useEffect, useState } from 'react';
import { fetchPublicRanking } from '../../api/rankApi.js';
import '../../styles/main/RankingPreview.css';
import {TIER_BADGES} from '../../constants/tiers.js';

export default function MainRanking() {
    const [rankingData, setRankingData] = useState(null);

    useEffect(() => {
        fetchPublicRanking()
            .then(setRankingData)
            .catch((e) => console.error("랭킹 프리뷰 불러오기 실패", e));
    }, []);

    if (!rankingData) return null;

    return (
        <div className="ranking-preview">
            <div className="ranking-preview-title">
                <h2>{rankingData.seasonName}</h2>
                <div className="ranking-preview-time">
                    <p>{rankingData.updatedAtText}</p>
                    <p>{rankingData.minutesLeftText}</p>
                </div>
            </div>

            {rankingData.rankings.length === 0 ? (
                <div className="no-participants">아직 시즌에 참여한 사람이 없습니다.
                    <br/>시즌에 참여해주세요!</div>
            ) : (
                <ul className="ranking-preview-list">
                    {rankingData.rankings.map((user, idx) => (
                        <li key={idx} className="ranking-user">
                            <span className="ranking-number">{idx + 1}위</span>
                            <img src={user.profileImage} alt={user.name} className="profile-img" />
                            <span className="user-name">{user.name}</span>
                            <img src={TIER_BADGES[user.tier]} alt="티어" className="tier-img" />
                            <span className="total-value">{user.totalValue.toLocaleString()}원</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}