package main

import (
	"Bitground-go/config"
	"Bitground-go/testsupport"
	"context"
	"fmt"
	"os"
	"reflect"
	"testing"
	"time"
)

// 실제 MySQL(백엔드 마이그레이션 스키마)에 한 시간분 실행(run)을 돌려 결과 테이블을 확인한다.
// 외부 API(업비트·CoinGecko)와 백엔드는 대역을 쓴다. Docker 필요.

var testDB *testsupport.MySQL

func TestMain(m *testing.M) {
	ctx := context.Background()
	var err error
	testDB, err = testsupport.StartMySQL(ctx)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	code := m.Run()
	testDB.Close(ctx)
	os.Exit(code)
}

const (
	btcPrice = 100_000_000.0
	ethPrice = 5_000_000.0
	xrpPrice = 1_000.0
)

type env struct {
	f       testsupport.Fixtures
	market  *testsupport.Market
	backend *testsupport.Backend
	cfg     config.Config
	btc     int
	eth     int
	xrp     int
}

func setup(t *testing.T) *env {
	t.Helper()
	ctx := context.Background()
	if err := testDB.Reset(ctx); err != nil {
		t.Fatal(err)
	}
	time.Local = testsupport.KST // handleAt과 같은 기준 시간대
	e := &env{
		f:       testsupport.Fixtures{T: t, DB: testDB.DB, Ctx: ctx},
		market:  testsupport.NewMarket(),
		backend: testsupport.NewBackend(t, "scheduler-key"),
	}
	e.market.List("KRW-BTC", "비트코인", btcPrice).List("KRW-ETH", "이더리움", ethPrice).List("KRW-XRP", "리플", xrpPrice).
		MarketCaps(map[string]int64{"btc": 2_000_000_000_000, "eth": 400_000_000_000, "xrp": 150_000_000_000,
			"sol": 90_000_000_000, "doge": 30_000_000_000})
	e.market.Install(t)
	e.cfg = config.Config{DB: testDB.Config, Location: testsupport.KST, BackendURL: e.backend.URL, SchedulerKey: "scheduler-key"}
	e.btc = e.f.Coin("KRW-BTC", "비트코인")
	e.eth = e.f.Coin("KRW-ETH", "이더리움")
	e.xrp = e.f.Coin("KRW-XRP", "리플")
	return e
}

func (e *env) run(t *testing.T, at time.Time) error {
	t.Helper()
	return run(context.Background(), testDB.DB, e.cfg, at, &jobRunner{})
}

func kst(s string) time.Time {
	t, err := time.ParseInLocation(time.DateTime, s, testsupport.KST)
	if err != nil {
		panic(err)
	}
	return t
}

// rankingFixture 참가자 7명(총자산이 모두 다름), 거래하지 않은 사용자, 탈퇴한 사용자
func (e *env) rankingFixture(seasonID int) (users []int) {
	add := func(cash int64, holdings ...func(uid int)) int {
		uid := e.f.User(cash)
		e.f.Trade(uid, e.eth, seasonID, "BUY", 0.1, ethPrice) // 체결 주문이 있어야 랭킹 참가
		for _, h := range holdings {
			h(uid)
		}
		users = append(users, uid)
		return uid
	}
	add(5_000_000, func(uid int) { e.f.Asset(uid, e.btc, 0.1, 90_000_000) }) // 15,000,000
	add(12_000_000)
	add(11_000_000)
	add(9_000_000, func(uid int) { e.f.Asset(uid, e.xrp, 1000, 900) }) // 10,000,000
	add(9_500_000)
	add(9_000_000)
	add(8_000_000)

	idle := e.f.User(20_000_000) // 거래하지 않음: 예약 주문만 있음
	e.f.Reserve(idle, e.btc, seasonID, "BUY", 0.001, 90_000_000)
	gone := e.f.DeletedUser(30_000_000)
	e.f.Trade(gone, e.eth, seasonID, "BUY", 0.1, ethPrice)
	return users
}

