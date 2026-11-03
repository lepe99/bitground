import api from './axiosConfig.js';

// 시장가·지정가 즉시 주문
export const placeOrder = async (payload) => (await api.post('/trade', payload)).data;

// 예약 주문: 접수·조회·취소 모두 /orders/reserve
export const placeReserveOrder = async (payload) => (await api.post('/orders/reserve', payload)).data;

// 현재 시즌 미체결 예약 주문
export const fetchPendingOrders = async () => (await api.get('/orders/reserve')).data;

export const cancelReserveOrder = (orderId) => api.delete(`/orders/reserve/${orderId}`);

// 시즌별 내 주문 내역
export const fetchSeasonOrders = async (seasonId) => (await api.get('/orders', {params: {seasonId}})).data;
