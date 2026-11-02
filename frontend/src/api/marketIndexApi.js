import api from './axiosConfig';

export const fetchTodayMarketIndex = async () => {
    const response = await api.get('/market-index/today');
    return response.data;
};

export const fetchYesterdayMarketIndex = async () => {
    const response = await api.get('/market-index/yesterday');
    return response.data;
};