import React, { useEffect, useState } from "react";
import '../../styles/mypage/MyTradeInfo.css';
import MyTradeAI from "./MyTradeAI.jsx";
import { fetchTradeSummary } from "../../api/fetchTradeSummary.js";
import { fetchTradeDetails } from "../../api/fetchTradeDetails.js";
import api from "../../api/axiosConfig.js";
import Loading from "../Loading.jsx";
import bronze from '../../assets/images/bronze.png';
import silver from '../../assets/images/silver.png';
import gold from '../../assets/images/gold.png';
import platinum from '../../assets/images/platinum.png';
import diamond from '../../assets/images/diamond.png';
import master from '../../assets/images/master.png';
import grandmaster from '../../assets/images/grandmaster.png';

// 티어 이름 매핑
const tierNameMap = {
    1: 'Bronze',
    2: 'Silver',
    3: 'Gold',
    4: 'Platinum',
    5: 'Diamond',
    6: 'Master',
    7: 'Grandmaster'
};

// 티어 이미지 매핑
const tierImageMap = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster
};

export default function MyTradeInfo() {
    // 탭 상태 ('분석' 또는 '상세')
    const [tab, setTab] = useState('분석');
    // 시즌 목록 상태
    const [seasons, setSeasons] = useState([]);
    // 선택된 시즌 ID 상태
    const [selectedSeason, setSelectedSeason] = useState(null);
    // 거래 요약 데이터 상태
    const [summary, setSummary] = useState([]);
    // 상세 거래 내역 데이터 상태
    const [details, setDetails] = useState([]);
    // 로딩 상태
    const [loading, setLoading] = useState(true);
    // AI 분석 결과 상태
    const [analysis, setAnalysis] = useState({
        topCoin: '-', // 최다 투자 종목
        bestProfitCoin: '-', // 최고 수익 종목
        worstProfitCoin: '-', // 최저 수익 종목
        totalTrades: 0, // 총 거래 횟수
        totalInvested: 0 // 총 투자 금액
    });
    // 사용자 티어 상태
    const [myTier, setMyTier] = useState(null);
    // AI 조언 데이터 상태 (새로 추가)
    const [aiAdvice, setAiAdvice] = useState(null);

    // 시즌 목록을 불러오는 useEffect
    useEffect(() => {
        const fetchSeasons = async () => {
            try {
                // 서버에서 시즌 목록을 가져옴
                const res = await api.get('/seasons');
                // 완료된 시즌만 필터링
                const completedSeasons = res.data.filter(s => s.status === 'COMPLETED');
                setSeasons(completedSeasons);
                // 완료된 시즌이 있으면 가장 최신 시즌을 기본 선택
                if (completedSeasons.length > 0) {
                    setSelectedSeason(completedSeasons[0].id);
                }
            } catch (err) {
                console.error("시즌 목록 불러오기 실패", err);
            }
        };
        fetchSeasons();
    }, []); // 컴포넌트 마운트 시 한 번만 실행

    // 선택된 시즌이 변경될 때마다 사용자 티어를 불러오는 useEffect
    useEffect(() => {
        if (!selectedSeason) return; // 선택된 시즌이 없으면 실행하지 않음

        const fetchMyTier = async () => {
            try {
                // 서버에서 사용자 티어 정보를 가져옴
                const res = await api.get('/mypage/tier', {
                    params: { seasonId: selectedSeason } // 선택된 시즌 ID를 파라미터로 전달
                });
                setMyTier(res.data); // 티어 정보 상태 업데이트 (예: { tier: 3, tierName: "Gold", tierImage: "..." })
            } catch (err) {
                console.error('내 티어 불러오기 실패', err);
                setMyTier(null); // 실패 시 티어 정보 초기화
            }
        };

        fetchMyTier();
    }, [selectedSeason]); // selectedSeason이 변경될 때마다 실행

    // 선택된 시즌이 변경될 때 AI 조언 데이터를 불러오는 useEffect (새로 추가)
    useEffect(() => {
        if (!selectedSeason) {
            setAiAdvice(null); // 시즌이 선택되지 않으면 AI 조언 초기화
            return;
        }

        const fetchAiAdvice = async () => {
            try {
                const res = await api.get(`/mypage/ai-advice`, {
                    params: { seasonId: selectedSeason }
                });
                // AI 조언 데이터가 있다면 상태 업데이트, 없으면 null
                setAiAdvice(res.data || null);
            } catch (err) {
                console.error("AI 조언 불러오기 실패", err);
                setAiAdvice(null); // 실패 시 AI 조언 초기화
            }
        };
        fetchAiAdvice();
    }, [selectedSeason]); // selectedSeason이 변경될 때마다 실행

    // 요약 + 상세 데이터 동시 로딩
    useEffect(() => {
        const fetchAll = async () => {
            if (!selectedSeason) return; // 선택된 시즌이 없으면 실행하지 않음

            setLoading(true); // 로딩 시작
            try {
                // Promise.all을 사용하여 요약 데이터와 상세 데이터를 비동기적으로 동시에 가져옴
                const [summaryData, detailData] = await Promise.all([
                    fetchTradeSummary(selectedSeason), // 거래 요약 API 호출
                    fetchTradeDetails(selectedSeason)  // 상세 거래 내역 API 호출
                ]);
                setSummary(summaryData); // 요약 데이터 상태 업데이트
                setDetails(detailData);   // 상세 데이터 상태 업데이트

                // AI 분석 관련 로직: 데이터 로딩 후 분석 결과 업데이트
                if (summaryData.length === 0) {
                    // 거래 데이터가 없으면 분석 결과 초기화
                    setAnalysis({
                        topCoin: '-',
                        bestProfitCoin: '-',
                        worstProfitCoin: '-',
                        totalTrades: 0,
                        totalInvested: 0
                    });
                } else {
                    // 총 거래 횟수 계산 (상세 내역의 길이)
                    const totalTrades = detailData.length;
                    // 총 투자 금액 계산 (요약 데이터의 buyAmount 합계)
                    const totalInvested = summaryData.reduce((acc, cur) => acc + cur.buyAmount, 0);

                    // 최다 투자 종목 찾기 (buyAmount가 가장 큰 종목)
                    const topCoin = summaryData.reduce((prev, curr) =>
                        curr.buyAmount > prev.buyAmount ? curr : prev, summaryData[0]).koreanName;

                    // 최고 수익 종목 찾기 (profit이 가장 큰 종목)
                    const bestProfitCoin = summaryData.reduce((prev, curr) =>
                        curr.profit > prev.profit ? curr : prev, summaryData[0]).koreanName;

                    // 최저 수익 종목 찾기 (profit이 가장 작은 종목)
                    const worstProfitCoin = summaryData.reduce((prev, curr) =>
                        curr.profit < prev.profit ? curr : prev, summaryData[0]).koreanName;

                    // 분석 결과 상태 업데이트
                    setAnalysis({
                        topCoin,
                        bestProfitCoin,
                        worstProfitCoin,
                        totalTrades,
                        totalInvested
                    });
                }
            } catch (err) {
                console.error("요약 또는 상세 거래내역 불러오기 실패", err);
                // 에러 발생 시 데이터 및 분석 결과 초기화
                setSummary([]);
                setDetails([]);
                setAnalysis({
                    topCoin: '-',
                    bestProfitCoin: '-',
                    worstProfitCoin: '-',
                    totalTrades: 0,
                    totalInvested: 0
                });
            } finally {
                setLoading(false); // 로딩 종료
            }
        };
        fetchAll();
    }, [selectedSeason]); // selectedSeason이 변경될 때마다 실행

    // summary 또는 details 데이터가 변경될 때마다 분석 결과 다시 계산 (Optional: 위 useEffect에 이미 포함되어 있음)
    // 이 useEffect는 중복될 수 있으므로, 위의 fetchAll 내부에서 한 번만 계산하는 것이 더 효율적입니다.
    // 하지만 팀원 분의 원본 코드에 있었으므로 다시 추가합니다.
    useEffect(() => {
        if (!summary.length || !details.length) return;

        // 최다 투자 종목
        const top = [...summary].sort((a, b) => b.buyAmount - a.buyAmount)[0];

        // 최고 수익 종목
        const best = [...summary].sort((a, b) => b.profit - a.profit)[0];

        // 최저 수익 종목
        const worst = [...summary].sort((a, b) => a.profit - b.profit)[0];

        // 총 거래 횟수
        const totalTrades = details.length;

        // 총 투자 금액 (매수만)
        const totalInvested = details
            .filter(d => d.type === '매수')
            .reduce((sum, d) => sum + d.total, 0);

        setAnalysis({
            topCoin: top?.koreanName || '-',
            bestProfitCoin: best ? `${best.koreanName} (${best.returnRate})` : '-',
            worstProfitCoin: worst ? `${worst.koreanName} (${worst.returnRate})` : '-',
            totalTrades,
            totalInvested
        });

    }, [summary, details]); // summary 또는 details가 변경될 때마다 실행

    return (
        <div className="season-container">
            <div className="season-header">
                <div className="season-header-top">
                    <div>
                        {/* 선택된 시즌의 이름과 기간 표시 */}
                        {(() => {
                            const selected = seasons.find(s => s.id === selectedSeason);
                            if (!selected) return <span className={"season-header-title"}>시즌 정보 없음</span>;
                            return (
                                <div className={"season-header-title"}>
                                    <span>{selected.name}</span><div>({selected.startAt?.substring(0, 10)} ~ {selected.endAt?.substring(0, 10)})</div>
                                </div>
                            );
                        })()}
                    </div>
                    <div className="season-header-info">
                        <div className="season-rank">
                            {/* 사용자 티어 정보 표시 */}
                            {myTier ? (
                                <span className="season-rank-tier">시즌티어 :
                                    <img src={tierImageMap[myTier.tier]} alt={tierNameMap[myTier.tier]} />
                                    {tierNameMap[myTier.tier]}
                                </span>
                            ) : (
                                <span>🏅 랭킹 정보가 없습니다.</span>
                            )}
                        </div>
                        {/* 시즌 선택 드롭다운 */}
                        <select
                            value={selectedSeason || ''}
                            onChange={e => setSelectedSeason(Number(e.target.value))}
                        >
                            {seasons.map(season => (
                                <option key={season.id} value={season.id}>
                                    {season.name}
                                </option>
                            ))}
                        </select>
                    </div>
                </div>

                {/* 탭 메뉴 (시즌분석 / 상세내역) */}
                <div className="season-menu">
                    <button onClick={() => setTab('분석')} className={tab === '분석' ? 'active' : ''}>시즌분석</button>
                    <button onClick={() => setTab('상세')} className={tab === '상세' ? 'active' : ''}>상세내역</button>
                </div>
            </div>
            {/* 로딩 중이거나 탭에 따라 다른 내용 표시 */}
            {loading ? <Loading /> : tab === '분석' ? (
                <div className="season-analysis">
                    {/* MyTradeAI 컴포넌트에 aiAdvice prop 전달 */}
                    <MyTradeAI aiAdvice={aiAdvice} />
                    <div>
                        {/* 분석 결과 테이블 */}
                        <table>
                            <thead>
                            <tr><th style={{ width: '40%' }}>항목</th><th>분석 결과</th></tr>
                            </thead>
                            <tbody>
                            <tr>
                                <td>최다 투자 종목</td>
                                <td>{analysis.topCoin}</td>
                            </tr>
                            <tr>
                                <td>최고 수익 종목</td>
                                <td>{analysis.bestProfitCoin}</td>
                            </tr>
                            <tr>
                                <td>최저 수익 종목</td>
                                <td>{analysis.worstProfitCoin}</td>
                            </tr>
                            <tr>
                                <td>총 거래 횟수</td>
                                <td>{analysis.totalTrades.toLocaleString()}</td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            ) : (
                <div className="season-details">
                    <h2>시즌별 투자내역</h2>
                    {/* 시즌별 투자내역 요약 테이블 */}
                    <table>
                        <thead>
                        <tr>
                            <th>종목</th><th>최초매수일</th><th>총매수금액</th><th>총매도금액</th>
                            <th>평균매수가</th><th>평균매도가</th><th>수익률</th><th>수익금</th>
                        </tr>
                        </thead>
                        <tbody>
                        {summary.map((item, i) => (
                            <tr key={i}>
                                <td>{item.koreanName} ({item.coin})</td>
                                <td>{item.buyDate.toLocaleString()}</td>
                                <td>{item.buyAmount.toLocaleString()}</td>
                                <td>{item.sellAmount.toLocaleString()}</td>
                                <td>{item.avgBuy.toLocaleString()}</td>
                                <td>{item.avgSell.toLocaleString()}</td>
                                <td>{item.returnRate}</td>
                                <td>{item.profit.toLocaleString()}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>

                    <h2 style={{ marginTop: '2rem' }}>상세 거래내역</h2>
                    {/* 상세 거래내역 테이블 */}
                    <table>
                        <thead>
                        <tr>
                            <th>거래일자</th><th>종목</th><th>종류</th><th>수량</th><th>단가</th><th>금액</th>
                        </tr>
                        </thead>
                        <tbody>
                        {details.map((d, i) => (
                            <tr key={i}>
                                <td>{d.date || '-'}</td>
                                <td>{d.koreanName || '-'}</td>
                                <td>{d.type || '-'}</td>
                                <td>{Number(d.qty?.split(" ")[0] || 0).toLocaleString()}</td>
                                <td>{Number(d.price).toLocaleString()}</td>
                                <td>{Number(d.total).toLocaleString()}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
