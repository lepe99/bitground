import api from './axiosConfig.js';

export const fetchNotifications = async ({page, size, sort = 'createdAt,desc'}) =>
    (await api.get('/notifications', {params: {page, size, sort}})).data;

export const fetchUnreadNotificationCount = async () => (await api.get('/notifications/count')).data;

// 모두 읽음 처리
export const markAllNotificationsRead = () => api.patch('/notifications');
