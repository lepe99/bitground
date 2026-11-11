#!/bin/sh
# nginx 공식 이미지의 /docker-entrypoint.d/ 단계에서 실행된다 (nginx 시작 직전)
# 1) Grafana 기본 인증 파일 생성  2) 인증서가 있는 사이트만 HTTPS 설정 생성  3) 인증서가 새로 생기거나 갱신되면 다시 만들고 reload
# Nginx는 설정에 적힌 인증서 파일이 없으면 뜨지 않으므로, 첫 기동은 80번(인증서 발급용)만 열고 certbot이 받아 오면 HTTPS를 켠다.
set -eu

: "${API_DOMAIN:?}" "${GRAFANA_DOMAIN:?}" "${MONITORING_HOST:?}" "${GRAFANA_BASIC_AUTH_USER:?}" "${GRAFANA_BASIC_AUTH_HASH:?}"

LIVE=/etc/letsencrypt/live
SITES=/etc/bitground/sites
CONF=/etc/nginx/conf.d

# GRAFANA_BASIC_AUTH_HASH는 bcrypt 해시를 base64로 인코딩한 값 (bcrypt의 '$'가 compose .env에서 변수로 해석되지 않게)
printf '%s:%s\n' "$GRAFANA_BASIC_AUTH_USER" "$(printf '%s' "$GRAFANA_BASIC_AUTH_HASH" | base64 -d)" > /etc/nginx/grafana.htpasswd
chmod 644 /etc/nginx/grafana.htpasswd

if [ -n "${APEX_DOMAIN:-}" ]; then : "${SITE_URL:?APEX_DOMAIN을 쓰려면 SITE_URL(리다이렉트할 주소)이 필요}"; fi

render() {
    rm -f "$CONF"/*.conf
    cp /etc/bitground/base.conf "$CONF/00-base.conf"
    for site in api grafana apex; do
        case $site in
            api) domain=$API_DOMAIN ;;
            grafana) domain=$GRAFANA_DOMAIN ;;
            apex) domain=${APEX_DOMAIN:-} ;; # 선택: www 없는 주소 → SITE_URL 리다이렉트
        esac
        [ -n "$domain" ] || continue
        if [ -s "$LIVE/$domain/fullchain.pem" ]; then
            # 템플릿의 nginx 변수($host 등)는 그대로 두고 이 값들만 채운다
            envsubst '${API_DOMAIN} ${GRAFANA_DOMAIN} ${MONITORING_HOST} ${APEX_DOMAIN} ${SITE_URL}' < "$SITES/$site.conf.template" > "$CONF/$site.conf"
            echo "[bitground] $domain: HTTPS"
        else
            echo "[bitground] $domain: 인증서 없음, 발급될 때까지 80번만"
        fi
    done
}

# 인증서 파일의 실제 대상과 수정 시각 (certbot은 갱신할 때 새 파일을 만들고 링크를 바꾼다)
cert_state() {
    for f in "$LIVE"/*/fullchain.pem; do
        [ -e "$f" ] && echo "$f $(stat -L -c %Y "$f")"
    done
    return 0
}

render

(
    last=$(cert_state)
    while sleep 30; do
        now=$(cert_state)
        [ "$now" = "$last" ] && continue
        last=$now
        render
        if nginx -t -q; then nginx -s reload && echo "[bitground] 인증서 변경 → reload"; else echo "[bitground] 설정 오류, reload 건너뜀"; fi
    done
) &
