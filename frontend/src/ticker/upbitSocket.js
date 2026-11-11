// 업비트 시세 WebSocket. 탭 하나에 연결 하나만 열어 화면 전체(메인 카드, 거래소, 투자내역)가 함께 쓴다.
// 업비트는 브라우저에서 온 요청(Origin 헤더 포함)의 새 연결·REST 호출을 약 10초에 1회로 제한한다.
// 한 번 연결된 뒤의 시세 수신에는 제한이 없으므로, 연결을 하나로 묶고 새 연결 시도 사이에는 10초 넘게 간격을 둔다.
// (목록·거래대금·추이 그래프 같은 REST 데이터는 우리 서버가 대신 받아 캐시한 것을 쓴다: api/coinApi.js)
const UPBIT_WS_URL = 'wss://api.upbit.com/websocket/v1';
// 직전 연결 시도로부터 이만큼 지나야 다시 연결한다 (업비트 제한 10초 + 여유)
const CONNECT_GAP_MS = 10_500;
// 연결이 계속 실패할 때만 늘리는 대기 (오래 잘 붙어 있다 끊긴 경우는 바로 다시 연결)
const RETRY_MAX_MS = 60_000;
// 화면 이동으로 구독이 잠깐 비었다 다시 생기는 경우 연결을 끊지 않고 기다리는 시간
const IDLE_CLOSE_MS = 60_000;
// 탭으로 돌아왔을 때 이 시간 넘게 시세가 안 왔으면 끊긴 연결로 보고 새로 연결한다 (절전·네트워크 변경 후 반쯤 죽은 연결)
const STALE_MS = 15_000;

const listeners = new Set(); // {codes: Set<string>, onTick}
const lastTicks = new Map(); // 종목별 마지막 시세. 새 구독자에게 바로 넘겨 재연결을 기다리지 않고 화면을 채운다
const decoder = new TextDecoder();
let ws = null;
let connected = false;
let sentCodes = '';
let reconnectTimer = null;
let failures = 0; // 열리지 못하고 끝난 연속 시도 수
let lastAttemptAt = 0;
let lastMessageAt = 0;
let idleTimer = null;

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
    lastAttemptAt = Date.now();
    const socket = new WebSocket(UPBIT_WS_URL);
    ws = socket;
    socket.binaryType = 'arraybuffer';
    sentCodes = '';
    let opened = false;
    socket.onopen = () => {
        opened = true;
        failures = 0;
        lastMessageAt = Date.now();
        connected = true;
        sendSubscription();
    };
    socket.onmessage = (e) => {
        lastMessageAt = Date.now();
        const tick = JSON.parse(typeof e.data === 'string' ? e.data : decoder.decode(e.data));
        lastTicks.set(tick.code, tick);
        for (const l of listeners) {
            if (l.codes.has(tick.code)) l.onTick(tick);
        }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
        // 이미 닫고 새로 연결한 뒤에 늦게 온 close라면 새 연결을 건드리지 않는다
        if (ws !== socket) return;
        ws = null;
        connected = false;
        if (!opened) failures += 1;
        scheduleReconnect();
    };
};

// 다음 연결 시각: 직전 시도로부터 10초 넘게, 연속 실패면 간격을 늘린다 (다른 탭과 겹쳐도 몰리지 않게 약간 흩뜨림)
const scheduleReconnect = ({ignoreBackoff = false} = {}) => {
    if (!listeners.size || ws) return;
    const backoff = ignoreBackoff || failures <= 1 ? 0 : Math.min(CONNECT_GAP_MS * 2 ** (failures - 1), RETRY_MAX_MS);
    const earliest = lastAttemptAt + Math.max(CONNECT_GAP_MS, backoff);
    const jitter = failures ? Math.floor(Math.random() * 1000) : 0;
    clearTimeout(reconnectTimer);
    reconnectTimer = setTimeout(connect, Math.max(0, earliest - Date.now()) + jitter);
};

// 탭으로 돌아오거나 네트워크가 다시 잡히면 기다리던 재연결을 앞당기고, 조용히 죽은 연결은 새로 연다.
// (백그라운드 탭에서는 브라우저가 타이머를 크게 늦추므로 예약해 둔 재연결이 제때 돌지 않는다)
const revive = () => {
    if (document.visibilityState !== 'visible' || !listeners.size) return;
    if (ws && connected && Date.now() - lastMessageAt > STALE_MS) {
        const socket = ws;
        ws = null;
        socket.close();
        connected = false;
    }
    if (!ws) scheduleReconnect({ignoreBackoff: true});
};
document.addEventListener('visibilitychange', revive);
window.addEventListener('online', revive);
window.addEventListener('pageshow', revive);

const ensureConnected = () => {
    clearTimeout(idleTimer);
    if (ws) scheduleSubscription();
    else if (!reconnectTimer) scheduleReconnect({ignoreBackoff: true});
};

/** codes 종목의 시세가 올 때마다 onTick(업비트 ticker 메시지)을 부른다. 받아 둔 시세가 있으면 바로 한 번씩 넘긴다. 반환값을 부르면 구독 해제 */
export function subscribeTickers(codes, onTick) {
    const listener = {codes: new Set(codes), onTick};
    listeners.add(listener);
    for (const code of listener.codes) {
        const tick = lastTicks.get(code);
        if (tick) onTick(tick);
    }
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
            connected = false;
        }, IDLE_CLOSE_MS);
    };
}
