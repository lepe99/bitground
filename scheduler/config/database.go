package config

import (
	"context"
	"database/sql"
	"fmt"
	"github.com/go-sql-driver/mysql"
	"time"
)

// DBConfig db 구성 구조체
type DBConfig struct {
	Host     string // host:port
	User     string
	Password string
	DBName   string
	Charset  string
}

// ConnectDB 데이터베이스 연결
func ConnectDB(ctx context.Context, cfg DBConfig) (*sql.DB, error) {

	// MySQL DSN 구성 (비밀번호에 특수문자가 있어도 깨지지 않도록 드라이버로 조립)
	dsnCfg := mysql.NewConfig()
	dsnCfg.User = cfg.User
	dsnCfg.Passwd = cfg.Password
	dsnCfg.Net = "tcp"
	dsnCfg.Addr = cfg.Host
	dsnCfg.DBName = cfg.DBName
	dsnCfg.ParseTime = true
	dsnCfg.Params = map[string]string{"charset": cfg.Charset}

	db, err := sql.Open("mysql", dsnCfg.FormatDSN())
	if err != nil {
		return nil, fmt.Errorf("데이터베이스 연결 실패: %v", err)
	}

	// Context를 활용한 연결 테스트 (타임아웃: 10초)
	pingCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	// 연결 테스트
	if err := db.PingContext(pingCtx); err != nil {
		if err := db.Close(); err != nil {
			return nil, fmt.Errorf("데이터베이스 연결 종료 실패: %v", err)
		}
		return nil, fmt.Errorf("데이터베이스 연결 확인 실패: %v", err)
	}

	// 커넥션 풀 설정
	// 시간당 한 번 도는 배치라 연결이 많이 필요 없음 (RDS 프리티어 연결 수 보호)
	db.SetMaxOpenConns(5)
	db.SetMaxIdleConns(2)
	db.SetConnMaxLifetime(time.Hour)

	return db, nil
}
