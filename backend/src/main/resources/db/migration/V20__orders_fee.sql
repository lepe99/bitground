-- 체결 때 실제로 낸 거래 수수료 (원). 수수료 도입 전 주문·시스템 청산은 0
ALTER TABLE `orders` ADD COLUMN `fee` bigint NOT NULL DEFAULT 0 AFTER `trade_price`;
