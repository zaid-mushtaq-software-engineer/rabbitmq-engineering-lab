# Phase 10 — Failure scenario evidence matrix (T-10.2)

Canonical descriptions: [implementation.md §12.2](../documentation/implementation.md). Guard file: `consumer-service/src/test/resources/failure-scenario-ids.txt` (nine IDs, checked by `FailureScenarioCoverageTest`).

**Self-review:** Matrix reviewed twice on 2026-11-17 against `mvn clean verify` on producer + consumer modules.

| # | Scenario | Expected final state | Evidence type | How to reproduce | Primary evidence |
|---|----------|----------------------|---------------|------------------|------------------|
| 1 | Consumer offline | Queue Ready grows; after consumer starts, all `SENT` | automated | `mvn -pl consumer-service test -Dtest=ConsumerOfflineIntegrationTest` | [`ConsumerOfflineIntegrationTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/messaging/ConsumerOfflineIntegrationTest.java) |
| 2 | Consumer crash mid-processing | Redelivery; one `SENT`, attempt shows `redelivered=true` | automated + manual | Test: `DuplicateRedeliveryIntegrationTest`; manual: [phase-4-consumer-crash.md](phase-4-consumer-crash.md) with `slow@example.com` | [`DuplicateRedeliveryIntegrationTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/service/DuplicateRedeliveryIntegrationTest.java) |
| 3 | Message processing fails | `flaky` → `SENT` after retries; `perm-fail` → `DEAD_LETTERED` | automated | `mvn -pl consumer-service test -Dtest=NotificationProcessingIntegrationTest` | [`NotificationProcessingIntegrationTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/service/NotificationProcessingIntegrationTest.java) |
| 4 | RabbitMQ unavailable | `POST` → `503`, `PUBLISH_FAILED`; recovery without app restart | manual-compose | [phase-6-broker-outage.md](phase-6-broker-outage.md): `docker compose stop rabbitmq` then `start` | Notes + producer `POST` behaviour (Phase 6) |
| 5 | Producer loses broker connection | In-flight `503`; later `202` after auto-recovery | manual-ui | Management UI **Connections** → force-close producer connection; submit again | Same as 4 + UI connection close; confirm timeout ambiguity (`OQ-05`) |
| 6 | Duplicate delivery | One outcome; `duplicate.ignored` in logs | automated | `DuplicatePublishIntegrationTest`; manual: [phase-7-duplicate-delivery.md](phase-7-duplicate-delivery.md) | [`DuplicatePublishIntegrationTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/service/DuplicatePublishIntegrationTest.java) |
| 7 | Broker restart | Durable queue depth unchanged; redelivery after restart | automated + manual | `PersistentPublishIntegrationTest`; manual: [phase-4-broker-restart.md](phase-4-broker-restart.md) | [`PersistentPublishIntegrationTest`](../producer-service/src/test/java/com/example/notificationlab/producer/messaging/PersistentPublishIntegrationTest.java) |
| 8 | Concurrent consumers | N messages → N `SENT`, no duplicate outcomes | automated + manual | `ConsumerConcurrencyIntegrationTest`; manual: [phase-8-dual-consumer-instances.md](phase-8-dual-consumer-instances.md) | [`ConsumerConcurrencyIntegrationTest`](../consumer-service/src/test/java/com/example/notificationlab/consumer/service/ConsumerConcurrencyIntegrationTest.java) |
| 9 | Retries exhausted | `temp-fail@example.com` → `DEAD_LETTERED`, DLQ depth 1, 4 attempts | automated | `NotificationProcessingIntegrationTest#tempFailExhaustsRetriesAndDeadLetters` | Same integration test class |

## Scenario 5 detail (producer connection loss)

1. Producer and consumer running; infrastructure up.
2. Open RabbitMQ UI → **Connections** → close the connection from `producer-service` (or block AMQP briefly).
3. `POST /api/v1/notifications` while disconnected → expect `503` / `MESSAGE_PUBLISH_FAILED`.
4. Without restarting the JVM, `POST` again after the client reconnects → expect `202` / `QUEUED`.