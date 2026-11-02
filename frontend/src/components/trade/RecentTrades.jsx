import React, {useEffect, useState} from 'react';
import "../../styles/trade/RecentTrades.css"

// 화면에 유지하는 최근 체결 건수 (새 체결이 계속 와도 목록이 끝없이 커지지 않도록)
const MAX_ROWS = 30;
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 30000;

export default function RecentTrades({symbol}) {
    const [history, setHistory] = useState([]);

    const currency = symbol.split("-")[0];
    const displaySymbol = symbol.split("-")[1];
    const formatDate = isoString => {
        const d = new Date(isoString);
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        const hh = String(d.getHours()).padStart(2, '0');
        const mi = String(d.getMinutes()).padStart(2, '0');
        return {
            date: `${mm}.${dd}`,
            time: `${hh}:${mi}`
        };
    };

    // 종목마다 WebSocket 하나: 구독 직후 서버가 최근 체결(initial)을 보내고, 이후 새 체결(update)을 보낸다.
    // 연결이 끊기면 지수 백오프로 다시 연결하고, 종목이 바뀌거나 화면을 떠나면 닫는다.
    useEffect(() => {
        if (!symbol) return;

        let ws;
        let reconnectTimer;
        let attempts = 0;
        let disposed = false;
        setHistory([]);

        const connect = () => {
            const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
            ws = new WebSocket(`${proto}://${window.location.host}/ws/trade/history`);

            ws.onopen = () => {
                attempts = 0;
                ws.send(JSON.stringify({action: 'subscribe', symbol}));
            };
            ws.onmessage = evt => {
                let msg;
                try {
                    msg = JSON.parse(evt.data);
                } catch {
                    return;
                }
                setHistory(prev => (msg.type === 'initial' ? msg.data : [msg.data, ...prev]).slice(0, MAX_ROWS));
            };
            ws.onclose = () => {
                if (disposed) return;
                const delay = Math.min(RECONNECT_BASE_MS * 2 ** attempts, RECONNECT_MAX_MS);
                attempts++;
                reconnectTimer = setTimeout(connect, delay);
            };
        };
        connect();

        return () => {
            disposed = true;
            clearTimeout(reconnectTimer);
            ws?.close();
        };
    }, [symbol]);

    return (
        <div className="trade-history-wrapper">
            <h3>체결 내역 (최근 {history.length}건)</h3>
            <div className={"trade-history-table-container"}>
                <table className="trade-history-table">
                    <thead>
                    <tr>
                        <th>체결시간</th>
                        <th>단가({currency})</th>
                        <th>체결량({displaySymbol})</th>
                        <th>거래대금({currency})</th>
                    </tr>
                    </thead>
                    <tbody>
                    {history.map((t, i) => {
                        const {date, time} = t.createdAt
                            ? formatDate(t.createdAt)
                            : {date: '—', time: '—'};
                        const rawAmt = typeof t.amount === 'number' ? t.amount : null;
                        const price = typeof t.tradePrice === 'number'
                            ? t.tradePrice.toLocaleString(undefined, {
                                minimumFractionDigits: 0,
                                maximumFractionDigits: 10
                            })
                            : '—';
                        const rowClass = t.orderType === 'BUY' ? 'row-buy' : 'row-sell';
                        const tradingValue = t.orderType === 'BUY'
                            ? Math.ceil(rawAmt * t.tradePrice).toLocaleString()
                            : Math.floor(rawAmt * t.tradePrice).toLocaleString();

                        return (
                            <tr key={i} className={rowClass}>
                                <td className="time-cell">
                                    <span className="date-part">{date}</span>&nbsp;
                                    <span className="time-part">{time}</span>
                                </td>
                                <td className={t.orderType === 'BUY' ? "buy-color" : "sell-color"}>{price}</td>
                                <td className={t.orderType === 'BUY' ? "buy-color" : "sell-color"}>
                                    {rawAmt != null
                                        ? rawAmt.toLocaleString(undefined, {
                                            minimumFractionDigits: 0,
                                            maximumFractionDigits: 10
                                        })
                                        : '—'}
                                </td>
                                <td>{tradingValue}</td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
