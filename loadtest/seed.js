// 커뮤니티 화면용 테스트 데이터: 가상 사용자들이 게시글 40개, 글마다 댓글 5개를 쓴다 (한 번만 실행)
//   만든 게시글 번호는 마지막 요약(handleSummary)에 출력되고 /scripts/results/posts.txt에 저장된다
import http from 'k6/http';
import exec from 'k6/execution';
import {BASE, tokens} from './lib/common.js';

export const options = {
    scenarios: {seed: {executor: 'shared-iterations', vus: 4, iterations: 40, maxDuration: '5m'}},
};

const auth = (i) => ({cookies: {jwt_token: tokens[1000 + i]}, headers: {'Content-Type': 'application/json'}});
const created = [];

export default function () {
    const i = exec.scenario.iterationInTest;
    const res = http.post(`${BASE}/posts/form`, JSON.stringify({
        category: ['CHAT', 'INFO', 'QUESTION'][i % 3],
        title: `부하 테스트 글 ${i}`,
        content: '<p>' + '부하 테스트 본문입니다. '.repeat(40) + '</p>',
    }), auth(i));
    if (res.status !== 201) throw new Error(`게시글 실패 ${res.status} ${res.body}`);
    const postId = res.json('id');
    for (let j = 0; j < 5; j++) {
        http.post(`${BASE}/comments`, JSON.stringify({postId, content: `테스트 댓글 ${j}`}), auth(100 + i * 5 + j));
    }
    console.log(`POST_ID ${postId}`);
}
