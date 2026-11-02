import LoadingSpinner from "../LoadingSpinner.jsx";
import React from 'react';
import RankingList from "./RankingList.jsx";
import '../../styles/rank/PastRankingList.css';

export default function PastRankingList({
                                            seasons,
                                            pastRankingsMap,
                                            pastLoading,
                                            selectedSeason,
                                            setSelectedSeason
                                        }) {
    return (
        <div className="past-ranking-wrapper">
            <div className="section-header">
                <div className="title-and-date-group">
                    <span className="section-title">지난시즌 랭킹</span>
                    <span className="season-date">
                        {seasons.find(s => s.id === selectedSeason)?.startAt?.substring(0, 10)} ~
                        {seasons.find(s => s.id === selectedSeason)?.endAt?.substring(0, 10)}
                    </span>
                </div>
                <select
                    className="season-select"
                    value={selectedSeason}
                    onChange={(e) => setSelectedSeason(Number(e.target.value))}
                >
                    {seasons.map(season => (
                        <option key={season.id} value={season.id}>
                            {season.name}
                        </option>
                    ))}
                </select>
            </div>

            {pastLoading ? (
                <LoadingSpinner />
            ) : (
                <RankingList
                    data={pastRankingsMap[selectedSeason] || []}
                    highlightTop3
                />
            )}
        </div>
    );
}