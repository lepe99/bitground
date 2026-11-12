# 부하 테스트

운영 서버(EC2 t4g.small)를 대상으로 하는 k6 시나리오와 서버 준비·측정·정리 스크립트. 결과는 [docs/LOAD_TEST.md](../docs/LOAD_TEST.md).

## 구성
| 파일 | 역할 |
|---|---|
| `smoke.js` | 로그인 없는 API 확인 (10명, 1분) |
| `capacity.js` | S1 용량 측정. 가상 사용자 1명 = 알림 SSE 1개 + 30%는 체결 WebSocket + 5~15초마다 화면 이동 |
| `seed.js` | 커뮤니티 화면용 게시글 40개·댓글 200개 |
| `lib/common.js` | 대상 주소, VU별 토큰, 화면 단위 요청(`openPage`), 실패율·화면 시간 지표 |
| `run.sh` / `series.sh` | S1 한 단계 / 여러 단계 실행 (서버 기록 → k6 → 서버 지표 수집) |
| `ssm-run.sh` | 로컬 스크립트를 운영 서버에서 SSM Run Command로 실행 |
| `server/prepare.sh` | 백업 → 기준 행 수 → 가상 사용자(`provider='loadtest'`) → 토큰 → 요청 제한 임시 해제 |
| `server/sample.sh` | 측정 구간 표시, 컨테이너 자원·데드락·Nginx 오류·OOM 기록 |
| `server/prom.sh` | 모니터링 서버 Prometheus에서 구간 지표 조회 |
| `server/burst.sh` | S2 지정가 체결 버스트 |
| `server/cleanup.sh` | 테스트 데이터 삭제 → 요청 제한 원복 → 토큰 삭제 |

## 실행
필요: Docker, `grafana/k6:2.3.0`, AWS CLI(`aws login --profile bitground`). Git Bash 기준.
```bash
export AWS_PROFILE=bitground
./ssm-run.sh server/prepare.sh 600
aws s3 cp s3://bitground-prod-backup/mysql/loadtest/tokens.json .secrets/tokens.json --region ap-northeast-2
MSYS_NO_PATHCONV=1 docker run --rm -v "$(cygpath -w "$PWD"):/scripts" grafana/k6:2.3.0 run --quiet /scripts/seed.js   # 출력의 POST_ID를 results/posts.txt에 쉼표로
./series.sh 200 300 500 750 1000          # 단계마다 램프업 1분 + 유지 5분
printf 'N=1000\n' | cat - server/burst.sh > /tmp/b.sh && ./ssm-run.sh /tmp/b.sh 300
./ssm-run.sh server/cleanup.sh 600         # 반드시 실행: 요청 제한 원복
rm .secrets/tokens.json
aws s3 rm s3://bitground-prod-backup/mysql/loadtest/tokens.json --region ap-northeast-2
```
토큰(`.secrets/`)과 원본 결과(`results/raw/`)는 커밋하지 않는다.
