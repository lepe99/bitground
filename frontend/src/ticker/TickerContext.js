import {createContext} from 'react';

// 업비트 실시간 시세 (TickerProvider가 채운다)
export const TickerContext = createContext({
    markets: [],
    selectedMarket: null,
    tickerMap: {},
    setSelectedMarket: () => {}
});
