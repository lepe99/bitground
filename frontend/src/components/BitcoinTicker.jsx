import React, { useEffect, useState } from 'react';

const BitcoinTicker = () => {
    const [price, setPrice] = useState(null); // ✅ TS 타입 제거

    useEffect(() => {
        const socket = new WebSocket('wss://api.upbit.com/websocket/v1');

        socket.onopen = () => {
            const msg = [
                { ticket: 'test' },
                { type: 'ticker', codes: ['KRW-BTC'] }
            ];
            socket.send(JSON.stringify(msg));
        };

        socket.onmessage = (event) => {
            const reader = new FileReader();
            reader.onload = () => {
                const text = reader.result;
                try {
                    const json = JSON.parse(text);
                    setPrice(json.trade_price);
                } catch (e) {
                    console.error("JSON 파싱 오류:", e);
                }
            };
            reader.readAsText(event.data);
        };

        return () => {
            socket.close();
        };
    }, []);

    return (
        <div>
            <h2>비트코인 현재가</h2>
            <p>{price !== null ? `${Number(price).toLocaleString()} 원` : '로딩 중...'}</p>
        </div>
    );
};

export default BitcoinTicker;
