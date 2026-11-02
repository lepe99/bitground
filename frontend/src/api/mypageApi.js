import api from './axiosConfig.js';

export const fetchMyTier = async (seasonId) => (await api.get('/mypage/tier', {params: {seasonId}})).data;

// 종료된 시즌의 AI 투자 조언 (없으면 서버가 생성)
export const fetchAiAdvice = async (seasonId) => (await api.get('/mypage/ai-advice', {params: {seasonId}})).data;

// 코인별 거래 요약
export const fetchTradeSummary = async (seasonId) =>
    (await api.get('/users/trade-summary', {params: {seasonId}})).data;

// 거래 상세 내역 (실패하면 빈 목록)
export const fetchTradeDetails = async (seasonId) => {
    try {
        return (await api.get('/users/trade-details', {params: {seasonId}})).data;
    } catch (err) {
        console.error('상세 거래 내역 불러오기 실패:', err);
        return [];
    }
};
