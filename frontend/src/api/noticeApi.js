import api from './axiosConfig.js';

export const fetchNotices = async ({page, size, keyword = ''}) =>
    (await api.get('/notices', {params: {page, size, keyword}})).data;

// notify: 등록할 때 전체 사용자에게 알림을 보낼지 (관리자)
export const createNotice = async ({title, content, notify = true}) => (await api.post('/notices', {title, content, notify})).data;

export const updateNotice = (noticeId, {title, content}) => api.put(`/notices/${noticeId}`, {title, content});

export const deleteNotice = (noticeId) => api.delete(`/notices/${noticeId}`);
