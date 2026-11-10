import {useEffect, useState} from 'react';
import {subscribeTickers} from '../ticker/upbitSocket.js';

// 시세 메시지는 초당 여러 건 오므로 모아 두었다가 이 간격으로 한 번에 반영한다
const FLUSH_MS = 300;

const toTicker = (t) => ({
    price: t.trade_price,
    changeRate: t.signed_change_rate * 100,
    changePrice: t.signed_change_price,
    volume: t.acc_trade_price_24h,
});

/**
 * 몇 개 종목의 실시간 현재가 (메인 화면처럼 종목이 적을 때용. 거래소·투자내역은 TickerProvider를 쓴다)
 * 탭 공용 WebSocket으로 받는다. 업비트가 구독 직후 현재 시세를 먼저 보내므로 따로 REST로 채우지 않는다
 * (브라우저의 업비트 REST 호출은 약 10초에 1회로 제한된다)
 * @returns {{[market]: {price, changeRate(%), changePrice, volume}}}
 */
export function useUpbitTickers(markets) {
    const [tickers, setTickers] = useState({});
    const key = markets.join(',');

    useEffect(() => {
        if (!key) return;
        let pending = {};

        const unsubscribe = subscribeTickers(key.split(','), (tick) => {
            pending[tick.code] = toTicker(tick);
        });
        const flush = () => {
            if (!Object.keys(pending).length) return;
            const batch = pending;
            pending = {};
            setTickers((prev) => ({...prev, ...batch}));
        };
        flush(); // 전에 받아 둔 시세는 바로 반영
        const flushTimer = setInterval(flush, FLUSH_MS);

        return () => {
            unsubscribe();
            clearInterval(flushTimer);
        };
    }, [key]);

    return tickers;
}
