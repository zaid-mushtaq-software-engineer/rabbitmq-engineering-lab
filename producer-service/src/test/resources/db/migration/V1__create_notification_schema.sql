CREATE TABLE notifications (
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    message_id      CHAR(36)     NOT NULL,
    channel         VARCHAR(10)  NOT NULL,
    recipient       VARCHAR(254) NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    correlation_id  VARCHAR(64)  NOT NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    attempt_count   INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME(3)  NOT NULL,
    queued_at       DATETIME(3)  NULL,
    completed_at    DATETIME(3)  NULL,
    updated_at      DATETIME(3)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_notifications_message_id UNIQUE (message_id),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_notifications_status CHECK (status IN (
        'PENDING_PUBLISH', 'QUEUED', 'PUBLISH_FAILED', 'PROCESSING',
        'RETRY_SCHEDULED', 'SENT', 'DEAD_LETTERED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notifications_status_created ON notifications (status, created_at);
CREATE INDEX idx_notifications_channel_created ON notifications (channel, created_at);
CREATE INDEX idx_notifications_created ON notifications (created_at);

CREATE TABLE notification_attempts (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notification_id  CHAR(36)     NOT NULL,
    message_id       CHAR(36)     NOT NULL,
    attempt_number   INT          NOT NULL,
    consumer_name    VARCHAR(30)  NOT NULL,
    outcome          VARCHAR(25)  NOT NULL,
    error_code       VARCHAR(50)  NULL,
    error_message    VARCHAR(500) NULL,
    redelivered      BOOLEAN      NOT NULL,
    started_at       DATETIME(3)  NOT NULL,
    finished_at      DATETIME(3)  NOT NULL,
    CONSTRAINT uk_notification_attempt UNIQUE (notification_id, attempt_number),
    CONSTRAINT fk_attempt_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE RESTRICT,
    CONSTRAINT chk_attempt_outcome CHECK (outcome IN ('SUCCESS', 'TEMPORARY_FAILURE', 'PERMANENT_FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processed_messages (
    message_id       CHAR(36)     NOT NULL PRIMARY KEY,
    notification_id  CHAR(36)     NOT NULL,
    consumer_name    VARCHAR(30)  NOT NULL,
    outcome          VARCHAR(20)  NOT NULL,
    processed_at     DATETIME(3)  NOT NULL,
    CONSTRAINT fk_processed_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE RESTRICT,
    CONSTRAINT chk_processed_outcome CHECK (outcome IN ('SENT', 'DEAD_LETTERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_processed_notification_id ON processed_messages (notification_id);
CREATE INDEX idx_processed_at ON processed_messages (processed_at);

