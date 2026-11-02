import api from './axiosConfig.js';

export const fetchCurrentRanking = async () => (await api.get('/rank/current')).data;

export const fetchSeasonRanking = async (seasonId) => (await api.get(`/rank/${seasonId}`)).data;

export const fetchUserRankDetail = async (userId) => (await api.get(`/rank/detail/${userId}`)).data;

// 메인 화면 상위 5명 (로그인 없이 조회)
export const fetchPublicRanking = async () => (await api.get('/public/ranking')).data;
