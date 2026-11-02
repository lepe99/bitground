-- 스케줄러(Go) 정산 실행 기록: 같은 기간의 시즌 정산·스플릿이 두 번 실행되지 않도록 막는다
-- (Lambda 재시도·중복 호출 대비). notified_at은 백엔드 알림까지 끝났는지 표시
-- Flyway 도입 전 수동으로 만든 DB가 있어 IF NOT EXISTS
CREATE TABLE IF NOT EXISTS `scheduler_runs` (
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `notified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`kind`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
