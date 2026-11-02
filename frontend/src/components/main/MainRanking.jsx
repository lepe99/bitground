import React, { useEffect, useState } from 'react';
import { fetchPublicRanking } from '../../api/fetchPublicRanking';
import '../../styles/main/RankingPreview.css';
import bronze from '../../assets/images/bronze.png';
import silver from '../../assets/images/silver.png';
import gold from '../../assets/images/gold.png';
import platinum from '../../assets/images/platinum.png';
import diamond from '../../assets/images/diamond.png';
import master from '../../assets/images/master.png';
import grandmaster from '../../assets/images/grandmaster.png';

const tierImageMap = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster,
};

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
                            <img src={tierImageMap[user.tier]} alt="티어" className="tier-img" />
                            <span className="total-value">{user.totalValue.toLocaleString()}원</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}