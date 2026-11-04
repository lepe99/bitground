-- 화면에 스플릿·시즌 종료까지 남은 시간을 보여주기 위한 정확한 시각. 새 시즌은 스케줄러가 일정(prod/dev)에 맞춰 넣는다.
-- 기존 시즌은 운영 일정(시작 7일 뒤 0시 스플릿, 종료일 다음 날 0시 정산)으로 채운다
ALTER TABLE seasons
    ADD COLUMN split_starts_at DATETIME NULL AFTER end_at,
    ADD COLUMN ends_at DATETIME NULL AFTER split_starts_at;

UPDATE seasons
SET split_starts_at = TIMESTAMP(DATE_ADD(start_at, INTERVAL 7 DAY)),
    ends_at = TIMESTAMP(DATE_ADD(end_at, INTERVAL 1 DAY));
