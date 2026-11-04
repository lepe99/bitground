import api from './axiosConfig.js';

// 시즌 목록 (최신순)
export const fetchSeasons = async () => (await api.get('/seasons')).data;

// 진행 중인 시즌 id
export const fetchCurrentSeasonId = async () => (await api.get('/seasons/current-id')).data;

// 진행 중인 시즌 일정: 스플릿 2 시작·시즌 종료 시각 (로그인 없이 볼 수 있음)
export const fetchCurrentSeason = async () => (await api.get('/seasons/current')).data;
