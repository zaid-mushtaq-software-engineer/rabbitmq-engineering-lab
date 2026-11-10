-- Schema and application users for notification_lab.
-- Table-level grants for lab_producer are applied in Flyway V1 (consumer-service) after tables exist.

CREATE DATABASE IF NOT EXISTS notification_lab
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Passwords are overridden in local dev via docker env; defaults match .env.example for first boot only.
CREATE USER IF NOT EXISTS 'lab_consumer'@'%' IDENTIFIED BY 'change_me_consumer';
CREATE USER IF NOT EXISTS 'lab_producer'@'%' IDENTIFIED BY 'change_me_producer';

GRANT ALL PRIVILEGES ON notification_lab.* TO 'lab_consumer'@'%';

-- Full column-level restrictions are applied in Flyway V1 (T-2.11). Until tables exist,
-- DML on the schema lets the producer connect for Actuator health (Phase 0).
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_lab.* TO 'lab_producer'@'%';

FLUSH PRIVILEGES;
