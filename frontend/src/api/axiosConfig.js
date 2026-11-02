import axios from 'axios';

// 토큰 갱신 중인지 추적하는 변수 추가
let isRefreshing = false;
// 토큰 갱신 대기 중인 요청들을 저장할 배열
let refreshSubscribers = [];

// 토큰 갱신 후 대기 중인 요청들을 처리하는 함수
const onRefreshed = () => {
    refreshSubscribers.forEach(callback => callback());
    refreshSubscribers = [];
};

// 토큰 갱신을 기다리는 요청을 추가하는 함수
const addRefreshSubscriber = (callback) => {
    refreshSubscribers.push(callback);
};

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
    withCredentials: true,
});

api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        // 리프레시 토큰 엔드포인트 요청은 재시도하지 않음
        if (originalRequest.url === '/auth/refresh') {
            isRefreshing = false;
            return Promise.reject(error);
        }

        // 401 에러이고, 재시도한 요청이 아닐 경우
        if (error.response?.status === 401 && !originalRequest._retry) {

            originalRequest._retry = true;

            // 이미 토큰 갱신 중이라면, 대기열에 추가
            if (isRefreshing) {
                return new Promise((resolve) => {
                    addRefreshSubscriber(() => {
                        resolve(api(originalRequest));
                    });
                });
            }

            isRefreshing = true;

            try {
                // 백엔드의 토큰 재발급 API 호출
                await api.post('/auth/refresh');

                // 토큰 갱신 성공 처리
                isRefreshing = false;
                onRefreshed();

                // 원래 요청 재시도
                return api(originalRequest);
            } catch (refreshError) {
                isRefreshing = false;
                window.dispatchEvent(new Event('forceLogout'));
                return Promise.reject(refreshError);
            }
        }
        return Promise.reject(error);
    }
);

export default api;