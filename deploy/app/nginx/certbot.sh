#!/bin/sh
# Let's Encrypt 인증서 발급·갱신 (HTTP-01, nginx가 /var/www/certbot을 /.well-known/acme-challenge/로 서빙)
# 인증서가 없는 도메인은 발급하고, 이후 12시간마다 갱신을 시도한다 (만료 30일 이내인 것만 실제로 갱신)
# 결과 파일이 바뀌면 nginx 컨테이너(entrypoint.sh)가 알아채고 reload한다
set -u

: "${DOMAINS:?}" "${ACME_EMAIL:?}"

issue() {
    for d in $DOMAINS; do
        [ -s "/etc/letsencrypt/live/$d/fullchain.pem" ] && continue
        certbot certonly --webroot -w /var/www/certbot -d "$d" --cert-name "$d" \
            --email "$ACME_EMAIL" --agree-tos --no-eff-email --non-interactive || return 1
    done
}

sleep 10 # nginx가 80번을 열 때까지
# 실패가 반복되면 Let's Encrypt가 막으므로(같은 도메인 시간당 5회) 간격을 넉넉히
until issue; do
    echo "[certbot] 발급 실패, 10분 뒤 다시 시도"
    sleep 600
done

while :; do
    certbot renew --webroot -w /var/www/certbot --non-interactive --quiet || echo "[certbot] 갱신 실패"
    sleep 43200
done
