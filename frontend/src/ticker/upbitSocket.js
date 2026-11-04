// 업비트 시세 WebSocket을 화면 여러 곳이 함께 쓰도록 한 연결로 묶는다.
// (메인처럼 카드마다 시세가 필요할 때 카드 수만큼 연결을 열지 않게. 거래소·투자내역은 TickerProvider가 따로 연결한다)
const UPBIT_WS_URL = 'wss://api.upbit.com/websocket/v1';
const RECONNECT_DELAY_MS = 3000;
// 화면 이동으로 구독이 잠깐 비었다 다시 생기는 경우 연결을 끊지 않고 기다리는 시간
const IDLE_CLOSE_MS = 5000;

const listeners = new Set(); // {codes: string[], onTick}
const decoder = new TextDecoder();
let ws = null;
let sentCodes = '';
let reconnectTimer = null;
let idleTimer = null;

const wantedCodes = () => [...new Set([...listeners].flatMap((l) => l.codes))].sort();

// 구독 종목이 바뀌면 같은 연결에 요청을 다시 보낸다 (업비트는 마지막 요청으로 구독을 바꾼다)
const sendSubscription = () => {
    const codes = wantedCodes();
    const key = codes.join(',');
    if (ws?.readyState !== WebSocket.OPEN || !codes.length || key === sentCodes) return;
    ws.send(JSON.stringify([{ticket: 'bitground-shared'}, {type: 'ticker', codes}]));
    sentCodes = key;
};

const connect = () => {
    clearTimeout(reconnectTimer);
    const socket = new WebSocket(UPBIT_WS_URL);
    ws = socket;
    socket.binaryType = 'arraybuffer';
    sentCodes = '';
    socket.onopen = sendSubscription;
    socket.onmessage = (e) => {
        const tick = JSON.parse(typeof e.data === 'string' ? e.data : decoder.decode(e.data));
        for (const l of listeners) {
            if (l.codes.includes(tick.code)) l.onTick(tick);
        }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
        // 이미 닫고 새로 연결한 뒤에 늦게 온 close라면 새 연결을 건드리지 않는다
        if (ws !== socket) return;
        ws = null;
        if (listeners.size) reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS);
    };
};

/** codes 종목의 시세가 올 때마다 onTick(업비트 ticker 메시지)을 부른다. 반환값을 부르면 구독 해제 */
export function subscribeTickers(codes, onTick) {
    const listener = {codes, onTick};
    listeners.add(listener);
    clearTimeout(idleTimer);
    if (!ws) connect();
    else sendSubscription();

    return () => {
        listeners.delete(listener);
        if (listeners.size) {
            sendSubscription();
            return;
        }
        idleTimer = setTimeout(() => {
            if (listeners.size) return;
            clearTimeout(reconnectTimer);
            ws?.close();
            ws = null;
        }, IDLE_CLOSE_MS);
    };
}
