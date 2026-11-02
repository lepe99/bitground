-- Pinpoint v4.0.0: feature-flag/src/main/resources/sql/CreateTableStatement-mysql.sql
CREATE TABLE feature_flag (
    service_name VARCHAR(127) NOT NULL,
    application_name VARCHAR(127) NOT NULL,
    feature_name VARCHAR(64) NOT NULL,
    rule_type ENUM('ENABLED','DISABLED') NOT NULL,
    PRIMARY KEY (service_name, application_name, feature_name)
);