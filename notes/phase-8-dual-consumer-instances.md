# Phase 8 — Two consumer JVMs (T-8.4)

Horizontal scaling: two stateless consumer instances share one broker and one MySQL database.

## Start instance A (default)

```bash
cd consumer-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Runs on **8081** (see `application.yml`).

## Start instance B (second port)

```bash
cd consumer-service
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8082
```

Both instances register consumers on `email.queue` and `sms.queue`.

## Load test

1. Publish **50** notifications (mix EMAIL/SMS) via producer `POST /api/v1/notifications`.
2. In Management UI → **Connections**: two connections from the consumer service.
3. **Queues → Consumers**: consumers split across both connections.
4. Split logs: grep `message.received` on each terminal — work should distribute (round-robin within prefetch limits).

## Kill one instance mid-run

1. Start a batch of 50 slow or normal messages.
2. `kill` or Ctrl+C **instance B** while queues still have Ready/Unacked messages.
3. Instance A continues; unacked work may be **redelivered** after channel close.
4. Assert all 50 end **`SENT`** (`GET /api/v1/notifications/{id}`) — Phase 7 idempotency prevents duplicate outcomes.

## Optional second instance snippet

Same broker/DB env vars as instance A; only `server.port` differs. No extra `docker-compose` service is required for the lab.