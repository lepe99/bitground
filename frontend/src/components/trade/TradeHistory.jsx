import React, {useEffect, useRef, useState} from 'react';
import api from "../../api/axiosConfig.js";
import "../../styles/trade/TradeHistory.css"

export default function TradeHistory({symbol}) {
    const [history, setHistory] = useState([]);
    const wsRef = useRef(null);
    const prevSymbolRef = useRef(null);

    // 1) 초기 REST 호출
    useEffect(() => {
        if (!symbol) return;
        api.get('/trade/history', {params: {symbol}})
            .then(res => setHistory(res.data.slice(0, 30)))
            .catch(console.error);
    }, [symbol]);

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

    // 2) WebSocket 연결 (마운트 시 단 한 번만)
    useEffect(() => {
        const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
        const wsUrl = `${proto}://${window.location.host}/ws/trade/history`;
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
            if (symbol) {
                ws.send(JSON.stringify({action: 'subscribe', symbol}));
                prevSymbolRef.current = symbol;
            }
        };
        ws.onmessage = evt => {
            let msg;
            try {
                msg = JSON.parse(evt.data);
            } catch {
                return;
            }
            // 초기 메시지(type:"initial")는 data 배열, 업데이트(type:"update")는 data 객체
            const payload = msg.type === 'initial'
                ? msg.data
                : [msg.data];
            setHistory(prev => {
                // "initial"일 땐 전체 덮어쓰기, "update"일 땐 새 항목만 앞에 추가
                const next = msg.type === 'initial'
                    ? payload
                    : [payload[0], ...prev];
                return next.slice(0, 30); // 최신 30개만 유지
            });
        };
        ws.onerror = console.error;

        return () => ws.close();
    }, []); // 빈 deps

    // 3) symbol 변경 시 subscribe/unsubscribe
    useEffect(() => {
        const ws = wsRef.current;
        if (!ws || ws.readyState !== WebSocket.OPEN) return;

        // 전에 구독하던 건 해제
        if (prevSymbolRef.current && prevSymbolRef.current !== symbol) {
            ws.send(JSON.stringify({
                action: 'unsubscribe',
                symbol: prevSymbolRef.current
            }));
        }
        // 새 심볼 구독
        if (symbol && prevSymbolRef.current !== symbol) {
            ws.send(JSON.stringify({action: 'subscribe', symbol}));
            setHistory([]); // 화면 전환 시 초기화
            prevSymbolRef.current = symbol;
        }
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
