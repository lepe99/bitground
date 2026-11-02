import React, {createContext, useState, useEffect, useRef} from 'react';
import api from "../api/axiosConfig.js";

export const TickerContext = createContext({
    markets: [],
    selectedMarket: null,
    tickerMap: {},
    setSelectedMarket: () => {}
});

export function TickerProvider({ children }) {
    const [markets, setMarkets] = useState([]);
    const [selectedMarket, setSelectedMarket] = useState('KRW-BTC');
    const [tickerMap, setTickerMap] = useState({});
    const wsRef = useRef(null);
    const [isWsConnected, setIsWsConnected] = useState(false);

    // // (1) markets 불러오기
    useEffect(() => {
        api.get('/coins/symbols')
            .then(res => {
                const krw = res.data.map(c => ({
                    market: c.symbol,
                    name: c.koreanName
                }));
                setMarkets(krw);
                // setSelectedMarket('KRW-BTC');
            })
            .catch(err => {
                console.error('코인 목록 로드 실패:', err);
            });
    }, []);

    // (2) WebSocket 구독
    useEffect(() => {
        if (!markets.length) return;
        let ws, reconnectTimer;

        const connect = () => {
            const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
            const host     = window.location.host;
            const wsUrl    = `${protocol}://${host}/upbit-ws`;

            ws = new WebSocket(wsUrl);
            ws.binaryType = 'blob';
            wsRef.current = ws;

            ws.onopen = () => {
                setIsWsConnected(true);
                ws.send(JSON.stringify([
                    { ticket: 'bitground' },
                    { type: 'ticker', codes: markets.map(m => m.market) }
                ]));
            };
            ws.onmessage = async e => {
                const text = typeof e.data === 'string' ? e.data : await e.data.text();
                const msg  = JSON.parse(text);
                const tick = Array.isArray(msg) ? msg[0] : msg;
                setTickerMap(prev => ({
                    ...prev,
                    [tick.code]: {
                        price:      tick.trade_price,
                        changeAmt:  tick.signed_change_price,
                        changeRate: tick.signed_change_rate,
                        volume:     tick.acc_trade_price_24h,
                        high:       tick.high_price,
                        low:        tick.low_price
                    }
                }));
            };
            ws.onerror = e => {
                // 에러가 나면 강제 종료 → onclose에서 재연결
                ws.close();
            };
            ws.onclose = e => {
                setIsWsConnected(false);
                reconnectTimer = setTimeout(connect, 3000);
            };
        };
        connect();

        return () => {
            clearTimeout(reconnectTimer);
            if (ws) ws.close();
        };
    }, [markets]);

    return (
        <TickerContext.Provider
            value={{ markets, selectedMarket, tickerMap, setSelectedMarket, isWsConnected }}
        >
            {children}
        </TickerContext.Provider>
    );
}
