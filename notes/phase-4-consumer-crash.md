# Phase 4 — Kill consumer mid-processing (T-4.4)

1. Start consumer and producer against Docker Compose.
2. `POST /api/v1/notifications` with `recipient: slow@example.com` (15s simulated latency).
3. In RabbitMQ UI, open `email.queue` — **Unacked** should be 1 during processing.
4. Within 15s, `kill -9` the consumer JVM (or `pkill -9 -f consumer-service`).
5. Restart consumer; message is redelivered (`redelivered=true`).
6. `GET /api/v1/notifications/{id}` — status `SENT`, attempt row shows `redelivered: true`.

Automated cover: [`ManualAckRegressionTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/messaging/ManualAckRegressionTest.java).
