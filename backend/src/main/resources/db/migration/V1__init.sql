-- Bitground 초기 스키마 (JPA 엔티티 기준, Hibernate DDL로 생성 후 mysqldump --no-data)
-- 이미 적용된 마이그레이션 파일은 고치지 않는다. 스키마를 바꿀 때는 다음 버전 파일(V{n}__설명.sql)을 추가한다.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0; -- 테이블이 알파벳순이라 FK 대상보다 먼저 생성될 수 있음

CREATE TABLE `ai_advices` (
  `id` int NOT NULL AUTO_INCREMENT,
  `score` tinyint NOT NULL,
  `season_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `advice` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ai_advices_user_season` (`user_id`,`season_id`),
  KEY `ai_advices_seasons_id_fk` (`season_id`),
  CONSTRAINT `ai_advices_seasons_id_fk` FOREIGN KEY (`season_id`) REFERENCES `seasons` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ai_advices_users_userId_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_insights` (
  `date` date NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `score` tinyint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `insight` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `symbol` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ai_insights_date_symbol` (`date`,`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `coin_price_history` (
  `close_price` float NOT NULL,
  `coin_id` int NOT NULL,
  `date` date NOT NULL,
  `high_price` float NOT NULL,
  `hour` tinyint NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `low_price` float NOT NULL,
  `open_price` float NOT NULL,
  `volume` float NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_coin_price_history_coin_id_date_hour` (`coin_id`,`date`,`hour`),
  CONSTRAINT `fk_coin_price_history_coin_id` FOREIGN KEY (`coin_id`) REFERENCES `coins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `coins` (
  `change_rate` float DEFAULT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `is_caution` tinyint(1) NOT NULL DEFAULT '0',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `is_warning` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `trade_price_24h` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `korean_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `symbol` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_coins_symbol` (`symbol`),
  KEY `idx_is_deleted_symbol_id` (`is_deleted`,`symbol`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comments` (
  `dislikes` int NOT NULL DEFAULT '0',
  `id` int NOT NULL AUTO_INCREMENT,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `likes` int NOT NULL DEFAULT '0',
  `parent_id` int DEFAULT NULL,
  `post_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `content` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_comments_post_parent_created_at` (`post_id`,`parent_id`,`created_at`),
  KEY `comments_comments_id_fk` (`parent_id`),
  KEY `comments_users_id_fk` (`user_id`),
  CONSTRAINT `comments_comments_id_fk` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `comments_posts_id_fk` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `comments_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `favorite_coins` (
  `id` int NOT NULL AUTO_INCREMENT,
  `symbol_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_favorite_user_symbol` (`user_id`,`symbol_id`),
  KEY `favorite_coins_coins_id_fk` (`symbol_id`),
  CONSTRAINT `favorite_coins_coins_id_fk` FOREIGN KEY (`symbol_id`) REFERENCES `coins` (`id`) ON DELETE CASCADE,
  CONSTRAINT `favorite_coins_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `market_indices` (
  `alt_index` int NOT NULL,
  `date` date NOT NULL,
  `hour` tinyint NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `market_index` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_market_indices_date_hour` (`date`,`hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notices` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notices_title` (`title`),
  KEY `idx_notices_created_at_desc` (`created_at` DESC),
  KEY `notices_users_id_fk` (`user_id`),
  CONSTRAINT `notices_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `is_read` tinyint(1) DEFAULT '0',
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `message` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message_type` enum('NOTICE','ORDER_EXECUTION','SEASON_UPDATE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_created_at_desc` (`user_id`,`created_at` DESC),
  KEY `idx_notifications_user_is_read` (`user_id`,`is_read`),
  CONSTRAINT `notifications_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `orders` (
  `amount` double NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `reserve_price` float DEFAULT NULL,
  `season_id` int NOT NULL,
  `symbol_id` int NOT NULL,
  `trade_price` double DEFAULT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `order_type` enum('BUY','SELL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`id`),
  KEY `idx_orders_user_season` (`user_id`,`season_id`),
  KEY `idx_orders_season_status_user` (`season_id`,`status`,`user_id`),
  KEY `idx_orders_symbol_status_created_at_desc` (`symbol_id`,`status`,`created_at` DESC),
  CONSTRAINT `orders_coins_id_fk` FOREIGN KEY (`symbol_id`) REFERENCES `coins` (`id`) ON DELETE CASCADE,
  CONSTRAINT `orders_seasons_id_fk` FOREIGN KEY (`season_id`) REFERENCES `seasons` (`id`) ON DELETE CASCADE,
  CONSTRAINT `orders_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `posts` (
  `dislikes` int NOT NULL DEFAULT '0',
  `id` int NOT NULL AUTO_INCREMENT,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `likes` int NOT NULL DEFAULT '0',
  `tier` tinyint NOT NULL,
  `user_id` int NOT NULL,
  `views` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('CHAT','INFO','QUESTION') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_posts_deleted_category_created_at_desc` (`is_deleted`,`category`,`created_at` DESC),
  KEY `posts_users_id_fk` (`user_id`),
  CONSTRAINT `posts_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reactions` (
  `liked` bit(1) NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_id` bigint NOT NULL,
  `target_type` enum('COMMENT','POST') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7xdg8xikkaiqtsbmt95en8kbd` (`user_id`,`target_type`,`target_id`,`liked`),
  CONSTRAINT `FKqmewaibcp5bxtlqxc2cawhuln` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `seasons` (
  `end_at` date DEFAULT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `reward_calculated` tinyint(1) DEFAULT '0',
  `start_at` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('PENDING','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_assets` (
  `amount` double NOT NULL,
  `avg_price` double NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `symbol_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_assets_user_symbol` (`user_id`,`symbol_id`),
  KEY `user_assets_coins_id_fk` (`symbol_id`),
  CONSTRAINT `user_assets_coins_id_fk` FOREIGN KEY (`symbol_id`) REFERENCES `coins` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_assets_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_daily_balances` (
  `cash_balance` int NOT NULL,
  `coin_holdings_value` int NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `season_id` int NOT NULL,
  `snapshot_date` date NOT NULL,
  `total_value` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_daily_balances_user_id_season_id_snapshot_date` (`user_id`,`season_id`,`snapshot_date`),
  KEY `fk_user_daily_balances_season_id` (`season_id`),
  CONSTRAINT `fk_user_daily_balances_season_id` FOREIGN KEY (`season_id`) REFERENCES `seasons` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_daily_balances_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_rankings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `ranks` int NOT NULL,
  `season_id` int NOT NULL,
  `tier` tinyint NOT NULL DEFAULT '0',
  `total_value` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_rankings_user_symbol` (`user_id`,`season_id`),
  KEY `idx_user_rankings_season_ranks` (`season_id`,`ranks`),
  KEY `idx_user_rankings_user_season` (`user_id`,`season_id`),
  KEY `idx_user_rankings_user_tier_desc` (`user_id`,`tier` DESC),
  CONSTRAINT `users_rankings_seasons_id_fk` FOREIGN KEY (`season_id`) REFERENCES `seasons` (`id`) ON DELETE CASCADE,
  CONSTRAINT `users_rankings_users_id_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `cash` int NOT NULL,
  `id` int NOT NULL AUTO_INCREMENT,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `tier` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ROLE_ADMIN','ROLE_USER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ROLE_USER',
  PRIMARY KEY (`id`),
  KEY `idx_provider_provider_id` (`provider`,`provider_id`),
  KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
