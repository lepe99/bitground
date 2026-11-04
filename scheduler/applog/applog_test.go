package applog

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"testing"
	"time"
)

func TestJSONFieldsMatchBackend(t *testing.T) {
	var buf bytes.Buffer
	Setup(&buf, "json", slog.LevelInfo)
	time.Local = time.UTC // 실행 중 시간대가 바뀌어도 KST로 찍혀야 한다

	ctx := With(context.Background(), slog.String("run_id", "r1"), slog.String("period", "2026-11-04T17"))
	ctx = With(ctx, slog.String("job", "랭킹 업데이트"))
	slog.WarnContext(ctx, "재시도", "attempt", 2)
	slog.Debug("안 보여야 함")

	lines := strings.Split(strings.TrimSpace(buf.String()), "\n")
	if len(lines) != 1 {
		t.Fatalf("줄 수 = %d, want 1:\n%s", len(lines), buf.String())
	}
	var got map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &got); err != nil {
		t.Fatal(err)
	}
	for key, want := range map[string]any{
		"level": "WARN", "message": "재시도", "run_id": "r1", "period": "2026-11-04T17", "job": "랭킹 업데이트", "attempt": float64(2),
	} {
		if got[key] != want {
			t.Errorf("%s = %v, want %v", key, got[key], want)
		}
	}
	if ts, _ := got["@timestamp"].(string); !strings.HasSuffix(ts, "+09:00") {
		t.Errorf("@timestamp = %q, KST(+09:00)여야 함", ts)
	}
	if _, ok := got["msg"]; ok {
		t.Error("msg 대신 message여야 함")
	}
}

func TestWithDoesNotLeakBetweenContexts(t *testing.T) {
	var buf bytes.Buffer
	Setup(&buf, "json", slog.LevelInfo)

	base := With(context.Background(), slog.String("run_id", "r1"))
	a := With(base, slog.String("job", "a"))
	_ = With(base, slog.String("job", "b"))
	slog.InfoContext(a, "x")

	if strings.Count(buf.String(), `"job"`) != 1 || !strings.Contains(buf.String(), `"job":"a"`) {
		t.Errorf("다른 작업의 속성이 섞임: %s", buf.String())
	}
}
