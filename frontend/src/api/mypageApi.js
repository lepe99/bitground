import api from './axiosConfig.js';

export const fetchMyTier = async (seasonId) => (await api.get('/mypage/tier', {params: {seasonId}})).data;

// 종료된 시즌의 AI 리뷰 조회만 (아직 만들지 않았으면 null)
export const fetchAiAdvice = async (seasonId) => {
    const response = await api.get('/mypage/ai-advice', {params: {seasonId}});
    return response.status === 204 ? null : response.data;
};

// AI 리뷰 생성: 사용자가 버튼을 눌렀을 때만 부른다 (이미 있으면 그대로 돌려줌)
export const generateAiAdvice = async (seasonId) =>
    (await api.post('/mypage/ai-advice', null, {params: {seasonId}, timeout: 120_000})).data;

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
