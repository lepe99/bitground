
import api from "./axiosConfig.js"; // axiosConfig.js에서 설정된 'api' 인스턴스를 임포트

// 중요: 불필요한 apiClient 생성 제거!
// 이제 모든 요청은 axiosConfig.js에 정의된 'api' 인스턴스를 직접 사용합니다.
// axiosConfig.js의 baseURL이 최종 엔드포인트를 결정합니다.

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

// 원화 마켓 전체 [{symbol, koreanName, tradePrice24h, changeRate, warning, caution}] (거래대금 순, 매시 갱신, 서버 캐시)
// 업비트 /market/all·/ticker/all을 브라우저가 직접 부르지 않는다 (Origin 헤더 요청은 약 10초에 1회 제한)
let marketsRequest = null;
export const fetchCoinMarkets = () => {
    // 한 화면에서 여러 컴포넌트가 동시에 불러도 요청은 한 번 (실패하면 다음에 다시 시도)
    marketsRequest ??= api.get('/coins/markets')
        .then((response) => response.data)
        .catch((error) => {
            console.error('Error fetching coin markets:', error);
            marketsRequest = null;
            return [];
        });
    return marketsRequest;
};

// 가격이 원화에 묶여 거래대금만 큰 스테이블코인 (거래대금 순위에서 뺀다. 스케줄러 stableCoins와 같은 목록)
const STABLE_COINS = new Set(['KRW-USDT', 'KRW-USDC', 'KRW-USDS', 'KRW-USDE', 'KRW-USD1', 'KRW-DAI', 'KRW-PYUSD']);

/** 원화 마켓 24시간 거래대금 상위 count개: [{market, name}] (스테이블코인과 exclude는 뺀다) */
export const fetchTopVolumeMarkets = async (count, exclude = []) => {
    const skip = new Set(exclude);
    return (await fetchCoinMarkets())
        .filter((c) => !STABLE_COINS.has(c.symbol) && !skip.has(c.symbol))
        .slice(0, count) // 서버가 거래대금 순으로 준다
        .map((c) => ({market: c.symbol, name: c.koreanName}));
};

/** 최근 24시간 1시간봉 종가 (오래된 것부터). 추이 그래프용. 서버가 업비트를 대신 불러 캐시한다 */
export const fetchHourlyCloses = async (symbol) => (await api.get(`/coins/${symbol}/hourly-closes`)).data;

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
