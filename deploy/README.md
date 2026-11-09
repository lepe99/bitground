# 운영 배포 구성

로컬 개발 환경은 저장소 루트의 `docker-compose.yml`, 운영은 이 폴더를 쓴다.

```
인터넷 ──443──▶ 앱 서버 (t4g.small, 퍼블릭 서브넷)          모니터링 서버 (t4g.micro, 프라이빗 서브넷)
               caddy ─▶ backend ─▶ mysql, redis             loki ◀── 앱 서버 alloy (로그)
               alloy ──────────────────────────────────────▶ prometheus ◀── 앱 서버 alloy (지표)
               tinyproxy ◀── 모니터링 서버의 외부 통신        alloy:3500 ◀── lambda-promtail (Lambda 로그)
                                                            grafana ◀── SSM 포트 포워딩
EventBridge ─▶ Lambda A (수집, VPC 밖) ─▶ Lambda B (쓰기, VPC 안) ─▶ mysql:3306, backend:8090
CloudFront ─▶ S3 (프론트)
```

| 경로 | 내용 |
|---|---|
| `app/compose.yml` | 앱 서버: caddy, backend, mysql, redis, alloy, tinyproxy |
| `app/Caddyfile` | HTTPS(Let's Encrypt), `/healthz` |
| `app/alloy/config.alloy` | 컨테이너 로그·백엔드 지표·호스트 지표 → 모니터링 서버 |
| `app/tinyproxy/` | 모니터링 서버용 외부 통신 프록시 |
| `app/backup.sh` | MySQL → S3 백업 (호스트 cron) |
| `monitoring/compose.yml` | 모니터링 서버: loki, prometheus, grafana, alloy(Lambda 로그 수신) |

서버에는 `deploy/`와 `monitoring/`을 같은 구조로 올린다 (모니터링 compose가 `../../monitoring/`의 설정을 같이 쓴다).
비밀값은 `.env`, `backend.env`로 두고 커밋하지 않는다 (`*.env.example` 참고). 운영에서는 배포할 때 SSM Parameter Store에서 만든다.

서버 준비에서 챙길 것:
- 스왑 2GiB, `vm.swappiness=10`
- EC2 IMDSv2 홉 제한 2 (컨테이너의 백엔드가 IAM 역할로 S3에 접근)
- 모니터링 서버의 Docker·SSM 에이전트가 앱 서버 tinyproxy(`http://<앱 서버 프라이빗 IP>:8888`)를 프록시로 쓰도록 설정
