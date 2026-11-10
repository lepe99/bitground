// 업비트 시세 WebSocket. 탭 하나에 연결 하나만 열어 화면 전체(메인 카드, 거래소, 투자내역)가 함께 쓴다.
// 업비트는 브라우저에서 온 요청(Origin 헤더 포함)의 새 연결·REST 호출을 약 10초에 1회로 제한한다.
// 한 번 연결된 뒤의 시세 수신에는 제한이 없으므로, 연결을 하나로 묶고 다시 연결할 때는 10초 넘게 간격을 둔다.
// (목록·거래대금·추이 그래프 같은 REST 데이터는 우리 서버가 대신 받아 캐시한 것을 쓴다: api/coinApi.js)
const UPBIT_WS_URL = 'wss://api.upbit.com/websocket/v1';
const RECONNECT_MIN_MS = 11_000;
const RECONNECT_MAX_MS = 60_000;
// 화면 이동으로 구독이 잠깐 비었다 다시 생기는 경우 연결을 끊지 않고 기다리는 시간 (다시 열면 10초 제한에 걸리므로 넉넉히)
const IDLE_CLOSE_MS = 30_000;

const listeners = new Set(); // {codes: Set<string>, onTick}
const statusListeners = new Set(); // (connected: boolean) => void
const decoder = new TextDecoder();
let ws = null;
let connected = false;
let sentCodes = '';
let reconnectTimer = null;
let reconnectDelay = RECONNECT_MIN_MS;
let idleTimer = null;

const setConnected = (value) => {
    if (connected === value) return;
    connected = value;
    for (const l of statusListeners) l(value);
};

const wantedCodes = () => [...new Set([...listeners].flatMap((l) => [...l.codes]))].sort();

// 구독 종목이 바뀌면 같은 연결에 요청을 다시 보낸다 (업비트는 마지막 요청으로 구독을 바꾸고, 구독 직후 현재 시세를 먼저 보낸다)
const sendSubscription = () => {
    const codes = wantedCodes();
    const key = codes.join(',');
    if (ws?.readyState !== WebSocket.OPEN || !codes.length || key === sentCodes) return;
    ws.send(JSON.stringify([{ticket: 'bitground'}, {type: 'ticker', codes}]));
    sentCodes = key;
};

// 화면이 열릴 때 카드 여러 개가 거의 동시에 구독하므로, 잠깐 모았다가 요청 한 번으로 보낸다
// (업비트 WebSocket 메시지 제한: 연결당 초당 5회·분당 100회)
const SEND_DEBOUNCE_MS = 50;
let sendTimer = null;
const scheduleSubscription = () => {
    clearTimeout(sendTimer);
    sendTimer = setTimeout(sendSubscription, SEND_DEBOUNCE_MS);
};

const connect = () => {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    const socket = new WebSocket(UPBIT_WS_URL);
    ws = socket;
    socket.binaryType = 'arraybuffer';
    sentCodes = '';
    socket.onopen = () => {
        reconnectDelay = RECONNECT_MIN_MS;
        setConnected(true);
        sendSubscription();
    };
    socket.onmessage = (e) => {
        const tick = JSON.parse(typeof e.data === 'string' ? e.data : decoder.decode(e.data));
        for (const l of listeners) {
            if (l.codes.has(tick.code)) l.onTick(tick);
        }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
        // 이미 닫고 새로 연결한 뒤에 늦게 온 close라면 새 연결을 건드리지 않는다
        if (ws !== socket) return;
        ws = null;
        setConnected(false);
        if (!listeners.size) return;
        // 연결이 거절되거나 끊기면 10초 제한을 넘겨 다시 시도하고, 계속 실패하면 간격을 늘린다 (다른 탭과 겹쳐도 몰리지 않게 약간 흩뜨림)
        const delay = reconnectDelay + Math.floor(Math.random() * 2000);
        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS);
        reconnectTimer = setTimeout(connect, delay);
    };
};

const ensureConnected = () => {
    clearTimeout(idleTimer);
    if (!ws && !reconnectTimer) connect();
    else scheduleSubscription();
};

/** codes 종목의 시세가 올 때마다 onTick(업비트 ticker 메시지)을 부른다. 반환값을 부르면 구독 해제 */
export function subscribeTickers(codes, onTick) {
    const listener = {codes: new Set(codes), onTick};
    listeners.add(listener);
    ensureConnected();

    return () => {
        listeners.delete(listener);
        if (listeners.size) {
            scheduleSubscription();
            return;
        }
        idleTimer = setTimeout(() => {
            if (listeners.size) return;
            clearTimeout(reconnectTimer);
            reconnectTimer = null;
            const socket = ws;
            ws = null;
            socket?.close();
            setConnected(false);
        }, IDLE_CLOSE_MS);
    };
}

/** 연결 상태가 바뀔 때마다 onChange(connected)를 부르고, 지금 상태로 한 번 바로 부른다. 반환값을 부르면 해제 */
export function subscribeConnection(onChange) {
    statusListeners.add(onChange);
    onChange(connected);
    return () => statusListeners.delete(onChange);
}
