-- 처음 가입한 사용자가 프로필(사진·닉네임·이메일)을 확인하는 첫 화면을 마쳤는지.
-- 이미 가입해 쓰던 사용자는 마친 것으로 둔다 (새 사용자는 앱에서 0으로 만든다)
ALTER TABLE `users` ADD COLUMN `is_onboarded` tinyint(1) NOT NULL DEFAULT 1 AFTER `is_deleted`;
