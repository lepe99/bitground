import api from './axiosConfig.js';

// 관리자 페이지 (ROLE_ADMIN만, 서버가 권한을 확인한다)
export const fetchAdminOverview = async () => (await api.get('/admin/overview')).data;

export const fetchAdminUsers = async ({q = '', page = 0, size = 20}) =>
    (await api.get('/admin/users', {params: {q, page, size}})).data;

export const fetchAdminUser = async (id) => (await api.get(`/admin/users/${id}`)).data;

export const suspendUser = (id, reason) => api.post(`/admin/users/${id}/suspend`, {reason});

export const unsuspendUser = (id) => api.post(`/admin/users/${id}/unsuspend`);

export const fetchAdminPosts = async ({page = 0, size = 20}) => (await api.get('/admin/posts', {params: {page, size}})).data;

export const fetchAdminComments = async ({page = 0, size = 20}) => (await api.get('/admin/comments', {params: {page, size}})).data;

export const deleteAdminPost = (id) => api.delete(`/admin/posts/${id}`);

export const deleteAdminComment = (id) => api.delete(`/admin/comments/${id}`);

export const fetchAuditLogs = async ({page = 0, size = 20}) => (await api.get('/admin/audit-logs', {params: {page, size}})).data;
