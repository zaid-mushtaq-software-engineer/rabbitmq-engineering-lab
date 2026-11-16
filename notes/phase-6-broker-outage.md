# Phase 6 — Broker outage experiment (T-6.6)

1. Start infrastructure: `docker compose up -d` in `docker/`.
2. Start producer-service; submit `POST /api/v1/notifications` — expect `202` / `QUEUED`.
3. Stop RabbitMQ: `docker compose stop rabbitmq`.
4. Submit again — expect `503` / `MESSAGE_PUBLISH_FAILED`, `Retry-After: 5`, and a row with status `PUBLISH_FAILED`.
5. Start RabbitMQ: `docker compose start rabbitmq` (do **not** restart the producer app).
6. Submit again — expect `202`; watch **Connections** in the Management UI for automatic recovery.