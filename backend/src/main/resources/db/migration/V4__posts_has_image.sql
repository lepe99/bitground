-- 게시글 목록이 본문(mediumtext) 전체를 읽지 않도록 이미지 포함 여부를 컬럼으로 둔다
ALTER TABLE `posts` ADD COLUMN `has_image` tinyint(1) NOT NULL DEFAULT 0 AFTER `content`;
UPDATE `posts` SET `has_image` = (`content` LIKE '%<img%');
