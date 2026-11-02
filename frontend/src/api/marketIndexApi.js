import axiosInstance from './axiosInstance';

export const fetchTodayMarketIndex = async () => {
    const response = await axiosInstance.get('/market-index/today');
    return response.data;
};

export const fetchYesterdayMarketIndex = async () => {
    const response = await axiosInstance.get('/market-index/yesterday');
    return response.data;
};