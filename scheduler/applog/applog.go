// Package applog 스케줄러 로그 설정 (log/slog).
//
//   - 출력: 기본 JSON 한 줄(Lambda·Docker → CloudWatch·Loki), LOG_FORMAT=text면 터미널용 텍스트
//   - 필드 이름은 백엔드(logstash 형식)와 맞춘다: @timestamp, level(INFO/WARN/ERROR), message
//     → Alloy 처리 단계와 Grafana 질의를 백엔드와 같이 쓴다
//   - 시각은 항상 한국 시각(+09:00). 실행 중에 time.Local을 KST로 바꿔도 줄마다 시간대가 달라지지 않는다
//   - 실행(run_id·period)과 작업(job) 정보는 context에 담아 두면 그 context로 남기는 모든 줄에 붙는다
//     (병렬 작업의 줄이 섞여도 실행·작업별로 골라 볼 수 있게)
package applog

import (
	"context"
	"io"
	"log"
	"log/slog"
	"os"
	"strings"
	"time"
)

// KST 한국은 서머타임이 없어 고정 오프셋으로 충분하다
var KST = time.FixedZone("KST", 9*60*60)

type ctxKey struct{}

// Setup 기본 로거를 설정한다. 기존 log 패키지 출력도 같은 형식(INFO)으로 보낸다
func Setup(w io.Writer, format string, level slog.Level) {
	opts := &slog.HandlerOptions{Level: level, ReplaceAttr: replaceAttr}
	var h slog.Handler
	if strings.EqualFold(format, "text") {
		h = slog.NewTextHandler(w, opts)
	} else {
		h = slog.NewJSONHandler(w, opts)
	}
	slog.SetDefault(slog.New(contextHandler{h}))
	log.SetFlags(0)
}

// SetupFromEnv LOG_FORMAT(json|text), LOG_LEVEL(debug|info|warn|error)로 설정한다
func SetupFromEnv() {
	var level slog.Level
	if err := level.UnmarshalText([]byte(getenv("LOG_LEVEL", "info"))); err != nil {
		level = slog.LevelInfo
	}
	Setup(os.Stdout, getenv("LOG_FORMAT", "json"), level)
}

// With 이후 이 context로 남기는 로그에 attrs를 붙인다 (예: run_id, period, job)
func With(ctx context.Context, attrs ...slog.Attr) context.Context {
	prev, _ := ctx.Value(ctxKey{}).([]slog.Attr)
	merged := append(append([]slog.Attr{}, prev...), attrs...)
	return context.WithValue(ctx, ctxKey{}, merged)
}

func replaceAttr(groups []string, a slog.Attr) slog.Attr {
	if len(groups) > 0 {
		return a
	}
	switch a.Key {
	case slog.TimeKey:
		return slog.String("@timestamp", a.Value.Time().In(KST).Format("2006-01-02T15:04:05.000Z07:00"))
	case slog.MessageKey:
		a.Key = "message"
	}
	return a
}

// contextHandler context에 담긴 실행·작업 정보를 로그 줄에 붙인다
type contextHandler struct{ slog.Handler }

func (h contextHandler) Handle(ctx context.Context, r slog.Record) error {
	if attrs, ok := ctx.Value(ctxKey{}).([]slog.Attr); ok {
		r.AddAttrs(attrs...)
	}
	return h.Handler.Handle(ctx, r)
}

func (h contextHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return contextHandler{h.Handler.WithAttrs(attrs)}
}

func (h contextHandler) WithGroup(name string) slog.Handler {
	return contextHandler{h.Handler.WithGroup(name)}
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
