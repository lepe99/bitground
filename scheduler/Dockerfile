# 로컬 테스트용 스케줄러 컨테이너 (운영은 AWS Lambda). docker-compose의 scheduler 서비스가 RUN_HOURLY=1로 매시 실행한다
FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /out/scheduler .

# 실행 이미지도 같은 이미지를 쓴다 (HTTPS 인증서가 들어 있고, 시간대 데이터는 바이너리에 포함 time/tzdata)
FROM golang:1.26-alpine
COPY --from=build /out/scheduler /usr/local/bin/scheduler
ENTRYPOINT ["scheduler"]
