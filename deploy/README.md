# 운영 배포 구성

로컬 개발 환경은 저장소 루트의 `docker-compose.yml`, 운영은 이 폴더를 씁니다.

```
인터넷 ──443──▶ 앱 서버 (t4g.small, 퍼블릭 서브넷)          모니터링 서버 (t4g.micro, 프라이빗 서브넷)
               nginx ─▶ backend ─▶ mysql, redis             loki ◀── 앱 서버 alloy (로그)
               alloy ──────────────────────────────────────▶ prometheus ◀── 앱 서버 alloy (지표)
               tinyproxy ◀── 모니터링 서버의 외부 통신        alloy:3500 ◀── lambda-promtail (Lambda 로그)
                                                            grafana ◀── 앱 서버 nginx (기본 인증)
EventBridge ─▶ Lambda A (수집, VPC 밖) ─▶ Lambda B (쓰기, VPC 안) ─▶ mysql:3306, nginx:8090 → backend
CloudFront ─▶ S3 (프론트)
```

| 경로 | 내용 |
|---|---|
| `app/compose.yml` | 앱 서버: nginx, certbot, backend, mysql, redis, alloy, tinyproxy |
| `app/nginx/` | HTTPS 종료·프록시(`/healthz`, API·WebSocket·SSE, Grafana 기본 인증), certbot 발급·갱신 스크립트 |
| `app/alloy/config.alloy` | 컨테이너 로그·백엔드 지표·호스트 지표 → 모니터링 서버 |
| `app/tinyproxy/` | 모니터링 서버용 외부 통신 프록시 |
| `app/rollout.sh` | 백엔드 무중단 교체 (새 컨테이너 healthy → 이전 컨테이너 정상 종료, 실패 시 이전 유지). `deploy.sh`가 부릅니다 |
| `app/backup.sh` | MySQL → S3 백업 (호스트 cron) |
| `monitoring/compose.yml` | 모니터링 서버: loki, prometheus, grafana, alloy(Lambda 로그 수신) |
| `monitoring/deploy.sh` | 모니터링 서버 배포 (SSM에서 `.env` 재생성, 설정이 바뀐 서비스만 재시작, grafana·loki·prometheus 응답 확인). `monitoring/`이나 `deploy/monitoring/`이 바뀌면 배포 워크플로가 부릅니다 |
| `monitoring/grafana/alerting/` | 운영 전용 Grafana 경보: 규칙 7개(스케줄러 실패·미실행, 백엔드 5xx, 메모리, 스왑, 디스크, Redis 메모리)와 디스코드 연락처. 웹후크 주소는 `.env`의 `DISCORD_WEBHOOK_URL`로 받습니다 |

서버에는 `deploy/`와 `monitoring/`을 같은 구조로 올립니다 (모니터링 compose가 `../../monitoring/`의 설정을 같이 씁니다).
비밀값은 `.env`, `backend.env`로 두고 커밋하지 않습니다 (`*.env.example` 참고). 운영에서는 배포할 때 SSM Parameter Store에서 만듭니다.

서버를 준비할 때 챙길 것:
- 스왑 2GiB, `vm.swappiness=10`
- EC2 IMDSv2 홉 제한 2 (컨테이너의 백엔드가 IAM 역할로 S3에 접근)
- 모니터링 서버의 Docker·SSM 에이전트가 앱 서버 tinyproxy(`http://<앱 서버 프라이빗 IP>:8888`)를 프록시로 쓰도록 설정
- Grafana 기본 인증 해시는 bcrypt 비용 5로 만듭니다(`htpasswd -nbB -C 5`, `app/.env.example` 참고). Nginx가 요청마다 해시를 검증하므로 비용을 높이면 Grafana와 API가 함께 느려집니다. 비용 14에서는 Grafana 화면 하나를 여는 데 30초 넘게 걸렸습니다.
- 파일로 불러온 경보 규칙은 Grafana 화면에서 고칠 수 없습니다. `monitoring/grafana/alerting/`을 고쳐 커밋하면 배포 워크플로가 반영합니다.
