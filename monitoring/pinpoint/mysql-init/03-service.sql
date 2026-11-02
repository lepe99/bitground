-- Pinpoint v4.0.0: service-module/src/main/resources/sql/Service-Schema.sql
CREATE TABLE `service` (
  `uid` int NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`uid`),
  UNIQUE KEY `name_idx` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
