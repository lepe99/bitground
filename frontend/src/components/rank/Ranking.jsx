import React, {useEffect, useState,useRef} from 'react';
import api from "../../api/axiosConfig.js";
import DistributionChart from './DistributionChart.jsx';
import '../../styles/rank/Ranking.css';
import {useAuth} from '../../auth/useAuth.js';
import Loading from "../Loading.jsx";
import PastRankingList from "./PastRankingList.jsx";
import CurrentRankingList from "./CurrentRankingList.jsx";
import TierInfoTooltip from "./TierInfoTooltip.jsx";

export default function Ranking() {
    const [pastRankingsMap, setPastRankingsMap] = useState({});
    const [rankings, setRankings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pastLoading, setPastLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selectedSeason, setSelectedSeason] = useState(0);
    const [rankUpdatedTime, setRankUpdatedTime] = useState(null);
    const [seasons, setSeasons] = useState([]);
    const [currentSeasonName, setCurrentSeasonName] = useState('');
    const [currentUserAsset, setCurrentUserAsset] = useState(0);
    const [userAssets, setUserAssets] = useState([]);
    const tooltipRef = useRef(null);
    const questionIconRef = useRef(null);
    const [showTooltip, setShowTooltip] = useState(false);
    const [tooltipPosition, setTooltipPosition] = useState({ top: 0, left: 0 });

    // 툴팁 닫기용 클릭 아웃사이드
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (
                tooltipRef.current &&
                !tooltipRef.current.contains(e.target) &&
                questionIconRef.current &&
                !questionIconRef.current.contains(e.target)
            ) {
                setShowTooltip(false);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);


    const {user} = useAuth();

    //  상세 실시간 랭킹 로딩 (툴팁용 포함)
    useEffect(() => {
        const fetchDetailedRankings = async () => {
            try {
                const response = await api.get(`/rank/current`);
                const data = Array.isArray(response.data) ? response.data : [];

                setRankings(data);

                // 자산 정보
                const assets = data.map(item => item.totalValue);
                setUserAssets(assets);

                // 내 자산
                const userRank = data.find(item => item.userId === user.id);
                if (userRank) {
                    setCurrentUserAsset(userRank.totalValue);
                }

                // 업데이트 시간
                const timestamp = data[0]?.updatedAt;
                const date = new Date(timestamp);
                if (!isNaN(date.getTime())) {
                    setRankUpdatedTime(date); // ← 문자열 말고 Date 객체로 저장
                } else {
                    setRankUpdatedTime(null);
                }

            } catch (error) {
                console.error('상세 랭킹 로딩 실패:', error);
                setError('실시간 랭킹 데이터를 불러오지 못했습니다.');
            } finally {
                setLoading(false);
            }
        };
        fetchDetailedRankings();
    }, [user.id]);

    // 시즌 정보 로딩
    useEffect(() => {
        const fetchSeasons = async () => {
            try {
                const response = await api.get('/seasons');

                const seasonData = response.data
                    .filter(season => season.status !== 'PENDING')
                    .map(season => ({
                        id: season.id,
                        name: season.name,
                        startAt: season.startAt,
                        endAt: season.endAt
                    }));

                if (response.data[0].status === 'PENDING') {
                    setCurrentSeasonName(response.data[0].name);
                }

                setSeasons(seasonData);
                if (response.data && response.data[0].status === 'PENDING' && response.data[1].status === 'COMPLETED') {
                    setSelectedSeason(response.data[1]?.id);
                } else {
                    console.warn('현재 시즌 정보가 없습니다.');
                }
            } catch (error) {
                console.error('현재 시즌 정보 로딩 실패:', error);
            }
        };
        fetchSeasons();
    }, []);

    // 과거 시즌 랭킹 로딩
    useEffect(() => {
        const fetchPast = async () => {
            if (!selectedSeason || pastRankingsMap[selectedSeason]) {
                setPastLoading(false);
                return;
            }

            setPastLoading(true);
            try {
                const response = await api.get(`/rank/${selectedSeason}`);
                const seasonData = Array.isArray(response.data) ? response.data : [];

                setPastRankingsMap(prevMap => ({
                    ...prevMap,
                    [selectedSeason]: seasonData
                }));

            } catch (err) {
                console.error('과거 시즌 랭킹 로딩 실패:', err);
            } finally {
                setPastLoading(false);
            }
        };
        fetchPast();
    }, [selectedSeason, pastRankingsMap]);

    if (loading) return <div className="ranking-page"><Loading/></div>;
    if (error) return <div className="ranking-page"><div className="ranking-container">에러: {error}</div></div>;

    return (
        <div className="ranking-page">
            <div className="ranking-live-wrapper">
                <CurrentRankingList
                    rankUpdatedTime={rankUpdatedTime}
                    currentSeasonName={currentSeasonName}
                    rankings={rankings}
                    questionIconRef={questionIconRef}
                    onClickQuestionIcon={() => {
                        const rect = questionIconRef.current.getBoundingClientRect();

                        setTooltipPosition({
                            top: rect.bottom + window.scrollY - 55, // 아래에 8px 여유 주기
                            left: rect.left + window.scrollX + 215
                        });
                        setShowTooltip(true);
                    }}
                />
                {showTooltip && (
                    <div
                        ref={tooltipRef}
                        style={{
                            position: 'absolute',
                            top: tooltipPosition.top,
                            left: tooltipPosition.left,
                            zIndex: 1000,
                        }}
                    >
                        <TierInfoTooltip />
                    </div>
                )}
            </div>
            <div className="ranking-content-wrapper">
                <DistributionChart userAssets={userAssets} currentUserAsset={currentUserAsset} />

                {selectedSeason ? (
                    <PastRankingList
                        pastLoading={pastLoading}
                        pastRankingsMap={pastRankingsMap}
                        seasons={seasons}
                        selectedSeason={selectedSeason}
                        setSelectedSeason={setSelectedSeason}
                    />
                ) : (
                    <Loading />
                )}
            </div>
        </div>
    );
}