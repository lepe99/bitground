-- 금액·수량 컬럼 타입 정리
-- - 현금·자산 총액(원 단위 정수): INT는 약 21억 원에서 넘치므로 BIGINT
-- - 가격·수량: 부동소수점(FLOAT/DOUBLE)은 반올림 오차가 누적되므로 DECIMAL
--   가격은 1원 미만 코인이 있어 소수 8자리, 수량은 업비트 최소 단위에 맞춰 소수 10자리
ALTER TABLE `users`
  MODIFY `cash` BIGINT NOT NULL;

ALTER TABLE `orders`
  MODIFY `amount` DECIMAL(30,10) NOT NULL,
  MODIFY `reserve_price` DECIMAL(24,8) DEFAULT NULL,
  MODIFY `trade_price` DECIMAL(24,8) DEFAULT NULL;

ALTER TABLE `user_assets`
  MODIFY `amount` DECIMAL(30,10) NOT NULL,
  MODIFY `avg_price` DECIMAL(24,8) NOT NULL;

ALTER TABLE `user_rankings`
  MODIFY `total_value` BIGINT NOT NULL;

ALTER TABLE `user_daily_balances`
  MODIFY `cash_balance` BIGINT NOT NULL,
  MODIFY `coin_holdings_value` BIGINT NOT NULL,
  MODIFY `total_value` BIGINT NOT NULL;
