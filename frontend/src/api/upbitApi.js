import axios from 'axios';

// 업비트 공개 시세 REST API. 인증이 필요 없고 CORS를 허용해 브라우저가 직접 부른다 (우리 서버를 거치지 않음)
const upbit = axios.create({baseURL: 'https://api.upbit.com/v1', timeout: 5000});

/** 현재가: markets = ['KRW-BTC', ...] */
export const fetchTickers = async (markets) =>
    (await upbit.get('/ticker', {params: {markets: markets.join(',')}})).data;

/** 1시간봉 count개 (최신이 앞). 스파크라인용 */
export const fetchHourlyCandles = async (market, count = 24) =>
    (await upbit.get('/candles/minutes/60', {params: {market, count}})).data;
