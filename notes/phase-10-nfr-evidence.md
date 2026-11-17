# Phase 10 — NFR verification evidence (T-10.3)

Consolidated evidence for [project.md §11](../documentation/project.md). Measured numbers are labelled *measured on developer machine* (not SLAs).

| ID | How we verify (summary) | Evidence | Result |
|----|-------------------------|----------|--------|
| NFR-01 | Failure experiments; no silent loss after accept | Scenarios 1–9 in [phase-10-failure-scenarios.md](phase-10-failure-scenarios.md); confirms + manual ack + DLQ | Covered |
| NFR-02 | Broker restart; queue depth | `PersistentPublishIntegrationTest`; [phase-4-broker-restart.md](phase-4-broker-restart.md) | Covered |
| NFR-03 | API responsive; consumer throughput | Design: async API; Phase 8 notes (fill tables in [phase-8-concurrency-throughput.md](phase-8-concurrency-throughput.md), [phase-8-prefetch-experiment.md](phase-8-prefetch-experiment.md)) | Design goal; *measured* tables in notes |
| NFR-04 | Two consumer JVMs | [phase-8-dual-consumer-instances.md](phase-8-dual-consumer-instances.md); `ConsumerConcurrencyIntegrationTest` | Covered |
| NFR-05 | Stop/start infra; apps recover | [phase-9-health.md](phase-9-health.md); scenario 4 in failure matrix | Covered (single-node; no HA) |
| NFR-06 | Nine failure scenarios | [phase-10-failure-scenarios.md](phase-10-failure-scenarios.md) | Covered |
| NFR-07 | Status transitions; stuck publish | Integration tests; gauge `notifications.pending_publish.stuck` (Phase 9); `ProducerNotificationRepositoryIT` | Covered; `OQ-02` gap documented |
| NFR-08 | Duplicate → one outcome | `DuplicatePublishIntegrationTest`, `IdempotencyServiceConcurrentTest`, `DuplicateRedeliveryIntegrationTest` | Covered; `OQ-04` in-flight limit documented |
| NFR-09 | Modular repo; docs | Multi-module POM; [phase-10-doc-consistency.md](phase-10-doc-consistency.md) | Covered |
| NFR-10 | End-to-end trace | Phase 9 MDC + metrics; [phase-9-management-ui-runbook.md](phase-9-management-ui-runbook.md); correlation tests in `NotificationProcessingIntegrationTest` | Covered |
| NFR-11 | Secrets; log privacy | `.env.example`; `NotificationProcessingIntegrationTest#logsDoNotContainRawRecipientOrMessageBody` | Covered (local lab scope) |
| NFR-12 | `mvn clean verify` + Docker only | Full suite Testcontainers; `FailureScenarioCoverageTest` guard | Covered |