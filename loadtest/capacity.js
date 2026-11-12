// S1 용량 측정: 로그인한 동시 사용자 N명
//   가상 사용자 1명 = 알림 SSE 연결 1개 유지 + 30%는 체결 내역 WebSocket + 5~15초마다 화면 하나를 연다(browse)
//   시세는 브라우저가 업비트에 직접 연결하므로 서버 부하에 넣지 않는다.
//   실행: N=200 HOLD=5m 으로 단계마다 따로 돌린다 (run.sh 참고)
import http from 'k6/http';
import {WebSocket} from 'k6/websockets';
import {Counter} from 'k6/metrics';
import {sleep} from 'k6';
import {BASE, WS_BASE, SEASON_ID, SYMBOLS, tokens, token, params, record, openPage, pick} from './lib/common.js';

const N = parseInt(__ENV.N || '50');
const RAMP = __ENV.RAMP || '1m';
const HOLD = __ENV.HOLD || '5m';
const TOTAL_S = toSeconds(RAMP) + toSeconds(HOLD);
const POSTS = (__ENV.POSTS || '').split(',').filter(Boolean);

const wsMessages = new Counter('ws_messages');
const ordersPlaced = new Counter('orders_placed');

if (tokens.length < N) throw new Error(`토큰이 ${tokens.length}개뿐입니다 (N=${N})`);

export const options = {
    scenarios: {
        // 연결 유지형: 끊기면 바로 다시 연결 (브라우저 EventSource와 같다)
        sse: {executor: 'ramping-vus', exec: 'sse', startVUs: 0, gracefulRampDown: '0s',
            stages: [{duration: RAMP, target: N}, {duration: HOLD, target: N}]},
        ws: {executor: 'ramping-vus', exec: 'tradeWs', startVUs: 0, gracefulRampDown: '0s',
            stages: [{duration: RAMP, target: Math.max(1, Math.round(N * 0.3))}, {duration: HOLD, target: Math.max(1, Math.round(N * 0.3))}]},
        // 화면 이동: 램프업 동안 0 → N명, 이후 HOLD 동안 유지
        browse: {executor: 'ramping-vus', exec: 'browse', startVUs: 0, gracefulRampDown: '10s',
            stages: [{duration: RAMP, target: N}, {duration: HOLD, target: N}]},
    },
    thresholds: {
        'http_req_duration{kind:api}': ['p(95)<500'],
        api_errors: ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 화면별 비율(%)과 그 화면이 처음 열릴 때 부르는 API (프론트 pages/·components/ 기준)
const pages = [
    [25, 'home', () => openPage('home', [
        ['/users/me'], ['/notifications/count'], ['/public/ranking'], ['/coins/markets'],
        ['/coins/high-trade-price'], ['/coins/price-increase'], ['/coins/price-decrease'],
        ['/market-index/today', null, [200, 204, 404]], ['/market-index/yesterday', null, [200, 204, 404]],
        ['/seasons/current'], ['/assets/owned']])],
    [30, 'trade', trade],
    [10, 'investments', () => openPage('investments', [
        ['/assets/owned'], ['/assets'], ['/favorites'], ['/orders/reserve'], ['/coins/markets']])],
    [10, 'rank', () => {
        openPage('rank', [['/rank/current'], ['/seasons'], ['/seasons/current']]);
        sleep(1);
        const uid = 1 + Math.floor(Math.random() * 1000);   // 순위표에서 다른 사용자 카드에 마우스 올리기
        record(http.get(`${BASE}/rank/detail/${uid}`, params('/rank/detail/{id}')), [200, 404]);
    }],
    [10, 'trends', () => openPage('trends', [
        ['/ai-insights/today-symbols'], [`/coins/${pick(SYMBOLS)}/insight`, '/coins/{symbol}/insight', [200, 204, 404]],
        ['/coins/caution'], ['/coins/alert'], ['/coins/high-trade-price']])],
    [10, 'community', () => {
        openPage('community', [['/posts/list?page=0&size=10&sort=latest', '/posts/list']]);
        if (POSTS.length) {
            const id = pick(POSTS);
            openPage('post', [[`/posts/${id}`, '/posts/{id}'], [`/comments/post/${id}`, '/comments/post/{id}']]);
        }
    }],
    [5, 'mypage', () => openPage('mypage', [
        [`/mypage/tier?seasonId=${SEASON_ID}`, '/mypage/tier', [200, 204, 404]],
        [`/users/trade-summary?seasonId=${SEASON_ID}`, '/users/trade-summary'],
        [`/users/trade-details?seasonId=${SEASON_ID}`, '/users/trade-details'],
        ['/notifications?page=0&size=20&group=ALL', '/notifications']])],
];
const WEIGHT = pages.reduce((s, [w]) => s + w, 0);

function trade() {
    const sym = pick(SYMBOLS);
    openPage('trade', [['/coins/markets'], [`/coins/${sym}/hourly-closes`, '/coins/{symbol}/hourly-closes'],
        ['/assets'], ['/assets/owned'], ['/favorites'], ['/orders/reserve']]);
    sleep(2 + Math.random() * 3);   // 주문 칸을 보고 결정하는 시간
    const r = Math.random();
    if (r < 0.5) {        // 절반: 시장가 1만원 매수
        record(http.post(`${BASE}/trade`, JSON.stringify({symbol: sym, orderType: 'BUY', totalPrice: 10000}),
            params('POST /trade')), [200, 201]);
        ordersPlaced.add(1, {type: 'market'});
    } else if (r < 0.7) { // 20%: 체결되지 않을 낮은 지정가를 걸었다가 취소
        const res = record(http.post(`${BASE}/orders/reserve`,
            JSON.stringify({symbol: 'KRW-BTC', orderType: 'BUY', amount: 0.0001, reservePrice: 1000000}),
            params('POST /orders/reserve')), [200, 201]);
        ordersPlaced.add(1, {type: 'limit'});
        if (res.status === 201 || res.status === 200) {
            const id = res.json('orderId') || res.json('id');
            if (id) record(http.del(`${BASE}/orders/reserve/${id}`, null, params('DELETE /orders/reserve/{id}')));
        }
    }
}

export function browse() {
    let r = Math.random() * WEIGHT;
    for (const [w, , fn] of pages) {
        if ((r -= w) < 0) {
            fn();
            break;
        }
    }
    sleep(5 + Math.random() * 10);   // 다음 화면으로 넘어가기 전 머무는 시간
}

// 알림 구독: 서버가 끝내지 않는 응답이라, 시나리오가 끝날 때까지 기다렸다가 끊는다
export function sse() {
    http.get(`${BASE}/notifications/subscribe`, {
        cookies: {jwt_token: token()}, timeout: `${TOTAL_S + 30}s`, responseType: 'none',
        headers: {Accept: 'text/event-stream'}, tags: {kind: 'sse', name: 'sse'},
    });
}

export function tradeWs() {
    const ws = new WebSocket(`${WS_BASE}/ws/trade/history`);
    ws.onopen = () => ws.send(JSON.stringify({action: 'subscribe', symbol: 'KRW-BTC'}));
    ws.onmessage = () => wsMessages.add(1);
    setTimeout(() => ws.close(), (TOTAL_S + 5) * 1000);
}

function toSeconds(d) {
    const m = /^(\d+)(s|m|h)$/.exec(d);
    return m ? parseInt(m[1]) * {s: 1, m: 60, h: 3600}[m[2]] : 60;
}
