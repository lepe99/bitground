# 프론트 정적 파일을 Nginx로 서빙하는 이미지 (S3+CloudFront 대신 컨테이너로 띄울 때 사용)
# 1) Build 단계: Node LTS
FROM node:22-alpine AS builder
WORKDIR /app

# lock 파일 기준으로 정확히 같은 버전을 설치 (package 파일만 먼저 복사해 의존성 레이어 캐시)
COPY package.json package-lock.json ./
RUN npm ci

# 소스 복사 후 빌드 (API 주소는 빌드 시점에 번들에 들어감)
ARG VITE_API_URL
ENV VITE_API_URL=${VITE_API_URL}
COPY . .
RUN npm run build

# 2) Serve 단계: Nginx로 정적 파일 서빙
FROM nginx:stable-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
