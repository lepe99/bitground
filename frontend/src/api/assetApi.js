import api from './axiosConfig.js';

// 보유 자산 전체 + 현금 + 주문 가능 금액
export const fetchOwnedAssets = async () => (await api.get('/assets/owned')).data;

// 매도 가능 수량 기준 보유 자산 + 주문 가능 금액
export const fetchAvailableAssets = async () => (await api.get('/assets')).data;

// 관심 코인
export const fetchFavorites = async () => (await api.get('/favorites')).data;

export const addFavorite = (symbol) => api.post('/favorites', null, {params: {symbol}});

export const removeFavorite = (symbol) => api.delete(`/favorites/${symbol}`);
