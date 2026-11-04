import {useEffect, useState} from 'react';
import {fetchTickers} from '../api/upbitApi.js';
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
 * 처음 값은 REST로 바로 채우고, 이후로는 공용 WebSocket으로 갱신한다
 * @returns {{[market]: {price, changeRate(%), changePrice, volume}}}
 */
export function useUpbitTickers(markets) {
    const [tickers, setTickers] = useState({});
    const key = markets.join(',');

    useEffect(() => {
        if (!key) return;
        const codes = key.split(',');
        let alive = true;
        let pending = {};

        fetchTickers(codes)
            .then((list) => {
                if (!alive) return;
                // WebSocket 값이 먼저 와 있으면 그쪽이 더 최신이다
                setTickers((prev) => ({...Object.fromEntries(list.map((t) => [t.market, toTicker(t)])), ...prev}));
            })
            .catch((error) => console.warn('업비트 시세 조회 실패:', error.message));

        const unsubscribe = subscribeTickers(codes, (tick) => {
            pending[tick.code] = toTicker(tick);
        });
        const flushTimer = setInterval(() => {
            if (!Object.keys(pending).length) return;
            const batch = pending;
            pending = {};
            setTickers((prev) => ({...prev, ...batch}));
        }, FLUSH_MS);

        return () => {
            alive = false;
            unsubscribe();
            clearInterval(flushTimer);
        };
    }, [key]);

    return tickers;
}
