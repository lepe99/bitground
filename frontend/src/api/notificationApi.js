import api from './axiosConfig.js';

// group: 목록 탭 (ALL·TRADE·COMMUNITY·SYSTEM)
export const fetchNotifications = async ({page, size, group = 'ALL'}) =>
    (await api.get('/notifications', {params: {page, size, group}})).data;

export const fetchUnreadNotificationCount = async () => (await api.get('/notifications/count')).data;

// 모두 읽음 처리
export const markAllNotificationsRead = () => api.patch('/notifications');

// 알림 하나 읽음 (누른 알림만)
export const markNotificationRead = (id) => api.patch(`/notifications/${id}/read`);
