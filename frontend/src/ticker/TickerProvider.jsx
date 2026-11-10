import {useState, useEffect, useMemo} from 'react';
import {TickerContext} from './TickerContext.js';
import {fetchCoinMarkets} from "../api/coinApi.js";
import {subscribeConnection, subscribeTickers} from "./upbitSocket.js";

// 시세 메시지는 초당 수십 건이 오므로 모아 두었다가 이 간격으로 한 번에 화면에 반영한다
const FLUSH_INTERVAL_MS = 200;

const toTicker = (tick) => ({
    price:      tick.trade_price,
    changeAmt:  tick.signed_change_price,
    changeRate: tick.signed_change_rate,
    volume:     tick.acc_trade_price_24h,
    high:       tick.high_price,
    low:        tick.low_price
});

/** 거래소·투자내역의 원화 마켓 전체 실시간 시세. 종목 목록은 우리 서버(매시 갱신), 시세는 탭 공용 업비트 WebSocket */
export function TickerProvider({ children }) {
    const [markets, setMarkets] = useState([]);
    const [selectedMarket, setSelectedMarket] = useState('KRW-BTC');
    const [tickerMap, setTickerMap] = useState({});
    const [isWsConnected, setIsWsConnected] = useState(false);

    // (1) 종목 목록 (유의·주의 지정 포함)
    useEffect(() => {
        fetchCoinMarkets().then((list) => {
            setMarkets(list.map((c) => ({
                market: c.symbol,
                name: c.koreanName,
                warning: c.warning,
                caution: c.caution,
            })));
        });
    }, []);

    useEffect(() => subscribeConnection(setIsWsConnected), []);

    // (2) 시세 구독
    useEffect(() => {
        if (!markets.length) return;
        let pending = {}; // 아직 화면에 반영하지 않은 종목별 최신 시세

        const unsubscribe = subscribeTickers(markets.map((m) => m.market), (tick) => {
            pending[tick.code] = toTicker(tick);
        });
        const flushTimer = setInterval(() => {
            if (Object.keys(pending).length === 0) return;
            const batch = pending;
            pending = {};
            setTickerMap(prev => ({...prev, ...batch}));
        }, FLUSH_INTERVAL_MS);

        return () => {
            unsubscribe();
            clearInterval(flushTimer);
        };
    }, [markets]);

    const value = useMemo(
        () => ({ markets, selectedMarket, tickerMap, setSelectedMarket, isWsConnected }),
        [markets, selectedMarket, tickerMap, isWsConnected]
    );

    return (
        <TickerContext.Provider value={value}>
            {children}
        </TickerContext.Provider>
    );
}
