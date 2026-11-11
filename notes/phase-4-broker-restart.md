# Phase 4 — Broker restart with durable topology (T-4.6)

**Durable run (expected: no message loss)**

1. Stop consumer-service.
2. `POST` five EMAIL notifications via the API.
3. Confirm `email.queue` depth is 5 (Management UI).
4. `docker compose -f docker/docker-compose.yml restart rabbitmq`
5. Start consumer-service again.
6. All five should end `SENT`; queues return to empty.

**Optional contrast (destructive lab only)**

1. Delete `email.queue` and recreate it as **non-durable** (Management UI).
2. Repeat publish + restart — queue and messages are gone after broker restart.
3. Restore durable `email.queue` from app restart (consumer declares topology) or Compose workflow.
