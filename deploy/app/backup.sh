#!/bin/sh
# MySQL 백업 → S3. 앱 서버 호스트의 cron에서 매일 실행한다 (예: 0 4 * * * /opt/bitground/deploy/app/backup.sh)
#   BACKUP_BUCKET  백업 버킷 이름 (오래된 백업은 버킷 수명 주기 규칙이 지운다)
# S3 권한은 EC2 IAM 역할(s3:PutObject, 해당 버킷의 mysql/ 경로만)로 받는다
set -eu

: "${BACKUP_BUCKET:?BACKUP_BUCKET 필요}"
cd "$(dirname "$0")"

stamp=$(date +%Y%m%d-%H%M)
key="mysql/bitground-${stamp}.sql.gz"

# --single-transaction: 테이블을 잠그지 않고 한 시점의 스냅샷으로 덤프 (서비스 중에도 안전)
# 비밀번호는 컨테이너 안의 환경변수로 넘겨 프로세스 목록·셸 기록에 남기지 않는다
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces bitground' \
  | gzip -9 \
  | aws s3 cp - "s3://${BACKUP_BUCKET}/${key}" --only-show-errors

echo "백업 완료: s3://${BACKUP_BUCKET}/${key}"
