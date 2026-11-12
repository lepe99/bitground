// 연습·연결 확인용: 로그인 없이 볼 수 있는 API만 10명이 1분 동안 부른다
//   docker run --rm -v "<loadtest 폴더>:/scripts" grafana/k6:2.3.0 run /scripts/smoke.js
import http from 'k6/http';
import {check, sleep} from 'k6';

const BASE = __ENV.BASE_URL || 'https://api.bitground.kr';

export const options = {
    vus: 10,
    duration: '1m',
    thresholds: {
        http_req_duration: ['p(95)<500'],   // 95%가 0.5초 안에
        http_req_failed: ['rate<0.01'],     // 실패 1% 미만
    },
};

export default function () {
    const res = http.get(`${BASE}/seasons/current`, {tags: {name: '/seasons/current'}});
    check(res, {'200 응답': (r) => r.status === 200});
    http.get(`${BASE}/coins/high-trade-price`, {tags: {name: '/coins/high-trade-price'}});
    sleep(1 + Math.random());   // 사람처럼 1~2초 쉬기
}