func TestHourlyRun(t *testing.T) {
	e := setup(t)
	now := kst("2026-11-10 14:00:00")
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), kst("2026-11-08 00:00:00"), kst("2026-11-16 00:00:00"))
	// 스플릿은 이미 지급됨 (같은 시각 실행 기록)
	if _, err := testDB.DB.Exec(`INSERT INTO scheduler_runs (kind, period, notified_at) VALUES ('split', '2026-11-08T00', NOW())`); err != nil {
		t.Fatal(err)
	}
	users := e.rankingFixture(season)
	e.market.List("KRW-SOL", "솔라나", 200_000) // 새로 상장

	for i := 0; i < 2; i++ { // 같은 시각에 다시 실행돼도 결과가 같다 (Lambda 재시도)
		if err := e.run(t, now); err != nil {
			t.Fatalf("%d번째 실행 실패: %v", i+1, err)
		}
	}

	// 랭킹: 총자산 = 현금 + 보유 수량 × 현재가, 1위 7 · 2~3위 6 · 4위 이하는 상위 비율 (4명 중 1명 25% → 4, 2명 50% → 2, 나머지 1)
	got := e.f.Rows(`SELECT user_id, ranks, tier, total_value FROM user_rankings WHERE season_id = ? ORDER BY ranks`, season)
	want := [][]string{
		{itoa(users[0]), "1", "7", "15000000"},
		{itoa(users[1]), "2", "6", "12000000"},
		{itoa(users[2]), "3", "6", "11000000"},
		{itoa(users[3]), "4", "4", "10000000"},
		{itoa(users[4]), "5", "2", "9500000"},
		{itoa(users[5]), "6", "1", "9000000"},
		{itoa(users[6]), "7", "1", "8000000"},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("랭킹\n got %v\nwant %v", got, want)
	}

	// 시간별 시세 기록 (업비트에 있는 코인만, 같은 시각은 한 행). 새로 상장된 코인은 목록에 들어간 다음 실행부터 기록된다
	if got := e.f.Rows(`SELECT c.symbol, CAST(h.close_price AS DECIMAL(14,0)) FROM coin_price_history h JOIN coins c ON c.id = h.coin_id
		WHERE h.date = '2026-11-10' AND h.hour = 14 ORDER BY c.symbol`); !reflect.DeepEqual(got,
		[][]string{{"KRW-BTC", "100000000"}, {"KRW-ETH", "5000000"}, {"KRW-SOL", "200000"}, {"KRW-XRP", "1000"}}) {
		t.Errorf("시세 기록 %v", got)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM market_indices WHERE date = '2026-11-10' AND hour = 14`); n != 1 {
		t.Errorf("시장 지수 행 %d개", n)
	}
	// 코인 목록은 매시 갱신한다 (화면의 종목 목록·거래대금 상위가 이 표를 쓴다)
	if got := e.f.Rows(`SELECT korean_name, trade_price_24h FROM coins WHERE symbol = 'KRW-SOL'`); !reflect.DeepEqual(got, [][]string{{"솔라나", "200000000"}}) {
		t.Errorf("새로 상장된 코인 %v", got)
	}
	// 0시가 아니면 일일 스냅샷은 남기지 않는다
	if n := e.f.Int(`SELECT COUNT(*) FROM user_daily_balances`); n != 0 {
		t.Errorf("일일 스냅샷 %d행 (0시에만)", n)
	}
	if got := e.backend.Received(); got != "" {
		t.Errorf("정산·스플릿이 없는데 백엔드 알림 %q", got)
	}
	// 랭킹을 다시 계산할 때마다 백엔드 랭킹 캐시를 비우라고 알린다 (두 번 실행)
	if n := e.backend.RankingRefreshes(); n != 2 {
		t.Errorf("랭킹 갱신 알림 %d번", n)
	}
}

func TestMidnightRun(t *testing.T) {
	e := setup(t)
	now := kst("2026-11-11 00:00:00")
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), time.Time{}, kst("2026-11-16 00:00:00"))
	users := e.rankingFixture(season)
	e.market.List("KRW-SOL", "솔라나", 200_000)                  // 새로 상장
	old := e.f.Notification(users[0], now.AddDate(0, 0, -91)) // 보관 기간(90일)이 지난 알림
	recent := e.f.Notification(users[0], now.AddDate(0, 0, -10))

	if err := e.run(t, now); err != nil {
		t.Fatal(err)
	}

	// 코인 목록: 새 코인 추가, 기존 코인 24시간 거래대금 갱신
	if n := e.f.Int(`SELECT COUNT(*) FROM coins WHERE symbol = 'KRW-SOL' AND korean_name = '솔라나' AND is_deleted = 0`); n != 1 {
		t.Errorf("새 상장 코인이 추가되지 않음")
	}
	if v := e.f.Int(`SELECT trade_price_24h FROM coins WHERE id = ?`, e.btc); v != int64(btcPrice*1000) {
		t.Errorf("BTC 24시간 거래대금 %d", v)
	}
	// 일일 자산 스냅샷: 참가자 7명, 현금과 코인 평가액을 나눠 기록
	if n := e.f.Int(`SELECT COUNT(*) FROM user_daily_balances WHERE season_id = ? AND snapshot_date = '2026-11-11'`, season); n != 7 {
		t.Errorf("일일 스냅샷 %d행", n)
	}
	if got := e.f.Rows(`SELECT cash_balance, coin_holdings_value, total_value FROM user_daily_balances WHERE user_id = ?`, users[0]); !reflect.DeepEqual(got,
		[][]string{{"5000000", "10000000", "15000000"}}) {
		t.Errorf("1위 스냅샷 %v", got)
	}
	// 오래된 알림만 지운다
	if e.f.Int(`SELECT COUNT(*) FROM notifications WHERE id = ?`, old) != 0 || e.f.Int(`SELECT COUNT(*) FROM notifications WHERE id = ?`, recent) != 1 {
		t.Errorf("알림 정리: 오래된 알림은 지우고 최근 알림은 남겨야 함")
	}
}

func TestDelistingLiquidatesHoldings(t *testing.T) {
	e := setup(t)
	now := kst("2026-11-10 14:00:00")
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), time.Time{}, kst("2026-11-16 00:00:00"))
	doge := e.f.Coin("KRW-DOGE", "도지코인")
	e.f.PriceHistory(doge, kst("2026-11-10 12:00:00"), 150)
	e.f.PriceHistory(doge, kst("2026-11-10 13:00:00"), 200) // 마지막 시세로 정리
	holder := e.f.User(1_000_000)
	e.f.Trade(holder, doge, season, "BUY", 100, 150)
	e.f.Asset(holder, doge, 100, 150)
	e.f.Reserve(holder, doge, season, "SELL", 50, 300)
	// 업비트 목록에 DOGE가 없음 (거래지원 종료)

	if err := e.run(t, now); err != nil {
		t.Fatal(err)
	}

	if v := e.f.Int(`SELECT is_deleted FROM coins WHERE id = ?`, doge); v != 1 {
		t.Errorf("상장 폐지 표시 안 됨")
	}
	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, holder); v != 1_000_000+20_000 {
		t.Errorf("정리 대금 입금 후 현금 %d (100개 × 200원 = 20,000원 더해져야 함)", v)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM user_assets WHERE user_id = ?`, holder); n != 0 {
		t.Errorf("상장 폐지 코인 보유분이 남음")
	}
	if got := e.f.Rows(`SELECT CAST(amount AS DECIMAL(10,0)), CAST(trade_price AS DECIMAL(10,0)) FROM orders
		WHERE user_id = ? AND order_type = 'SELL' AND status = 'COMPLETED'`, holder); !reflect.DeepEqual(got, [][]string{{"100", "200"}}) {
		t.Errorf("정리 매도 기록 %v", got)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = 'PENDING'`, holder); n != 0 {
		t.Errorf("상장 폐지 코인 예약 주문이 남음")
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM notifications WHERE user_id = ? AND message_type = 'ORDER_EXECUTION'`, holder); n != 2 {
		t.Errorf("알림 %d건 (정리 매도 1 + 예약 취소 1)", n)
	}
}

