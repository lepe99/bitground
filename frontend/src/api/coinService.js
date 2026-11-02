// src/components/api/coinService.js

import api from "./axiosConfig.js"; // axiosConfig.js에서 설정된 'api' 인스턴스를 임포트

// 중요: 불필요한 apiClient 생성 제거!
// 이제 모든 요청은 axiosConfig.js에 정의된 'api' 인스턴스를 직접 사용합니다.
// axiosConfig.js의 baseURL이 최종 엔드포인트를 결정합니다.

// 1. 모든 코인 정보 조회 (GET /coins)
export const fetchAllCoins = async () => {
    try {
        const response = await api.get('/coins'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching all coins:', error);
        return [];
    }
};

// 2. 거래대금 상위 5개 코인 조회 (GET /coins/high-trade-price)
export const fetchTop5HighTradePriceCoins = async () => {
    try {
        const response = await api.get('/coins/high-trade-price'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching top 5 high trade price coins:', error);
        return [];
    }
};

// 3. 상승폭 큰 종목 상위 5개 코인 조회 (GET /coins/price-increase)
export const fetchTop5PriceIncreaseCoins = async () => {
    try {
        const response = await api.get('/coins/price-increase'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching top 5 price increase coins:', error);
        return [];
    }
};

// 4. 하락폭 큰 종목 상위 5개 코인 조회 (GET /coins/price-decrease)
export const fetchTop5PriceDecreaseCoins = async () => {
    try {
        const response = await api.get('/coins/price-decrease'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching top 5 price decrease coins:', error);
        return [];
    }
};

// 5. 거래유의 종목 조회 (GET /coins/caution)
export const fetchWarningCoins = async () => {
    try {
        const response = await api.get('/coins/caution'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching warning coins:', error);
        return [];
    }
};

// 6. 투자주의 종목 조회 (GET /coins/alert)
export const fetchAlertCoins = async () => {
    try {
        const response = await api.get('/coins/alert'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching alert coins:', error);
        return [];
    }
};

// 7. 모든 코인 심볼 조회 (GET /coins/symbols)
export const fetchCoinSymbols = async () => {
    try {
        const response = await api.get('/coins/symbols'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching coin symbols:', error);
        return [];
    }
};

// 8. 특정 코인에 대한 AI 분석 결과 조회 (GET /coins/{symbol}/insight)
export const fetchCoinInsight = async (symbol) => {
    try {
        const response = await api.get(`/coins/${symbol}/insight`); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error(`Error fetching AI insight for ${symbol}:`, error);
        return null;
    }
};

// 9. 전체 시장에 대한 AI 분석 결과 조회 (GET /ai-insights/overall-market)
export const fetchOverallMarketInsight = async () => {
    try {
        const response = await api.get('/ai-insights/overall-market'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching overall market AI insight:', error);
        return null;
    }
};

// 10. 오늘자 AI 분석 결과가 존재하는 모든 코인 심볼 목록 조회 (GET /ai-insights/today-symbols)
export const fetchTodayInsightSymbols = async () => {
    try {
        const response = await api.get('/ai-insights/today-symbols'); // api 인스턴스 직접 사용
        return response.data;
    } catch (error) {
        console.error('Error fetching today insight symbols:', error);
        return [];
    }
};
