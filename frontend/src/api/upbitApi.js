import axios from 'axios';

// 업비트 공개 시세 REST API. 인증이 필요 없고 CORS를 허용해 브라우저가 직접 부른다 (우리 서버를 거치지 않음)
const upbit = axios.create({baseURL: 'https://api.upbit.com/v1', timeout: 5000});

/** 현재가: markets = ['KRW-BTC', ...] */
export const fetchTickers = async (markets) =>
    (await upbit.get('/ticker', {params: {markets: markets.join(',')}})).data;

/**
 * 원화 마켓의 유의·주의 지정 여부: {'KRW-BTC': {warning, caution}}
 * caution은 업비트가 세부 사유(가격 급등락, 거래량 급등 등)별로 주므로 하나라도 켜져 있으면 주의로 본다
 */
export const fetchMarketEvents = async () => {
    const list = (await upbit.get('/market/all', {params: {is_details: true}})).data;
    return Object.fromEntries(list
        .filter((m) => m.market.startsWith('KRW-'))
        .map((m) => [m.market, {
            warning: Boolean(m.market_event?.warning),
            caution: Object.values(m.market_event?.caution ?? {}).some(Boolean),
        }]));
};

/** 1시간봉 count개 (최신이 앞). 스파크라인용 */
export const fetchHourlyCandles = async (market, count = 24) =>
    (await upbit.get('/candles/minutes/60', {params: {market, count}})).data;

// 가격이 원화에 묶여 거래대금만 큰 스테이블코인 (거래대금 순위에서 뺀다. 스케줄러 stableCoins와 같은 목록)
const STABLE_COINS = new Set(['KRW-USDT', 'KRW-USDC', 'KRW-USDS', 'KRW-USDE', 'KRW-USD1', 'KRW-DAI', 'KRW-PYUSD']);

/**
 * 원화 마켓 24시간 거래대금 상위 count개: [{market, name}] (스테이블코인과 exclude는 뺀다)
 * 업비트 원화 마켓 전체를 기준으로 한다
 */
export const fetchTopVolumeMarkets = async (count, exclude = []) => {
    const [markets, tickers] = await Promise.all([
        upbit.get('/market/all').then((r) => r.data),
        upbit.get('/ticker/all', {params: {quote_currencies: 'KRW'}}).then((r) => r.data),
    ]);
    const names = Object.fromEntries(markets.map((m) => [m.market, m.korean_name]));
    const skip = new Set(exclude);
    return tickers
        .filter((t) => names[t.market] && !STABLE_COINS.has(t.market) && !skip.has(t.market))
        .sort((a, b) => b.acc_trade_price_24h - a.acc_trade_price_24h)
        .slice(0, count)
        .map((t) => ({market: t.market, name: names[t.market]}));
};
