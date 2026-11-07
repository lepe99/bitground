import api from './axiosConfig.js';

// group: 목록 탭 (ALL·TRADE·COMMUNITY·SYSTEM)
export const fetchNotifications = async ({page, size, group = 'ALL'}) =>
    (await api.get('/notifications', {params: {page, size, group}})).data;

export const fetchUnreadNotificationCount = async () => (await api.get('/notifications/count')).data;

// 모두 읽음 처리
export const markAllNotificationsRead = () => api.patch('/notifications');

// 고른 알림만 읽음 (누른 알림, 묶여 보이는 알림이면 함께)
export const markNotificationsRead = (ids) => api.patch('/notifications/read', {ids});
