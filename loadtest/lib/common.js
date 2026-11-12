// 시나리오 공통: 대상 주소, 가상 사용자 토큰, 요청 도우미, 지표
import http from 'k6/http';
import exec from 'k6/execution';
import {Rate, Trend} from 'k6/metrics';

export const BASE = __ENV.BASE_URL || 'https://api.bitground.kr';
export const WS_BASE = BASE.replace(/^http/, 'ws');
export const SEASON_ID = __ENV.SEASON_ID || '1';
export const SYMBOLS = ['KRW-BTC', 'KRW-ETH', 'KRW-XRP', 'KRW-SOL', 'KRW-DOGE'];

// 토큰 파일은 저장소에 넣지 않는다 (.secrets/ 는 .gitignore). 없으면 로그인 없는 시나리오만 가능
const TOKEN_FILE = __ENV.TOKENS || '/scripts/.secrets/tokens.json';
export const tokens = (() => {
    try {
        return JSON.parse(open(TOKEN_FILE));
    } catch (e) {
        return [];
    }
})();

/** 실패율: 허용한 상태 코드가 아니면 실패로 센다 (응답 시간과 따로 본다) */
export const apiErrors = new Rate('api_errors');
/** 화면 하나를 여는 데 걸린 시간 = 그 화면이 동시에 부르는 API 묶음이 모두 끝날 때까지 */
export const pageTime = new Trend('page_time', true);

const OK = [200, 201, 204];

/** VU마다 서로 다른 가상 사용자 토큰 (같은 토큰을 나눠 쓰면 사용자 행 잠금이 한 사람에게 몰린다) */
export function token() {
    return tokens[(exec.vu.idInTest - 1) % tokens.length];
}

export function params(name, extra = {}) {
    return Object.assign({
        cookies: {jwt_token: token()},
        headers: {'Content-Type': 'application/json'},
        tags: {kind: 'api', name},
        timeout: '30s',
    }, extra);
}

export function record(res, allowed = OK) {
    apiErrors.add(!allowed.includes(res.status), {name: res.request ? res.request.url : ''});
    return res;
}

/** 브라우저가 화면을 열 때처럼 여러 API를 동시에 부르고, 다 끝날 때까지의 시간을 page_time에 남긴다 */
export function openPage(page, paths) {
    const t0 = Date.now();
    const res = http.batch(paths.map(([path, name]) => ['GET', BASE + path, null, params(name || path)]));
    res.forEach((r, i) => record(r, paths[i][2] || OK));
    pageTime.add(Date.now() - t0, {page});
    return res;
}

export function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}
