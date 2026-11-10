import {useEffect, useState} from 'react';
import {formatAmount, formatCompactKRW, formatPrice} from '../../utils/format.js';

// 화면에 유지하는 최근 체결 건수 (새 체결이 계속 와도 목록이 끝없이 커지지 않도록)
const MAX_ROWS = 30;
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 30000;

// 백엔드 WebSocket 주소. 운영은 프론트(CloudFront)와 API 서버의 도메인이 달라 VITE_API_URL에서 만들고,
// 개발은 지정이 없으면 Vite 개발 서버 프록시(/ws)를 거친다
const TRADE_WS_URL = (() => {
    const base = new URL(import.meta.env.VITE_API_URL || window.location.origin, window.location.origin);
    base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
    base.pathname = `${base.pathname.replace(/\/$/, '')}/ws/trade/history`;
    return base.toString();
})();

const timeOf = (iso) => {
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
};

/** Bitground 안의 최근 체결 (우리 서비스 사용자들의 체결) */
export default function RecentTrades({symbol, className = ''}) {
    const [history, setHistory] = useState([]);

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
            ws = new WebSocket(TRADE_WS_URL);

            ws.onopen = () => {
                attempts = 0;
                ws.send(JSON.stringify({action: 'subscribe', symbol}));
            };
            ws.onmessage = (evt) => {
                let msg;
                try {
                    msg = JSON.parse(evt.data);
                } catch {
                    return;
                }
                setHistory((prev) => (msg.type === 'initial' ? msg.data : [msg.data, ...prev]).slice(0, MAX_ROWS));
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
        <section className={`flex min-h-0 flex-col rounded-xl border border-line bg-surface px-4 py-3 ${className}`}>
            <h2 className="pb-2 text-sm font-bold">체결 내역</h2>
            <div className="grid grid-cols-[1fr_1.4fr_1fr_1fr] gap-2 border-b border-line py-1.5 text-xs text-fg-3">
                <span>시간</span>
                <span className="text-right">체결가</span>
                <span className="text-right">수량</span>
                <span className="text-right">금액</span>
            </div>
            {history.length === 0 && <p className="py-8 text-center text-sm text-fg-3">아직 이 종목의 체결이 없어요.</p>}
            <ul className="min-h-0 flex-1 overflow-y-auto">
                {history.map((t, i) => {
                    const color = t.orderType === 'BUY' ? 'text-up' : 'text-down';
                    const total = t.orderType === 'BUY' ? Math.ceil(t.amount * t.tradePrice) : Math.floor(t.amount * t.tradePrice);
                    return (
                        <li key={`${t.createdAt}-${i}`} className="num grid grid-cols-[1fr_1.4fr_1fr_1fr] gap-2 py-1.5 text-xs">
                            <span className="text-fg-3">{t.createdAt ? timeOf(t.createdAt) : '—'}</span>
                            <span className={`text-right font-semibold ${color}`}>{formatPrice(t.tradePrice)}</span>
                            <span className={`text-right ${color}`}>{formatAmount(t.amount)}</span>
                            <span className="text-right text-fg-2">{formatCompactKRW(total)}</span>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}
