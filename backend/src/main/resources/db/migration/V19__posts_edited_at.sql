-- 게시글을 작성자가 마지막으로 수정한 시각 (수정한 적 없으면 NULL)
-- updated_at은 조회수·좋아요가 바뀔 때도 DB가 갱신하므로 "수정됨" 표시에 쓸 수 없다
ALTER TABLE `posts` ADD COLUMN `edited_at` datetime(6) DEFAULT NULL AFTER `answered_at`;
