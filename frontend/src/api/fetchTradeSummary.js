import api from './axiosConfig.js';

/**
 * 특정 시즌의 거래 요약 데이터를 가져옴
 * @param {number} seasonId - 조회할 시즌의 ID
 * @returns {Promise<Array>} - TradeSummaryDto 배열
 */
export async function fetchTradeSummary(seasonId) {
    const response = await api.get(`/users/trade-summary`, {
        params: { seasonId },
    });
    return response.data;
}