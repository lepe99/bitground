// Package testsupport DB를 붙여 스케줄러 작업을 실행해 보는 테스트용 도구.
// MySQL 컨테이너(백엔드 Flyway 마이그레이션으로 만든 실제 스키마)와 외부 API 대역을 제공한다.
// 테스트에서만 import 한다 (Docker 필요).
package testsupport

import (
	"Bitground-go/config"
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"time"

	"github.com/go-sql-driver/mysql"
	"github.com/testcontainers/testcontainers-go"
	tcmysql "github.com/testcontainers/testcontainers-go/modules/mysql"
)

// KST 서비스 기준 시간대 (스케줄러 설정과 같은 값)
var KST = mustLoad("Asia/Seoul")

func mustLoad(name string) *time.Location {
	loc, err := time.LoadLocation(name)
	if err != nil {
		panic(err)
	}
	return loc
}

// MySQL 테스트용 MySQL 컨테이너 하나 (패키지 테스트 전체가 같이 쓰고, 테스트마다 Reset으로 비운다)
type MySQL struct {
	Config    config.DBConfig
	DB        *sql.DB
	container *tcmysql.MySQLContainer
}

// StartMySQL 운영과 같은 MySQL 8.0을 띄우고 백엔드의 Flyway 마이그레이션을 순서대로 적용한다
func StartMySQL(ctx context.Context) (*MySQL, error) {
	c, err := tcmysql.Run(ctx, "mysql:8.0",
		tcmysql.WithDatabase("bitground"),
		tcmysql.WithUsername("bitground"),
		tcmysql.WithPassword("bitground"),
		testcontainers.WithEnv(map[string]string{"TZ": "Asia/Seoul"}),
		testcontainers.WithCmdArgs("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
			"--default-time-zone=+09:00"),
	)
	if err != nil {
		return nil, fmt.Errorf("MySQL 컨테이너 시작 실패: %w", err)
	}
	host, err := c.Host(ctx)
	if err != nil {
		return nil, err
	}
	port, err := c.MappedPort(ctx, "3306/tcp")
	if err != nil {
		return nil, err
	}
	cfg := config.DBConfig{Host: fmt.Sprintf("%s:%s", host, port.Port()), User: "bitground", Password: "bitground",
		DBName: "bitground", Charset: "utf8mb4"}

	if err := migrate(ctx, cfg); err != nil {
		_ = c.Terminate(ctx)
		return nil, err
	}
	db, err := config.ConnectDB(ctx, cfg) // 운영 코드와 같은 연결 설정
	if err != nil {
		_ = c.Terminate(ctx)
		return nil, err
	}
	return &MySQL{Config: cfg, DB: db, container: c}, nil
}

// Close 연결을 닫고 컨테이너를 지운다
func (m *MySQL) Close(ctx context.Context) {
	_ = m.DB.Close()
	_ = m.container.Terminate(ctx)
}

// Reset 모든 테이블을 비운다 (테스트마다 처음 상태에서 시작)
func (m *MySQL) Reset(ctx context.Context) error {
	rows, err := m.DB.QueryContext(ctx, `SELECT table_name FROM information_schema.tables
		WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'`)
	if err != nil {
		return err
	}
	var tables []string
	for rows.Next() {
		var t string
		if err := rows.Scan(&t); err != nil {
			rows.Close()
			return err
		}
		tables = append(tables, t)
	}
	rows.Close()

	conn, err := m.DB.Conn(ctx) // 외래 키 검사 끄기는 연결 단위라 같은 연결에서 실행
	if err != nil {
		return err
	}
	defer conn.Close()
	if _, err := conn.ExecContext(ctx, "SET FOREIGN_KEY_CHECKS = 0"); err != nil {
		return err
	}
	for _, t := range tables {
		if _, err := conn.ExecContext(ctx, "TRUNCATE TABLE `"+t+"`"); err != nil {
			return fmt.Errorf("%s 비우기 실패: %w", t, err)
		}
	}
	_, err = conn.ExecContext(ctx, "SET FOREIGN_KEY_CHECKS = 1")
	return err
}

var migrationName = regexp.MustCompile(`^V(\d+)__.+\.sql$`)

// migrate 백엔드 마이그레이션 파일(V1__, V2__, …)을 버전 순서대로 실행한다
func migrate(ctx context.Context, cfg config.DBConfig) error {
	dir := filepath.Join(repoRoot(), "backend", "src", "main", "resources", "db", "migration")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return fmt.Errorf("마이그레이션 폴더 읽기 실패: %w", err)
	}
	type file struct {
		version int
		path    string
	}
	var files []file
	for _, e := range entries {
		if m := migrationName.FindStringSubmatch(e.Name()); m != nil {
			v, _ := strconv.Atoi(m[1])
			files = append(files, file{v, filepath.Join(dir, e.Name())})
		}
	}
	sort.Slice(files, func(i, j int) bool { return files[i].version < files[j].version })

	dsn := mysql.NewConfig()
	dsn.User, dsn.Passwd, dsn.Net, dsn.Addr, dsn.DBName = cfg.User, cfg.Password, "tcp", cfg.Host, cfg.DBName
	dsn.MultiStatements = true
	dsn.Params = map[string]string{"charset": "utf8mb4"}
	db, err := sql.Open("mysql", dsn.FormatDSN())
	if err != nil {
		return err
	}
	defer db.Close()
	for _, f := range files {
		body, err := os.ReadFile(f.path)
		if err != nil {
			return err
		}
		if _, err := db.ExecContext(ctx, string(body)); err != nil {
			return fmt.Errorf("%s 적용 실패: %w", filepath.Base(f.path), err)
		}
	}
	return nil
}

// repoRoot 저장소 최상위 (이 파일: scheduler/testsupport/mysql.go)
func repoRoot() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(file), "..", "..")
}
