# Phase 10 — Final review and retrospective (T-10.6)

## Learning objectives (project.md §3) — evidence

| # | Objective | Evidence |
|---|-----------|----------|
| 1 | Direct, topic, fanout exchanges | Phase 1 experiments; [phase-3-direct-exchange.md](phase-3-direct-exchange.md); lab topic/fanout listeners |
| 2 | Queues, bindings, ack, redelivery | Phases 2–4; manual ack tests; `DuplicateRedeliveryIntegrationTest` |
| 3 | Durability and persistence | Phase 4 notes; `PersistentPublishIntegrationTest` |
| 4 | Retry + DLQ + TTL | Phase 5; [phase-5-retry-ttl.md](phase-5-retry-ttl.md) |
| 5 | Publisher confirms | Phase 6; [phase-6-broker-outage.md](phase-6-broker-outage.md) |
| 6 | Idempotent consumer | Phase 7; `processed_messages` + duplicate tests |
| 7 | Concurrency and prefetch | Phase 8 notes (measured tables) |
| 8 | Deliberate failure scenarios | [phase-10-failure-scenarios.md](phase-10-failure-scenarios.md) |

## Engineering objectives

- **Multi-module Maven lab:** `shared-contracts`, `producer-service`, `consumer-service`.
- **TDD / Testcontainers:** `mvn clean verify`.
- **Five consistent docs** + phase notes; [phase-10-doc-consistency.md](phase-10-doc-consistency.md).

## Why exactly-once is not a RabbitMQ guarantee

RabbitMQ provides **at-least-once** delivery when messages are persistent, queues durable, and consumers use manual acks after durable work. The broker cannot atomically commit with the consumer’s database or an external provider. This lab records **one outcome per `messageId`** via `processed_messages` (effectively-once *recorded* outcomes), but cannot prevent two in-flight attempts on the same message from both invoking a real provider before either commits (`OQ-04`). Publisher confirm timeouts can also leave ambiguous states (`OQ-05`).

## Future enhancements (not built)

- Transactional **outbox** for Producer DB-write + publish (`OQ-02`)
- HTTP **`Idempotency-Key`** on submit (`OQ-05`)
- **DLQ replay** tooling (`OQ-01`)
- Tiered retry backoff (fixed TTL today)
- **Processing lease** against concurrent in-flight duplicates (`OQ-04`)
- Data retention / archival policies

## Closure artifacts

- E2E: [`scripts/e2e-smoke.sh`](../scripts/e2e-smoke.sh)
- Scenarios: [phase-10-failure-scenarios.md](phase-10-failure-scenarios.md)
- NFRs: [phase-10-nfr-evidence.md](phase-10-nfr-evidence.md)