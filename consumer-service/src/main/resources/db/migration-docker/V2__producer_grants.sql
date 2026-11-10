-- Requires lab_producer from docker/mysql-init (local Docker only).
-- Skipped safely in Testcontainers when the user is missing (manual grant in prod docker).

REVOKE DELETE ON notification_lab.* FROM 'lab_producer'@'%';
GRANT SELECT ON notification_lab.notifications TO 'lab_producer'@'%';
GRANT SELECT ON notification_lab.notification_attempts TO 'lab_producer'@'%';
GRANT SELECT ON notification_lab.processed_messages TO 'lab_producer'@'%';
GRANT INSERT ON notification_lab.notifications TO 'lab_producer'@'%';
GRANT UPDATE (status, queued_at, updated_at, completed_at, last_error, version)
    ON notification_lab.notifications TO 'lab_producer'@'%';
