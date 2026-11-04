import api from './axiosConfig.js';

// 시즌 목록 (최신순)
export const fetchSeasons = async () => (await api.get('/seasons')).data;

// 진행 중인 시즌 id
export const fetchCurrentSeasonId = async () => (await api.get('/seasons/current-id')).data;
