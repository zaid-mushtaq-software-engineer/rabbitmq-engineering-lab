# Phase 7 — Duplicate delivery experiment (T-7.3)

## Setup

```bash
docker compose up -d
# Run producer and consumer (local profile).
```

## Consumer running — publish the same body twice

1. In RabbitMQ Management (**Queues →** pick `email.queue` → **Publish message**), paste identical JSON twice (same `messageId`, `notificationId`, body).
2. `GET http://localhost:8080/api/v1/notifications/{notificationId}` — status `SENT` (or `DEAD_LETTERED` if the recipient is a failure fixture).
3. In MySQL:

```sql
SELECT * FROM notifications WHERE id = '<notificationId>';
SELECT * FROM notification_attempts WHERE notification_id = '<notificationId>';
SELECT * FROM processed_messages WHERE message_id = '<messageId>';
```

**Expected:** one notification row, **one** attempt row, **one** `processed_messages` row. Consumer logs `duplicate.ignored` for the second delivery.

## Consumer stopped between publishes

1. Stop the consumer (`docker compose stop consumer` or kill the JVM).
2. Publish the same JSON twice from the UI (messages accumulate in `email.queue`).
3. Start the consumer.
4. Re-run the SQL above.

**Expected:** still one outcome set — the duplicate is absorbed when the second delivery runs.

## Idempotency key

Duplicate detection uses **`messageId` only**, not `notificationId`. A second message with a **new** `messageId` but the same `notificationId` is treated as a **new** delivery (two outcomes possible). That matches at-least-once resubmit ambiguity (`OQ-05` / producer confirm timeout).

## DLQ duplicate (T-7.6)

For `perm-fail@example.com`, publishing the same `messageId` twice yields one `DEAD_LETTERED` row and **two** messages in `notification.dlq` (second copy is nack’d without reprocessing). Replay from the DLQ without clearing `processed_messages` would hit the duplicate path (`OQ-01`).