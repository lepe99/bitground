# 1) Build 단계: Node 23.11.0-alpine
FROM node:23.11.0-alpine AS builder
WORKDIR /app

# npm 버전 고정 (필요 없다면 생략 가능)
RUN npm install -g npm@10.9.2

# 패키지 매니저 잠금파일 설치
COPY package.json ./
RUN npm i

# 소스 복사 후 빌드
COPY .. .
RUN npm run build

# 2) Serve 단계: Nginx로 정적 파일 서빙
FROM nginx:stable-alpine
# 빌드 결과물을 Nginx 루트로 복사
COPY --from=builder /app/dist /usr/share/nginx/html
# nginx conf 파일 복사
COPY nginx.default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
