import {useEffect, useState} from 'react';
import {fetchTickers} from '../api/upbitApi.js';

const POLL_MS = 5000;

/**
 * 몇 개 종목의 현재가를 주기적으로 불러온다 (메인 화면처럼 종목이 적을 때용. 거래소는 TickerProvider의 WebSocket을 쓴다)
 * @returns {{[market]: {price, changeRate, changePrice, volume}}}
 */
export function useUpbitTickers(markets) {
    const [tickers, setTickers] = useState({});
    const key = markets.join(',');

    useEffect(() => {
        if (!key) return;
        let alive = true;
        const load = () =>
            fetchTickers(key.split(','))
                .then((list) => {
                    if (!alive) return;
                    const next = {};
                    for (const t of list) {
                        next[t.market] = {
                            price: t.trade_price,
                            changeRate: t.signed_change_rate * 100,
                            changePrice: t.signed_change_price,
                            volume: t.acc_trade_price_24h,
                        };
                    }
                    setTickers(next);
                })
                .catch((error) => console.warn('업비트 시세 조회 실패:', error.message));

        void load();
        const timer = setInterval(load, POLL_MS);
        return () => {
            alive = false;
            clearInterval(timer);
        };
    }, [key]);

    return tickers;
}
