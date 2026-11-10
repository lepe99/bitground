import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
    withCredentials: true,
});

// 액세스 토큰(30분)이 만료되면 401이 오고, 리프레시 토큰으로 한 번 재발급받아 원래 요청을 다시 보낸다.
// 동시에 여러 요청이 401을 받아도 재발급은 한 번만 하고 나머지는 그 결과를 기다린다.
let refreshing = null; // 진행 중인 재발급 Promise

// 재발급·로그아웃 요청 자체는 다시 재발급을 시도하지 않는다
const NO_RETRY = new Set(['/auth/refresh', '/auth/logout']);

// 서버가 리프레시 토큰을 거절한 경우만 세션이 끝난 것으로 본다.
// (절전에서 깨어난 직후의 네트워크 오류나 서버 재시작 중 5xx·429는 세션이 살아 있으므로 로그아웃시키지 않는다)
const isSessionExpired = (error) => [401, 403].includes(error.response?.status);

api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;
        if (error.response?.status !== 401 || !originalRequest || originalRequest._retry || NO_RETRY.has(originalRequest.url)) {
            return Promise.reject(error);
        }
        originalRequest._retry = true;

        refreshing ??= api.post('/auth/refresh').finally(() => {
            refreshing = null;
        });
        try {
            await refreshing;
        } catch (refreshError) {
            if (isSessionExpired(refreshError)) {
                window.dispatchEvent(new Event('forceLogout'));
            }
            return Promise.reject(refreshError);
        }
        return api(originalRequest);
    }
);

export default api;
