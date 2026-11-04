import {useState, useEffect, useMemo} from 'react';
import {TickerContext} from './TickerContext.js';
import {fetchCoinSymbols} from "../api/coinApi.js";
import {fetchMarketEvents} from "../api/upbitApi.js";

// Upbit 공개 시세 WebSocket. 인증이 필요 없어 브라우저가 직접 연결한다 (우리 서버를 거쳐 중계하지 않음)
const UPBIT_WS_URL = 'wss://api.upbit.com/websocket/v1';
// 시세 메시지는 초당 수십 건이 오므로 모아 두었다가 이 간격으로 한 번에 화면에 반영한다
const FLUSH_INTERVAL_MS = 200;
const RECONNECT_DELAY_MS = 3000;


const toTicker = (tick) => ({
    price:      tick.trade_price,
    changeAmt:  tick.signed_change_price,
    changeRate: tick.signed_change_rate,
    volume:     tick.acc_trade_price_24h,
    high:       tick.high_price,
    low:        tick.low_price
});

export function TickerProvider({ children }) {
    const [markets, setMarkets] = useState([]);
    const [selectedMarket, setSelectedMarket] = useState('KRW-BTC');
    const [tickerMap, setTickerMap] = useState({});
    const [isWsConnected, setIsWsConnected] = useState(false);

    // (1) markets 불러오기
    useEffect(() => {
        // 유의·주의 지정은 업비트에서 바로 받는다. 실패해도 목록은 보여준다
        Promise.all([fetchCoinSymbols(), fetchMarketEvents().catch(() => ({}))])
            .then(([symbols, events]) => {
                setMarkets(symbols.map(c => ({
                    market: c.symbol,
                    name: c.koreanName,
                    warning: events[c.symbol]?.warning ?? false,
                    caution: events[c.symbol]?.caution ?? false,
                })));
            })
            .catch(err => {
                console.error('코인 목록 로드 실패:', err);
            });
    }, []);

    // (2) WebSocket 구독
    useEffect(() => {
        if (!markets.length) return;

        let ws;
        let reconnectTimer;
        let disposed = false; // 언마운트 후에는 onclose가 다시 연결하지 않도록
        let pending = {};     // 아직 화면에 반영하지 않은 종목별 최신 시세
        const decoder = new TextDecoder();

        const flushTimer = setInterval(() => {
            if (Object.keys(pending).length === 0) return;
            const batch = pending;
            pending = {};
            setTickerMap(prev => ({...prev, ...batch}));
        }, FLUSH_INTERVAL_MS);

        const connect = () => {
            ws = new WebSocket(UPBIT_WS_URL);
            ws.binaryType = 'arraybuffer';

            ws.onopen = () => {
                setIsWsConnected(true);
                ws.send(JSON.stringify([
                    { ticket: 'bitground' },
                    { type: 'ticker', codes: markets.map(m => m.market) }
                ]));
            };
            ws.onmessage = e => {
                const text = typeof e.data === 'string' ? e.data : decoder.decode(e.data);
                const msg = JSON.parse(text);
                const tick = Array.isArray(msg) ? msg[0] : msg;
                pending[tick.code] = toTicker(tick);
            };
            ws.onerror = () => {
                // 에러가 나면 강제 종료 → onclose에서 재연결
                ws.close();
            };
            ws.onclose = () => {
                setIsWsConnected(false);
                if (!disposed) {
                    reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS);
                }
            };
        };
        connect();

        return () => {
            disposed = true;
            clearTimeout(reconnectTimer);
            clearInterval(flushTimer);
            ws?.close();
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
