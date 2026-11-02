import api from "./axiosConfig";

export async function fetchTradeDetails(seasonId) {
    try {
        const res = await api.get(`/users/trade-details?seasonId=${seasonId}`);
        return res.data;
    } catch (err) {
        console.error("상세 거래 내역 불러오기 실패:", err);
        return [];
    }
}