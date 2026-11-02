import api from './axiosConfig.js';

export const fetchNotices = async ({page, size, keyword = ''}) =>
    (await api.get('/notices', {params: {page, size, keyword}})).data;

export const createNotice = async ({title, content}) => (await api.post('/notices', {title, content})).data;

export const deleteNotice = (noticeId) => api.delete(`/notices/${noticeId}`);
