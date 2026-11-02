import api from './axiosConfig.js';

// 시즌 목록 (최신순)
export const fetchSeasons = async () => (await api.get('/seasons')).data;
