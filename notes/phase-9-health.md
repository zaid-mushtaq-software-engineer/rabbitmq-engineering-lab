# Phase 9 — Actuator health (T-9.3)

Both **producer-service** (`8080`) and **consumer-service** (`8081`) expose `/actuator/health` with Spring Boot auto-configured contributors for **RabbitMQ** (`components.rabbit`) and **MySQL** (`components.db` / `db`).

## Checklist (docker compose on host)

1. `curl -s http://localhost:8080/actuator/health | jq` → status `UP`; `rabbit` and `db` present and `UP`.
2. `curl -s http://localhost:8081/actuator/health | jq` → same for consumer.
3. `docker compose -f docker/docker-compose.yml stop rabbitmq` → both apps' health → `DOWN` (rabbit component).
4. `docker compose -f docker/docker-compose.yml start rabbitmq` → wait for healthy → apps recover to `UP` without restart.
5. `docker compose -f docker/docker-compose.yml stop mysql` → both apps' health → `DOWN` (db component).
6. `docker compose -f docker/docker-compose.yml start mysql` → apps recover to `UP`.

**Acceptance:** Stopping either dependency flips aggregate health to `DOWN`; restart restores `UP`.