func TestSplitPaysOnce(t *testing.T) {
	e := setup(t)
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), kst("2026-11-08 00:00:00"), kst("2026-11-16 00:00:00"))
	users := e.rankingFixture(season)

	for _, at := range []string{"2026-11-08 00:00:00", "2026-11-08 01:00:00"} { // 다음 정각에 다시 돌아도 한 번만
		if err := e.run(t, kst(at)); err != nil {
			t.Fatalf("%s 실행 실패: %v", at, err)
		}
	}

	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, users[1]); v != 12_000_000+10_000_000 {
		t.Errorf("스플릿 지급 후 현금 %d", v)
	}
	if v := e.f.Int(`SELECT cash FROM users WHERE is_deleted = 1`); v != 30_000_000 {
		t.Errorf("탈퇴한 사용자에게도 지급됨: %d", v)
	}
	if got := e.backend.Received(); got != "split" {
		t.Errorf("백엔드 알림 %q (split 한 번)", got)
	}
}

func TestSeasonSettlement(t *testing.T) {
	e := setup(t)
	endsAt := kst("2026-11-16 00:00:00")
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), kst("2026-11-08 00:00:00"), endsAt)
	if _, err := testDB.DB.Exec(`INSERT INTO scheduler_runs (kind, period, notified_at) VALUES ('split', '2026-11-08T00', NOW())`); err != nil {
		t.Fatal(err)
	}
	users := e.rankingFixture(season)

	for _, at := range []string{"2026-11-16 00:00:00", "2026-11-16 01:00:00"} { // 다음 정각에 다시 돌아도 한 번만 정산
		if err := e.run(t, kst(at)); err != nil {
			t.Fatalf("%s 실행 실패: %v", at, err)
		}
	}

	if got := e.f.Rows(`SELECT status, reward_calculated FROM seasons WHERE id = ?`, season); !reflect.DeepEqual(got, [][]string{{"COMPLETED", "1"}}) {
		t.Errorf("이전 시즌 %v", got)
	}
	// 새 시즌: 종료 시각부터 시작, 7일 이상 남은 첫 경계(12월 1일)에 종료, 7일 뒤 스플릿
	if got := e.f.Rows(`SELECT name, DATE_FORMAT(start_at, '%Y-%m-%d'), DATE_FORMAT(split_starts_at, '%Y-%m-%d %H:%i'), DATE_FORMAT(ends_at, '%Y-%m-%d %H:%i')
		FROM seasons WHERE status = 'PENDING'`); !reflect.DeepEqual(got,
		[][]string{{"26년 4시즌", "2026-11-16", "2026-11-23 00:00", "2026-12-01 00:00"}}) {
		t.Errorf("새 시즌 %v", got)
	}
	// 최종 랭킹의 티어를 사용자에게, 거래하지 않은 사용자는 0
	if got := e.f.Rows(`SELECT tier FROM users WHERE is_deleted = 0 ORDER BY id`); !reflect.DeepEqual(got,
		[][]string{{"7"}, {"6"}, {"6"}, {"4"}, {"2"}, {"1"}, {"1"}, {"0"}}) {
		t.Errorf("티어 반영 %v", got)
	}
	// 보유 코인은 현재가로 청산 기록을 남기고 지운다
	if got := e.f.Rows(`SELECT user_id, CAST(trade_price AS DECIMAL(12,0)) FROM orders
		WHERE season_id = ? AND order_type = 'SELL' ORDER BY user_id`, season); !reflect.DeepEqual(got,
		[][]string{{itoa(users[0]), "100000000"}, {itoa(users[3]), "1000"}}) {
		t.Errorf("청산 기록 %v", got)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM user_assets`); n != 0 {
		t.Errorf("보유 자산 %d개 남음", n)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM orders WHERE season_id = ? AND status = 'PENDING'`, season); n != 0 {
		t.Errorf("이전 시즌 예약 주문 %d건 남음", n)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM users WHERE is_deleted = 0 AND cash <> 10000000`); n != 0 {
		t.Errorf("현금이 시작 자금으로 초기화되지 않은 사용자 %d명", n)
	}
	if got := e.backend.Received(); got != "season" {
		t.Errorf("백엔드 알림 %q (season 한 번)", got)
	}
}

func TestNotificationRetriedAfterBackendRecovers(t *testing.T) {
	e := setup(t)
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), kst("2026-11-08 00:00:00"), kst("2026-11-16 00:00:00"))
	users := e.rankingFixture(season)

	e.backend.SetDown(true)
	if err := e.run(t, kst("2026-11-08 00:00:00")); err == nil {
		t.Fatal("백엔드 알림이 실패했는데 실행이 성공으로 끝남 (재시도 대상이어야 함)")
	}
	// 지급은 커밋됐고 알림만 남았다
	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, users[1]); v != 22_000_000 {
		t.Errorf("스플릿 지급 후 현금 %d", v)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM scheduler_runs WHERE kind = 'split' AND notified_at IS NULL`); n != 1 {
		t.Errorf("보내지 못한 알림 기록 %d개", n)
	}

	e.backend.SetDown(false)
	if err := e.run(t, kst("2026-11-08 01:00:00")); err != nil {
		t.Fatal(err)
	}
	if got := e.backend.Received(); got != "split" {
		t.Errorf("백엔드 알림 %q (복구 후 split 한 번)", got)
	}
	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, users[1]); v != 22_000_000 {
		t.Errorf("재시도에서 다시 지급됨: %d", v)
	}
}

func itoa(n int) string { return fmt.Sprint(n) }
