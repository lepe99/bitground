-- 첫 시즌 이름을 스케줄러가 붙이는 형식("26년 1시즌")에 맞춘다.
-- V3이 고정 이름 '시즌 1'로 만들었고, 다음 시즌부터는 스케줄러가 "YY년 N시즌"으로 이어 붙인다.
-- 이미 새 형식의 시즌이 있으면(이름이 겹칠 수 있으므로) 건드리지 않는다.
SET NAMES utf8mb4; -- 클라이언트 문자셋을 맞추지 않으면 한글이 깨져서 저장됨
UPDATE seasons
SET name = CONCAT(DATE_FORMAT(start_at, '%y'), '년 1시즌')
WHERE name = '시즌 1'
  AND NOT EXISTS (SELECT 1 FROM (SELECT name FROM seasons WHERE name REGEXP '^[0-9]{2}년 [0-9]+시즌$') AS named);
