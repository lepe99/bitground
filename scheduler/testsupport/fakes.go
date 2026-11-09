package testsupport

import (
	"Bitground-go/model"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"
)

// Market 업비트·CoinGecko 대역. 스케줄러는 기본 HTTP 전송(http.DefaultTransport)을 쓰므로
// Install로 그것을 바꿔 끼워 실제 네트워크로 나가지 않게 한다 (등록하지 않은 주소를 부르면 테스트 실패)
type Market struct {
	mu        sync.Mutex
	markets   []model.UpbitCoinList
	tickers   map[string]model.UpbitCoinPrice
	marketCap []map[string]any
}

func NewMarket() *Market {
	return &Market{tickers: map[string]model.UpbitCoinPrice{}}
}

// List 업비트 원화 시장에 상장된 코인 (마켓 목록·시세에 함께 나온다)
func (m *Market) List(symbol, koreanName string, price float64) *Market {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.markets = append(m.markets, model.UpbitCoinList{Market: symbol, KoreanName: koreanName})
	m.tickers[symbol] = model.UpbitCoinPrice{Market: symbol, TradePrice: price, PrevClosingPrice: price * 0.9,
		OpenPrice: price * 0.95, HighPrice: price * 1.05, LowPrice: price * 0.9, AccTradePrice: price * 1000, TradeVolume: 10}
	return m
}

// Delist 업비트 목록과 시세에서 뺀다 (거래지원 종료)
func (m *Market) Delist(symbol string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.tickers, symbol)
	kept := m.markets[:0]
	for _, c := range m.markets {
		if c.Market != symbol {
			kept = append(kept, c)
		}
	}
	m.markets = kept
}

// MarketCaps CoinGecko 시가총액 (지수 계산용)
func (m *Market) MarketCaps(caps map[string]int64) *Market {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.marketCap = nil
	for s, c := range caps {
		m.marketCap = append(m.marketCap, map[string]any{"symbol": s, "market_cap": c})
	}
	return m
}

// Install 테스트 동안 외부 호출을 이 대역으로 보낸다. 로컬 주소(가짜 백엔드)는 그대로 보낸다
func (m *Market) Install(t *testing.T) {
	t.Helper()
	original := http.DefaultTransport
	http.DefaultTransport = roundTripper(func(req *http.Request) (*http.Response, error) {
		host := req.URL.Hostname()
		if host == "127.0.0.1" || host == "localhost" {
			return original.RoundTrip(req)
		}
		return m.serve(req)
	})
	t.Cleanup(func() { http.DefaultTransport = original })
}

func (m *Market) serve(req *http.Request) (*http.Response, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var body any
	switch req.URL.Host + req.URL.Path {
	case "api.upbit.com/v1/market/all":
		body = m.markets
	case "api.upbit.com/v1/ticker/all":
		list := make([]model.UpbitCoinPrice, 0, len(m.tickers))
		for _, t := range m.tickers {
			list = append(list, t)
		}
		body = list
	case "api.coingecko.com/api/v3/coins/markets":
		body = m.marketCap
	default:
		return nil, fmt.Errorf("테스트에서 등록하지 않은 외부 호출: %s", req.URL)
	}
	b, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	return &http.Response{StatusCode: http.StatusOK, Header: http.Header{"Content-Type": {"application/json"}},
		Body: io.NopCloser(bytes.NewReader(b)), Request: req}, nil
}

type roundTripper func(*http.Request) (*http.Response, error)

func (f roundTripper) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

// Backend 시즌·스플릿 완료 알림(POST /seasons/update)을 받는 가짜 백엔드
type Backend struct {
	URL  string
	Key  string
	mu   sync.Mutex
	got  []string
	down bool
}

func NewBackend(t *testing.T, key string) *Backend {
	t.Helper()
	b := &Backend{Key: key}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b.mu.Lock()
		defer b.mu.Unlock()
		if b.down {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		body, _ := io.ReadAll(r.Body)
		form, _ := url.ParseQuery(string(body))
		if r.URL.Path != "/seasons/update" || r.Header.Get("X-Scheduler-Key") != b.Key {
			w.WriteHeader(http.StatusForbidden)
			return
		}
		b.got = append(b.got, form.Get("seasonFlag"))
		w.WriteHeader(http.StatusNoContent)
	}))
	t.Cleanup(srv.Close)
	b.URL = srv.URL
	return b
}

// SetDown true면 503으로 응답한다 (백엔드가 내려간 상황)
func (b *Backend) SetDown(down bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.down = down
}

// Received 받은 알림 종류 (season, split) 순서대로
func (b *Backend) Received() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return strings.Join(b.got, ",")
}
