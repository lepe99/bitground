-- 로컬 개발용 초기 데이터
SET NAMES utf8mb4; -- 클라이언트 문자셋을 맞추지 않으면 한글이 깨져서 저장됨
-- 스케줄러와 백엔드는 status = 'PENDING' 인 시즌을 현재 시즌으로 사용한다.
INSERT INTO seasons (name, start_at, end_at, status)
SELECT '시즌 1', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'PENDING'
WHERE NOT EXISTS (SELECT 1 FROM seasons WHERE status = 'PENDING');
