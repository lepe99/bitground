import api from './axiosConfig.js';

// 로그인한 사용자 정보 (쿠키의 액세스 토큰 기준, 로그인 안 했으면 401)
export const fetchMe = async () => (await api.get('/users/me')).data;

export const logout = () => api.post('/auth/logout');
