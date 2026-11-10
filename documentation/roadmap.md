# RabbitMQ Engineering Lab — Implementation Roadmap

> **Related:** `project.md` (requirements `FR-*`, `NFR-*`, decisions `PD-*`), `api.md`, `implementation.md`, `flow.md`
> The roadmap is **incremental**, **beginner-friendly** and **test-driven**. Each task is small, independently testable, and ends with a clear completion condition.

---

## 1. How to Work Through This Roadmap

**Working agreement (red → green → refactor).** For every task that has code:

1. Write or extend a **failing test** (or define the manual check) that expresses the acceptance criteria.
2. Implement the smallest change that makes it pass.
3. Refactor, run the full test suite, commit with the task ID in the message (e.g. `T-3.4: validate EMAIL recipient`).
4. Update the documentation listed in the task's **Docs** field. If you discover the documents are wrong, fix the document *and* note the change in the task's commit.

**Task fields.** Each task lists: Module, Prerequisites, Objective, What to implement, Expected behaviour, Testing procedure, Acceptance criteria, Docs to update. **Module** `infra` means Docker/Maven/UI work with no application code. Tasks marked **[Experiment]** produce understanding rather than production code; their acceptance criterion is a written observation in your own notes (outside the documentation directory).

**Rules of order.**
- Basic message consumption (Phase 2–3) is understood before manual acknowledgements (Phase 4).
- Manual acknowledgements are understood before retry and DLQ (Phase 5).
- Publisher confirms (Phase 6) come after the Producer publishes successfully.
- Idempotency (Phase 7) comes after retries exist, because retries and crashes are what create duplicates.
- Concurrency (Phase 8) comes after idempotency, because concurrency without idempotency hides bugs.

```mermaid
flowchart LR
    P0["Phase 0<br/>Foundation"] --> P1["Phase 1<br/>RabbitMQ basics"]
    P1 --> P2["Phase 2<br/>First vertical slice"]
    P2 --> P3["Phase 3<br/>Routing and API"]
    P3 --> P4["Phase 4<br/>Manual ack and durability"]
    P4 --> P5["Phase 5<br/>Retry and DLQ"]
    P5 --> P6["Phase 6<br/>Publisher confirms"]
    P6 --> P7["Phase 7<br/>Idempotency"]
    P7 --> P8["Phase 8<br/>Concurrency and prefetch"]
    P8 --> P9["Phase 9<br/>Observability"]
    P9 --> P10["Phase 10<br/>Testing and final review"]
```

**Names and defaults** used below are those in `project.md` section 0 and `PD-10` (3 retries, 10 s delay, prefetch 10, concurrency 2–4). Reserved test recipients are in `api.md` section 7.

---

## Phase 0 — Project and Infrastructure Foundation

### T-0.1 Verify tooling
- **Module:** infra · **Prereq:** none
- **Objective:** Confirm the machine can build and run everything.
- **Implement:** Install or check Java 21, Maven 3.9+, Docker with Compose; create the Git repository `rabbitmq-engineering-lab`.
- **Expected:** All tools report versions.
- **Test:** Run `java -version`, `mvn -version`, `docker compose version`, `docker run hello-world`.
- **Acceptance:** Java reports 21; the Docker test container runs.
- **Docs:** `project.md` assumption A-01 if versions differ.

### T-0.2 Create the Maven multi-module skeleton
- **Module:** infra · **Prereq:** T-0.1
- **Objective:** A building, empty three-module project.
- **Implement:** Parent POM (Java 21, Spring Boot BOM); modules `shared-contracts`, `producer-service`, `consumer-service`; copy the five Markdown files into `documentation/`.
- **Expected:** Build succeeds; producer and consumer depend on `shared-contracts` only.
- **Test:** `mvn clean verify` at the root; confirm no dependency between producer and consumer.
- **Acceptance:** Build green; dependency tree matches `implementation.md` section 2.1.
- **Docs:** `implementation.md` 2 (if structure differs).

### T-0.3 Start RabbitMQ with Docker Compose
- **Module:** infra · **Prereq:** T-0.2
- **Objective:** A durable local broker.
- **Implement:** `docker/docker-compose.yml` service `rabbitmq` (`rabbitmq:4-management`, ports 5672/15672, volume `rabbitmq_data`, health check); `.env.example`; real `.env` git-ignored.
- **Expected:** Container healthy; UI reachable.
- **Test:** `docker compose up -d`; open `http://localhost:15672` and log in with the lab user.
- **Acceptance:** UI shows the broker; login works with the dedicated user (not `guest`).
- **Docs:** `implementation.md` 16.

### T-0.4 Add MySQL and create database users
- **Module:** infra · **Prereq:** T-0.3
- **Objective:** MySQL with schema `notification_lab` and two users.
- **Implement:** Service `mysql` (`mysql:8.0`, volume `mysql_data`, health check); init script creating schema, `lab_consumer` and `lab_producer` with the privileges in `implementation.md` 8.1 (table grants applied after Flyway creates the tables; document the order).
- **Expected:** Both users can log in; `lab_producer` cannot drop tables.
- **Test:** Connect with a MySQL client as each user; try a forbidden statement as `lab_producer`.
- **Acceptance:** Allowed statements succeed, forbidden ones fail.
- **Docs:** `implementation.md` 8.1, 16.

### T-0.5 Create runnable skeleton applications
- **Module:** producer-service, consumer-service · **Prereq:** T-0.2
- **Objective:** Two Spring Boot apps that start and report health.
- **Implement:** Main classes, Actuator `health`; producer on 8080, consumer on 8081; no business code yet.
- **Expected:** Both start independently.
- **Test:** Run both; `GET /actuator/health` on each returns `UP`.
- **Acceptance:** Health `UP` for both; ports as in assumption A-03.
- **Docs:** none.

### T-0.6 Configuration structure and profiles
- **Module:** both · **Prereq:** T-0.5
- **Objective:** Externalised configuration.
- **Implement:** `application.yml` per service, profile `local`, environment-variable overrides for broker and DB credentials; property names from `implementation.md` 17.
- **Expected:** Changing an environment variable changes the connection target without code changes.
- **Test:** Start with a wrong password via env var and observe the connection failure; fix it and observe success.
- **Acceptance:** No credentials in committed files.
- **Docs:** `implementation.md` 17.

### T-0.7 Testcontainers smoke test
- **Module:** both · **Prereq:** T-0.6
- **Objective:** Prove the test infrastructure works before writing real tests.
- **Implement:** One test that starts `rabbitmq:4-management` and `mysql:8.0` via Testcontainers and checks they accept connections.
- **Expected:** Containers start, test passes, containers are removed.
- **Test:** `mvn verify`.
- **Acceptance:** Test green on a clean machine with only Docker.
- **Docs:** `implementation.md` 15 (note any shared-container strategy).

**Phase 0 checkpoint:** `mvn clean verify` is green; `docker compose up -d` yields healthy services; both apps show `UP`.

---

## Phase 1 — RabbitMQ Fundamentals (no application code)

### T-1.1 Tour the Management UI
- **Module:** infra · **Prereq:** T-0.3
- **Objective:** Learn the vocabulary: connection, channel, exchange, queue, binding, virtual host.
- **Implement:** Nothing; explore Overview, Connections, Channels, Exchanges, Queues tabs. Identify the default exchange and the built-in exchange types.
- **Expected:** You can describe each tab.
- **Test:** Write down the difference between a connection and a channel and where "Ready" and "Unacked" are shown.
- **Acceptance:** Notes complete; you can locate every item above.
- **Docs:** none (FR-B-06 first touch).

### T-1.2 [Experiment] Publish and read through the default exchange
- **Module:** infra · **Prereq:** T-1.1
- **Objective:** See publish → queue → get → ack with no code.
- **Implement:** Create durable queue `lab.hello.queue`; publish "hello" to the default exchange with routing key `lab.hello.queue`; use **Get messages** with *Nack message requeue true*, then again with *Automatic ack*.
- **Expected:** Message stays when requeued; disappears with ack.
- **Test:** Watch Ready/Unacked/Total after each step.
- **Acceptance:** You can explain why Ready stayed 1 after the requeue.
- **Docs:** none.

### T-1.3 [Experiment] Build a direct exchange by hand
- **Module:** infra · **Prereq:** T-1.2
- **Objective:** Understand exchange → binding → queue.
- **Implement:** Create direct exchange `lab.direct`, queues `lab.a` and `lab.b`, bindings `key-a` → `lab.a`, `key-b` → `lab.b`. Publish with `key-a`, `key-b`, and `key-c`.
- **Expected:** Messages land only where the key matches; `key-c` goes nowhere.
- **Test:** Check the queue counts after each publish.
- **Acceptance:** You predicted all three results before publishing.
- **Docs:** none.

### T-1.4 [Experiment] Durable and persistent versus transient
- **Module:** infra · **Prereq:** T-1.3
- **Objective:** See what a broker restart does.
- **Implement:** Create one durable and one non-durable queue; publish one persistent and one non-persistent message to each (delivery mode 2 vs 1); `docker compose restart rabbitmq`.
- **Expected:** Only the durable queue survives; within it, only the persistent message survives.
- **Test:** Compare queues and counts after restart.
- **Acceptance:** Outcome table written; delete all `lab.*` objects afterwards.
- **Docs:** none (prepares NFR-02).

**Phase 1 checkpoint:** You can explain exchange types, bindings, ack, durability and persistence in your own words.

---

## Phase 2 — Producer and Consumer: First Vertical Slice (EMAIL only)

### T-2.1 Define shared contracts
- **Module:** shared-contracts · **Prereq:** T-0.2
- **Objective:** One message contract and one set of names.
- **Implement:** `NotificationMessage` with the eight fields (`project.md` 8.3); `NotificationChannel` (`EMAIL`, `SMS`); constants for exchange, queue and routing-key names.
- **Expected:** JSON round trip preserves all fields; unknown JSON fields are ignored.
- **Test:** Unit tests: serialize → deserialize equality; missing `retryCount` handling defined; ISO-8601 `createdAt`.
- **Acceptance:** Tests green; no Spring dependency in the module.
- **Docs:** `project.md` 8.3 if field handling changed.

### T-2.2 Configure the RabbitMQ connection in both apps
- **Module:** both · **Prereq:** T-0.6, T-1.4
- **Objective:** Apps connect to the broker.
- **Implement:** Add Spring AMQP; connection properties from config.
- **Expected:** Each app opens one connection visible in the UI.
- **Test:** Start both; open **Connections** and **Channels** tabs.
- **Acceptance:** Two connections listed with the apps' names; stopping an app removes its connection.
- **Docs:** none.

### T-2.3 Declare the direct exchange
- **Module:** producer-service, consumer-service · **Prereq:** T-2.2, T-2.1
- **Objective:** `notification.direct.exchange` exists (durable, direct).
- **Implement:** Declare it in both apps using the shared constant.
- **Expected:** Appears in the UI on startup; second declaration is harmless.
- **Test:** Start consumer, check **Exchanges**; start producer, no error.
- **Acceptance:** Type direct, durable true.
- **Docs:** `implementation.md` 5.3.

### T-2.4 Declare `email.queue`
- **Module:** consumer-service · **Prereq:** T-2.3
- **Objective:** Durable queue exists.
- **Implement:** Declare `email.queue` (durable, no extra arguments yet).
- **Expected:** Queue visible with 0 messages.
- **Test:** Start consumer, check **Queues**.
- **Acceptance:** Durable `D` flag shown.
- **Docs:** `implementation.md` 5.4.

### T-2.5 Bind `email.queue` to the exchange
- **Module:** consumer-service · **Prereq:** T-2.4
- **Objective:** Key `notification.email` routes to `email.queue`.
- **Implement:** Declare the binding.
- **Expected:** Binding visible on the exchange and the queue.
- **Test:** Publish from the UI to the exchange with key `notification.email`; then with key `notification.sms`.
- **Acceptance:** First message arrives in `email.queue`; second is dropped (no binding). Purge the queue afterwards.
- **Docs:** none.

### T-2.6 Publish one message from the Producer
- **Module:** producer-service · **Prereq:** T-2.5, T-2.1
- **Objective:** First message sent by code.
- **Implement:** A lab-only runner (profile `lab`) that publishes one hard-coded `NotificationMessage` to `notification.direct.exchange` with key `notification.email`. No REST yet.
- **Expected:** The runner publishes once and exits cleanly.
- **Test:** Run with `lab` profile; check logs.
- **Acceptance:** Log line shows the message ID published; no exception.
- **Docs:** none.

### T-2.7 Verify the message appears in the queue
- **Module:** infra · **Prereq:** T-2.6 (consumer stopped)
- **Objective:** Confirm the broker stored it.
- **Implement:** Nothing; inspect via UI.
- **Expected:** `email.queue` Ready = 1.
- **Test:** UI → `email.queue` → **Get messages** with *Nack requeue true*; inspect JSON body and properties.
- **Acceptance:** Body has all eight fields; `content_type = application/json` is **not yet** guaranteed (fixed in T-2.10). Record what you see.
- **Docs:** none.

### T-2.8 Consume one message (default acknowledgement)
- **Module:** consumer-service · **Prereq:** T-2.7
- **Objective:** A listener on `email.queue` that logs the message.
- **Implement:** Minimal listener, default (automatic) ack, logs `messageId` only.
- **Expected:** On startup the waiting message is received once.
- **Test:** Start consumer with the T-2.7 message waiting.
- **Acceptance:** One log line; no stack trace.
- **Docs:** none.

### T-2.9 Verify the queue is empty — and what automatic ack risks
- **Module:** infra · **Prereq:** T-2.8
- **Objective:** Observe acknowledgement effect; see why it is at-most-once.
- **Implement:** Nothing; then temporarily make the listener throw after receiving, republish one message, observe.
- **Expected:** Queue Ready = 0 after success. With the throwing listener, behaviour depends on defaults — note it (feeds T-4.3).
- **Test:** UI counts before and after.
- **Acceptance:** You can explain why automatic ack is at-most-once. Revert the throwing change.
- **Docs:** none.

### T-2.10 Configure JSON serialization
- **Module:** both · **Prereq:** T-2.8
- **Objective:** Proper JSON messages with correct properties.
- **Implement:** Jackson message converter on both sides; set `content_type`, `message_id`, `correlation_id`, persistent delivery mode; listener declares its type explicitly (do not rely on type headers).
- **Expected:** UI shows JSON content type, persistent mode, IDs in properties.
- **Test:** Publish again; inspect in UI; consumer deserializes to the DTO.
- **Acceptance:** Round trip works across the two apps; `createdAt` is ISO-8601.
- **Docs:** `implementation.md` 6.

### T-2.11 Consumer database schema with Flyway
- **Module:** consumer-service · **Prereq:** T-0.4, T-0.6
- **Objective:** Three tables created by migration.
- **Implement:** Flyway migration `V1` creating `notifications`, `notification_attempts`, `processed_messages` with keys, constraints and indexes (`implementation.md` 8).
- **Expected:** Tables exist after startup.
- **Test:** Start consumer; inspect with a MySQL client; Testcontainers test applies migrations to an empty database.
- **Acceptance:** Constraints reject a duplicate `message_id` and an invalid `status`/`channel` value.
- **Docs:** `implementation.md` 8 if columns changed.

### T-2.12 Entities and repositories
- **Module:** consumer-service · **Prereq:** T-2.11
- **Objective:** Read and write the tables from code.
- **Implement:** JPA entities and repositories; `version` for optimistic locking.
- **Expected:** CRUD works; schema validation passes.
- **Test:** Repository tests with Testcontainers MySQL: save/find, unique `(notification_id, attempt_number)`, FK enforcement.
- **Acceptance:** All repository tests green.
- **Docs:** `implementation.md` 8.5.

### T-2.13 Delivery simulator (EMAIL)
- **Module:** consumer-service · **Prereq:** T-2.1
- **Objective:** Deterministic simulated delivery.
- **Implement:** `EmailDeliverySimulator` with rules in `implementation.md` 10; exceptions `TemporaryDeliveryException`, `PermanentDeliveryException`.
- **Expected:** Each reserved recipient behaves as specified.
- **Test:** Unit tests per rule including `flaky@example.com` with `retryCount` 0, 1, 2; latency configurable to 0 in tests.
- **Acceptance:** All rule tests pass.
- **Docs:** `implementation.md` 10, `api.md` 7 if rules changed.

### T-2.14 Basic processing with recorded outcome (no retries yet)
- **Module:** consumer-service · **Prereq:** T-2.10, T-2.12, T-2.13
- **Objective:** The listener persists a result.
- **Implement:** `NotificationProcessingService`: insert notification if absent, run simulator, write attempt row and status `SENT`. For now any failure just logs (retry and DLQ come later).
- **Expected:** One message → one `SENT` row and one `SUCCESS` attempt.
- **Test:** Integration test (Testcontainers RabbitMQ + MySQL): publish via template; await status `SENT`.
- **Acceptance:** Test green; table contents match expectations.
- **Docs:** `flow.md` 6 if flow differs.

**Phase 2 checkpoint:** A message published by the Producer's lab runner is consumed, and the result is stored in MySQL.

---

## Phase 3 — Exchanges, Queues, Bindings, Routing and the REST API

### T-3.1 Declare `sms.queue` and its binding
- **Module:** consumer-service · **Prereq:** T-2.5
- **Objective:** Second channel path.
- **Implement:** Durable `sms.queue`, binding key `notification.sms`.
- **Expected:** Visible in UI.
- **Test:** UI publish with `notification.sms` lands in `sms.queue` only.
- **Acceptance:** `email.queue` count unchanged.
- **Docs:** `implementation.md` 5.4.

### T-3.2 SMS listener and simulator
- **Module:** consumer-service · **Prereq:** T-3.1, T-2.14
- **Objective:** Independent SMS consumer (FR-C-02).
- **Implement:** Separate listener container for `sms.queue`; `SmsDeliverySimulator` with SMS reserved numbers.
- **Expected:** SMS messages processed without touching the email listener.
- **Test:** Integration test publishes one EMAIL and one SMS; both reach `SENT`. Stop the SMS listener, publish both: only EMAIL completes.
- **Acceptance:** Independence demonstrated.
- **Docs:** `flow.md` 7.

### T-3.3 Message factory and routing key selector
- **Module:** producer-service · **Prereq:** T-2.1
- **Objective:** Pure logic for IDs, message and key (FR-P-03/04/05).
- **Implement:** `MessageFactory` (UUIDs, UTC `createdAt`, `retryCount` 0), `RoutingKeySelector`.
- **Expected:** `EMAIL` → `notification.email`, `SMS` → `notification.sms`.
- **Test:** Unit tests: key mapping, unique IDs, field values, unsupported value rejected.
- **Acceptance:** Tests green.
- **Docs:** none.

### T-3.4 Request DTO and validation
- **Module:** producer-service · **Prereq:** T-0.5
- **Objective:** All rules in `api.md` 3.1–3.2 (FR-P-02).
- **Implement:** Request DTO, Bean Validation, channel-specific recipient validators (email format, E.164), unknown-property rejection.
- **Expected:** Valid requests pass; each invalid case yields the specified field detail.
- **Test:** Unit and web-slice tests covering every row of the validation table in `api.md` 3.5.
- **Acceptance:** Every scenario in `api.md` 3.5 (except 503/500) has a passing test.
- **Docs:** `api.md` 3 if rules were adjusted.

### T-3.5 Error response format and exception handler
- **Module:** producer-service · **Prereq:** T-3.4
- **Objective:** One error structure everywhere.
- **Implement:** `GlobalExceptionHandler`, `CorrelationIdFilter`, error code catalogue.
- **Expected:** All errors match `api.md` 2.4, with `X-Correlation-Id` on every response.
- **Test:** Web-slice tests for 400, 404, 405, 415, 500 shapes; malformed `X-Correlation-Id`.
- **Acceptance:** Error JSON shape asserted field by field.
- **Docs:** `api.md` 2.4–2.5.

### T-3.6 Producer database access and submission record
- **Module:** producer-service · **Prereq:** T-2.11, T-3.3
- **Objective:** Create `PENDING_PUBLISH` rows (FR-P-09, `PD-01`).
- **Implement:** Minimal entity for `notifications` with `ddl-auto=validate`; DB user `lab_producer`; repository insert and conditional status update; resolve how tests get the schema (`OQ-03`).
- **Expected:** Insert succeeds; updates only touch allowed columns; forbidden operations fail.
- **Test:** Repository test with Testcontainers: insert, conditional `PENDING_PUBLISH → QUEUED`, no change when status already `PROCESSING`.
- **Acceptance:** Tests green; `lab_producer` cannot delete.
- **Docs:** `project.md` `OQ-03` resolution, `implementation.md` 8.1.

### T-3.7 POST /notifications with basic publishing
- **Module:** producer-service · **Prereq:** T-3.3, T-3.4, T-3.5, T-3.6, T-2.10, T-3.1
- **Objective:** End-to-end submission without confirms yet (FR-P-01).
- **Implement:** Controller and service: validate → insert row → publish → mark `QUEUED` → `202` with the response body of `api.md` 3.4.
- **Expected:** `202`, row `QUEUED` (temporary: confirm handling comes in Phase 6), message in the correct queue.
- **Test:** MockMvc test with a mocked publisher; Testcontainers integration test: POST, then queue contains the message.
- **Acceptance:** Response never claims delivery; `Location` header present.
- **Docs:** `api.md` 3 if response changed.

### T-3.8 GET /notifications/{id}
- **Module:** producer-service · **Prereq:** T-3.7, T-2.14
- **Objective:** Status retrieval (FR-P-10).
- **Implement:** Query service and controller per `api.md` section 4, including `attempts` and `statusDescription`.
- **Expected:** Fresh notification returns `QUEUED`; after consumption returns `SENT` with one attempt; unknown ID returns `404`; bad UUID `400`.
- **Test:** Web-slice tests; integration test across both flows with Awaitility on status.
- **Acceptance:** Examples in `api.md` 4.3 reproducible.
- **Docs:** `api.md` 4.

### T-3.9 GET /notifications — list, filter, paginate
- **Module:** producer-service · **Prereq:** T-3.8
- **Objective:** FR-P-11.
- **Implement:** Filters `status`, `channel`, `createdFrom`, `createdTo`; `page`, `size`, `sort`; envelope per `api.md` 5.3.
- **Expected:** Filters combine with AND; invalid parameters give `INVALID_PAGINATION` / `INVALID_FILTER`.
- **Test:** Repository test with 25 seeded rows (pages, sort order, filters, empty page beyond the end); web-slice tests for all error rows in `api.md` 5.4.
- **Acceptance:** Every error and success scenario tested; queries use the planned indexes (check with `EXPLAIN`).
- **Docs:** `api.md` 5; `implementation.md` 8.2 if indexes changed.

### T-3.10 OpenAPI / Swagger
- **Module:** producer-service · **Prereq:** T-3.9
- **Objective:** Generated docs that match the contract.
- **Implement:** springdoc; annotate request/response/error schemas; enable only in `local`.
- **Expected:** `/swagger-ui.html` lists the three endpoints with examples.
- **Test:** Compare generated schemas with `api.md`; confirm Swagger is absent in the `test` profile.
- **Acceptance:** No mismatch between `api.md` and the OpenAPI document.
- **Docs:** `api.md` 2 (conventions).

### T-3.11 [Experiment] Direct exchange: matching and unroutable messages
- **Module:** infra · **Prereq:** T-3.7
- **Objective:** See exact matching and silent drops (FR-B-01).
- **Implement:** Publish via UI with `notification.email`, `notification.sms`, `Notification.Email`, `notification.push`.
- **Expected:** Only the first two route; case matters; others vanish silently.
- **Test:** Queue counts; check the exchange's message-rate graph.
- **Acceptance:** Notes explain why `mandatory` will be needed (Phase 6).
- **Docs:** `flow.md` 3 if observations differ.

### T-3.12 [Experiment] Topic exchange scenario
- **Module:** consumer-service, producer-service (lab) · **Prereq:** T-3.11
- **Objective:** Pattern routing (FR-B-02, `PD-07`).
- **Implement:** Profile `lab-exchanges`: `notification.topic.exchange`, three lab queues, bindings from `implementation.md` 5.5, a logging lab listener; lab runner publishes the six keys from `flow.md` 4.
- **Expected:** Results match the matrix in `flow.md` 4.
- **Test:** Integration test asserting which queue receives which key; compare to UI.
- **Acceptance:** All 18 cells of the matrix verified.
- **Docs:** `flow.md` 4; `implementation.md` 5.5.

### T-3.13 [Experiment] Fanout exchange scenario
- **Module:** consumer-service, producer-service (lab) · **Prereq:** T-3.12
- **Objective:** Broadcast (FR-B-03).
- **Implement:** `notification.fanout.exchange`, two lab queues; publish with different and empty routing keys.
- **Expected:** Both queues get one copy each time; routing key irrelevant.
- **Test:** Integration test counts; delete one queue and re-publish.
- **Acceptance:** Observed counts match `flow.md` 5.
- **Docs:** `flow.md` 5.

### T-3.14 Routing integration tests
- **Module:** both · **Prereq:** T-3.7, T-3.2
- **Objective:** Regression safety for routing.
- **Implement:** Testcontainers tests: API → `email.queue` for EMAIL, → `sms.queue` for SMS; wrong-key publish is unroutable.
- **Expected:** Deterministic and independent of the lab profiles.
- **Test:** `mvn verify`.
- **Acceptance:** Green on a clean checkout.
- **Docs:** `implementation.md` 15.

**Phase 3 checkpoint:** All three APIs work (with basic, unconfirmed publishing); both channels are processed independently; direct, topic and fanout behaviours are demonstrated.

---

## Phase 4 — Manual Acknowledgements and Message Durability

### T-4.1 Manual ack in the email listener
- **Module:** consumer-service · **Prereq:** T-3.14
- **Objective:** Ack only after the outcome is recorded (FR-C-06).
- **Implement:** Acknowledge mode `manual`; listener returns an instruction from the processing service and performs `basicAck` after TX-2 commit.
- **Expected:** `Unacked = 1` during processing; `0` after.
- **Test:** `slow@example.com`: watch **Unacked** in the UI while processing; integration test with Awaitility.
- **Acceptance:** Message removed only after `SENT` is committed.
- **Docs:** `flow.md` 8.

### T-4.2 Manual ack in the SMS listener
- **Module:** consumer-service · **Prereq:** T-4.1
- **Objective:** Same behaviour for SMS.
- **Implement:** Reuse the same mechanism.
- **Expected:** Same as T-4.1 using `+15550100004`.
- **Test:** As above.
- **Acceptance:** Both listeners acknowledge manually; no duplicated acknowledgement code.
- **Docs:** none.

### T-4.3 [Experiment] Exception and the requeue loop
- **Module:** consumer-service · **Prereq:** T-4.1
- **Objective:** Experience the infinite redelivery pitfall.
- **Implement:** Temporarily throw an unexpected exception in the listener with default requeue behaviour; publish one message; watch the redelivery counter and logs. Then switch to `nack` with requeue = false (the message is dropped for now, since no DLX exists yet) and re-run.
- **Expected:** First run loops hot; second run stops the loop.
- **Test:** Log rate and UI "redelivered" counters.
- **Acceptance:** Notes explain the loop; the safe handler (reject without requeue) is kept as the default for unexpected errors until Phase 5 gives it a destination.
- **Docs:** `implementation.md` 13.2.

### T-4.4 [Experiment] Kill the consumer mid-processing
- **Module:** infra · **Prereq:** T-4.1
- **Objective:** Redelivery after crash (scenario 2, flow 13).
- **Implement:** Submit `slow@example.com`; `kill -9` the consumer within the 15 s; restart.
- **Expected:** Message redelivered with `redelivered = true`; ends `SENT` once.
- **Test:** UI counts; `GET` the notification; check the attempt row's `redelivered`.
- **Acceptance:** One `SENT` outcome, no lost message; observations match `flow.md` 13.
- **Docs:** `flow.md` 13 if different.

### T-4.5 Persistent publishing check
- **Module:** producer-service · **Prereq:** T-2.10
- **Objective:** FR-P-06 verified by test.
- **Implement:** Ensure persistent delivery mode on every notification message; add a test that fetches a published message and asserts `delivery_mode = 2`. Optionally publish a non-persistent copy by hand for comparison.
- **Expected:** UI shows **Persistent** on messages from the API.
- **Test:** Integration test + UI check.
- **Acceptance:** Test green.
- **Docs:** `implementation.md` 6.

### T-4.6 [Experiment] Broker restart with durable topology
- **Module:** infra · **Prereq:** T-4.5
- **Objective:** Scenario 7 / flow 14.
- **Implement:** Stop the consumer; submit 5 notifications; `docker compose restart rabbitmq`; start the consumer. Then repeat after temporarily switching `email.queue` to non-durable (recreate it) to see the loss.
- **Expected:** Durable run: all 5 processed. Non-durable run: queue and messages gone.
- **Test:** Queue depth before/after restart; statuses via `GET`.
- **Acceptance:** Both outcomes observed; durable setup restored.
- **Docs:** `flow.md` 14.

### T-4.7 Consumer-offline scenario test
- **Module:** both · **Prereq:** T-4.1
- **Objective:** Scenario 1 automated.
- **Implement:** Integration test: no listener running; POST N notifications; assert queue depth N and status `QUEUED`; start listener; assert all `SENT`.
- **Expected:** No loss while the consumer is offline.
- **Test:** `mvn verify`.
- **Acceptance:** Green.
- **Docs:** `implementation.md` 12.2 row 1 if needed.

### T-4.8 Acknowledgement integration tests
- **Module:** consumer-service · **Prereq:** T-4.4, T-4.7
- **Objective:** Regression safety for ack behaviour.
- **Implement:** Tests: ack after success; message redelivered if the listener "crashes" (channel closed) before ack; queue empty after success.
- **Expected:** Deterministic tests.
- **Test:** Run 3 times in a row for flakiness.
- **Acceptance:** Stable green.
- **Docs:** none.

**Phase 4 checkpoint:** Messages survive consumer crashes and broker restarts, and you can show `Unacked` changes in the UI.

---

## Phase 5 — Retry Mechanisms and Dead Letter Queue

### T-5.1 Create the dead-letter exchange, DLQ and queue arguments
- **Module:** consumer-service · **Prereq:** T-4.3
- **Objective:** A destination for rejected messages (FR-B-04).
- **Implement:** `notification.dlx.exchange`, `notification.dlq`, binding key `notification.dead`; add dead-letter arguments to `email.queue` and `sms.queue`. Delete the old queues first (arguments are immutable — expect `PRECONDITION_FAILED` otherwise; this is part of the lesson).
- **Expected:** Queues show the DLX arguments (`DLX`, `DLK` badges).
- **Test:** UI publish to `email.queue`, **Get** with *Reject requeue false*; message appears in `notification.dlq` with an `x-death` header.
- **Acceptance:** Rejected message arrives in the DLQ.
- **Docs:** `implementation.md` 5.4.

### T-5.2 Permanent failure goes to the DLQ
- **Module:** consumer-service · **Prereq:** T-5.1, T-2.13
- **Objective:** First real dead-lettering (FR-C-09).
- **Implement:** On `PermanentDeliveryException` record attempt `PERMANENT_FAILURE`, status `DEAD_LETTERED`, then `nack` requeue = false.
- **Expected:** `perm-fail@example.com` ends `DEAD_LETTERED` after 1 attempt; one message in `notification.dlq`.
- **Test:** Integration test; check `GET` response `lastError`.
- **Acceptance:** DLQ depth 1; email queue empty; status final.
- **Docs:** `flow.md` 11.

### T-5.3 Failure classifier
- **Module:** consumer-service · **Prereq:** T-5.2
- **Objective:** FR-C-07.
- **Implement:** `FailureClassifier`: temporary vs permanent; unknown exceptions temporary; malformed permanent.
- **Expected:** Mapping as in `implementation.md` 13.2.
- **Test:** Unit tests for each exception type.
- **Acceptance:** All mapped correctly.
- **Docs:** `implementation.md` 13.2.

### T-5.4 Declare the retry exchange and per-channel retry queues
- **Module:** consumer-service · **Prereq:** T-5.1
- **Objective:** Retry topology (`PD-02`, FR-B-05).
- **Implement:** `notification.retry.exchange`; `email.retry.queue` and `sms.retry.queue` with `x-message-ttl` from `lab.retry.delay`, dead-letter to `notification.direct.exchange` with keys `notification.email` / `notification.sms`; bindings `email.retry` / `sms.retry`; no consumers.
- **Expected:** Both queues visible with TTL and DLX arguments.
- **Test:** Topology integration test reading queue arguments.
- **Acceptance:** Arguments exactly as in `implementation.md` 5.4.
- **Docs:** `implementation.md` 5.

### T-5.5 [Experiment] Watch a message expire and return
- **Module:** infra · **Prereq:** T-5.4
- **Objective:** See TTL plus DLX as a delay.
- **Implement:** In the UI, publish a notification message to `notification.retry.exchange` with key `email.retry`; stop the email listener to observe the message return to `email.queue` after about 10 s.
- **Expected:** Ready count moves from retry queue to main queue after the TTL.
- **Test:** Stopwatch and UI counts.
- **Acceptance:** Return time ≈ configured delay; notes explain what routing key the message carried when it came back.
- **Docs:** `flow.md` 10.

### T-5.6 [Experiment] Head-of-line blocking with per-message TTL
- **Module:** infra · **Prereq:** T-5.5
- **Objective:** Understand why the retry queues use queue-level TTL.
- **Implement:** Create a throwaway queue `lab.retry.shared` (no consumers, DLX to a lab queue). Publish one message with a 60 s per-message expiration, then one with 5 s.
- **Expected:** The 5 s message does not expire until the 60 s message ahead of it expires.
- **Test:** Observe both messages in the UI.
- **Acceptance:** Explanation written; delete the lab queues.
- **Docs:** `project.md` 8.2 (confirm trade-off statement).

### T-5.7 Retry decision logic
- **Module:** consumer-service · **Prereq:** T-5.3
- **Objective:** Pure decision rule.
- **Implement:** Function of (`retryCount`, `maxRetries`, classification) → `RETRY`, `DEAD_LETTER`.
- **Expected:** `retryCount < maxRetries` retries; otherwise dead-letters; permanent never retries.
- **Test:** Parameterised unit tests including `maxRetries = 0` and boundary values.
- **Acceptance:** All boundary cases pass.
- **Docs:** `implementation.md` 11.1.

### T-5.8 Retry publisher and wiring
- **Module:** consumer-service · **Prereq:** T-5.4, T-5.7
- **Objective:** Temporary failure → retry copy → ack original (FR-C-08, `PD-03`).
- **Implement:** `RetryPublisher` publishes a copy with `retryCount + 1`, same IDs, to `notification.retry.exchange` with confirm; listener acks the original only after confirm; on non-confirm `nack` with requeue after a short pause. TX-2 sets status `RETRY_SCHEDULED`.
- **Expected:** `flaky@example.com` ends `SENT` after about 20 s with `retryCount = 2`, `attemptCount = 3`.
- **Test:** Integration test with test property `lab.retry.delay=1s`; assert attempt sequence and final state via `GET`.
- **Acceptance:** Test green; UI shows the message passing through `email.retry.queue`.
- **Docs:** `flow.md` 10; `implementation.md` 11.2.

### T-5.9 Exhausted retries go to the DLQ
- **Module:** consumer-service · **Prereq:** T-5.8, T-5.2
- **Objective:** Scenario 9.
- **Implement:** When `retryCount >= maxRetries` on a temporary failure, dead-letter with `last_error` "retries exhausted after N attempts".
- **Expected:** `temp-fail@example.com`: 4 attempts, `retryCount = 3`, `DEAD_LETTERED`, DLQ depth 1.
- **Test:** Integration test (1 s delay); also a test with `maxRetries = 0`.
- **Acceptance:** `GET` response matches the example in `api.md` 5.3.
- **Docs:** `flow.md` 11.

### T-5.10 Malformed message handling
- **Module:** consumer-service · **Prereq:** T-5.1
- **Objective:** FR-C-12.
- **Implement:** Invalid JSON, missing fields, or channel not matching the queue → log (`message.malformed`) and `nack` requeue = false.
- **Expected:** Message in DLQ; listener keeps running.
- **Test:** Publish junk JSON and an `SMS` message into `email.queue` via the UI.
- **Acceptance:** DLQ depth +2; no redelivery loop.
- **Docs:** `implementation.md` 13.2.

### T-5.11 Retry and DLQ integration test suite
- **Module:** consumer-service · **Prereq:** T-5.9, T-5.10
- **Objective:** Lock in Phase 5 behaviour.
- **Implement:** Tests for flaky success, permanent failure, exhausted retries, malformed message, for both channels.
- **Expected:** All final states and DLQ counts as specified.
- **Test:** `mvn verify` three times.
- **Acceptance:** Stable green.
- **Docs:** none.

**Phase 5 checkpoint:** Every failure ends either in `SENT` or in `DEAD_LETTERED`; nothing loops forever; the DLQ contains exactly the expected messages.

---

## Phase 6 — Publisher Confirms and Reliable Publishing

### T-6.1 Enable confirms and returns
- **Module:** producer-service · **Prereq:** T-3.7
- **Objective:** Broker acknowledgement of publishes.
- **Implement:** `publisher-confirm-type=correlated`, `publisher-returns=true`, mandatory flag on every publish.
- **Expected:** Confirm callbacks fire for each publish.
- **Test:** Integration test logs or captures callback for one publish.
- **Acceptance:** Callback received with ack = true.
- **Docs:** `implementation.md` 7, 17.

### T-6.2 Wait for confirm with timeout
- **Module:** producer-service · **Prereq:** T-6.1
- **Objective:** `202` only after a confirm (FR-P-07, `PD-08`).
- **Implement:** Publish with correlation data; await the confirm with `lab.publisher.confirm-timeout`; return typed result `CONFIRMED`, `NACKED`, `TIMED_OUT`, `BROKER_UNAVAILABLE`.
- **Expected:** Normal publish returns `CONFIRMED`.
- **Test:** Unit test with a stubbed confirm future (ack, nack, never completes); integration test with the real broker.
- **Acceptance:** All four outcomes tested.
- **Docs:** `flow.md` 2.

### T-6.3 Map failures to `503` and `PUBLISH_FAILED`
- **Module:** producer-service · **Prereq:** T-6.2, T-3.5
- **Objective:** FR-P-08.
- **Implement:** `MessagePublishException` → `503 MESSAGE_PUBLISH_FAILED` with `notificationId` and `Retry-After`; conditional update to `PUBLISH_FAILED`.
- **Expected:** Body matches `api.md` 3.5 example.
- **Test:** Web-slice test with publisher stub returning each failure; DB row status asserted.
- **Acceptance:** All failure outcomes produce `503`, never `202`.
- **Docs:** `api.md` 3.5.

### T-6.4 Handle unroutable returns
- **Module:** producer-service · **Prereq:** T-6.2
- **Objective:** Returned messages are failures.
- **Implement:** Returns callback marks the correlation as `RETURNED_UNROUTABLE`.
- **Expected:** Publishing with no matching binding yields `503`.
- **Test:** Integration test: delete the `notification.email` binding (or use an exchange without it), POST, assert `503` and `PUBLISH_FAILED`; restore binding.
- **Acceptance:** Unroutable never produces `202`.
- **Docs:** `flow.md` 2, 3.

### T-6.5 Conditional status updates and race test
- **Module:** producer-service, consumer-service · **Prereq:** T-6.3
- **Objective:** Producer never overwrites a later consumer status (FR-P-09).
- **Implement:** Make Producer updates conditional on `status = PENDING_PUBLISH`; consumer's TX-1 accepts `PENDING_PUBLISH`, `QUEUED`, `PUBLISH_FAILED`.
- **Expected:** If the consumer finishes before the Producer's update, status stays `SENT`.
- **Test:** Repository test simulating the interleaving explicitly (consumer sets `PROCESSING` between Producer insert and update).
- **Acceptance:** Final status is the consumer's in every interleaving.
- **Docs:** `implementation.md` 9.4.

### T-6.6 [Experiment] Stop the broker, then restart it
- **Module:** infra · **Prereq:** T-6.3
- **Objective:** Scenarios 4 and 5.
- **Implement:** `docker compose stop rabbitmq`; POST → observe; start broker; POST again without restarting the app.
- **Expected:** `503` during outage, `202` after recovery; `PUBLISH_FAILED` row remains.
- **Test:** Time how long recovery takes; check the UI **Connections** tab.
- **Acceptance:** Observations recorded; also try closing the producer's connection in the UI.
- **Docs:** `implementation.md` 12.2 rows 4–5 if needed.

### T-6.7 Confirmed retry publishing in the Consumer
- **Module:** consumer-service · **Prereq:** T-6.1, T-5.8
- **Objective:** Retry copies are only trusted after a confirm.
- **Implement:** Enable confirms and returns on the consumer's template; use the same typed result; treat non-confirm as "do not ack".
- **Expected:** With the retry exchange binding removed, the retry publish is returned and the original is not acked.
- **Test:** Integration test with a broken retry binding; assert the original is redelivered and no message is lost.
- **Acceptance:** No loss; log shows the failure.
- **Docs:** `implementation.md` 11.2.

### T-6.8 Reliable publishing integration tests
- **Module:** producer-service · **Prereq:** T-6.4, T-6.5
- **Objective:** Lock in Phase 6.
- **Implement:** Test suite for ack, nack-equivalent, timeout (use an exchange that never confirms is hard — use a stub or a very small timeout), return, and broker-stopped cases (Testcontainers `stop`/`start`).
- **Expected:** Each result maps to the specified HTTP response and status.
- **Test:** `mvn verify`.
- **Acceptance:** Stable green.
- **Docs:** none.

**Phase 6 checkpoint:** `202` means the broker confirmed; every other outcome is `503` with a stored `PUBLISH_FAILED` record.

---

## Phase 7 — Idempotency and Duplicate Processing

### T-7.1 Record `processed_messages` on final outcomes
- **Module:** consumer-service · **Prereq:** T-5.9
- **Objective:** Write the idempotency record in TX-2 (FR-C-10, `PD-13`).
- **Implement:** On `SENT` and `DEAD_LETTERED` insert `processed_messages`; retries do not.
- **Expected:** Exactly one row per finished message; none for `RETRY_SCHEDULED`.
- **Test:** Integration tests for success, permanent failure, exhausted retries, flaky.
- **Acceptance:** Row counts and outcomes correct.
- **Docs:** `implementation.md` 8.4, 9.2.

### T-7.2 Duplicate check at the start of processing
- **Module:** consumer-service · **Prereq:** T-7.1
- **Objective:** Skip already-finished messages.
- **Implement:** TX-1 checks `processed_messages`; `SENT` → ack without processing.
- **Expected:** Second delivery of the same `messageId` causes no new attempt row.
- **Test:** Unit test of `IdempotencyService`; integration test publishing the same message twice.
- **Acceptance:** One attempt, one `SENT`, queue empty, log `duplicate.ignored`.
- **Docs:** `flow.md` 12.

### T-7.3 [Experiment] Publish the same JSON twice by hand
- **Module:** infra · **Prereq:** T-7.2
- **Objective:** See duplicates for real (scenario 6).
- **Implement:** In the UI publish an identical message body twice, with the consumer running; then again with the consumer stopped in between.
- **Expected:** Only one outcome recorded in each case.
- **Test:** `GET` the notification; query the three tables.
- **Acceptance:** Notes explain why a *different* `messageId` with the same `notificationId` would **not** be treated as a duplicate.
- **Docs:** `project.md` limitation 4 if needed.

### T-7.4 Crash between commit and ack
- **Module:** consumer-service · **Prereq:** T-7.2, T-4.8
- **Objective:** Prove the redelivery is harmless.
- **Implement:** A test hook (test profile only) that closes the channel after TX-2 commit and before `ack`.
- **Expected:** Message redelivered; duplicate path acks it; still one outcome.
- **Test:** Integration test with Awaitility; assert `redelivered` handled.
- **Acceptance:** One `SENT`, one attempt row, queue empty. Hook not active outside tests.
- **Docs:** `flow.md` 8, 12.

### T-7.5 Concurrent duplicate test
- **Module:** consumer-service · **Prereq:** T-7.2
- **Objective:** Primary-key protection under concurrency.
- **Implement:** Test that runs two `processed_messages` insertions for the same `messageId` in parallel transactions.
- **Expected:** One commits; the other fails and is treated as a duplicate.
- **Test:** Repeat 50 times to expose races.
- **Acceptance:** Exactly one row each run; no exception escapes to the container.
- **Docs:** `implementation.md` 11.4.

### T-7.6 Duplicate of a dead-lettered message
- **Module:** consumer-service · **Prereq:** T-7.2, T-5.2
- **Objective:** Duplicates of failures are also contained.
- **Implement:** If the recorded outcome is `DEAD_LETTERED`, `nack` requeue = false.
- **Expected:** Duplicate also lands in the DLQ; no processing.
- **Test:** Publish a `perm-fail@example.com` message twice.
- **Acceptance:** One `DEAD_LETTERED` row; DLQ depth 2 (two copies); one attempt row.
- **Docs:** `flow.md` 12; `project.md` `OQ-01` note on replay.

**Phase 7 checkpoint:** Duplicate deliveries never create a second outcome; the known in-flight-duplicate limitation is documented (`OQ-04`).

---

## Phase 8 — Consumer Concurrency and Prefetch

### T-8.1 Per-channel concurrency and prefetch configuration
- **Module:** consumer-service · **Prereq:** T-7.6
- **Objective:** FR-C-11.
- **Implement:** Separate listener container factories for EMAIL and SMS driven by `lab.consumer.<channel>.*` properties.
- **Expected:** UI shows the configured number of consumers and prefetch for each queue.
- **Test:** Start with e.g. email 3 / prefetch 5 and sms 1 / prefetch 1; check **Queues → Consumers**.
- **Acceptance:** Values visible and independent.
- **Docs:** `implementation.md` 11.5, 17.

### T-8.2 [Experiment] Prefetch 1 versus prefetch 10
- **Module:** infra · **Prereq:** T-8.1
- **Objective:** Understand fair dispatch.
- **Implement:** Publish 20 slow (`slow@example.com`) and 20 normal messages mixed, with 2 consumers; run with prefetch 1, then 10.
- **Expected:** Prefetch 10 can leave messages waiting behind a slow one on one consumer; prefetch 1 distributes more evenly.
- **Test:** Watch Ready/Unacked per consumer; record total time.
- **Acceptance:** Table of measured times written (labelled *measured on my machine*, not an SLA).
- **Docs:** `implementation.md` 11.5 (confirm or adjust defaults).

### T-8.3 [Experiment] Concurrency 1, 2, 5
- **Module:** infra · **Prereq:** T-8.2
- **Objective:** Observe throughput effects.
- **Implement:** Publish 200 normal messages with simulator latency 200 ms; run with concurrency 1, 2, 5.
- **Expected:** Throughput increases with threads until another limit (DB or CPU) shows up.
- **Test:** Time to drain; DB connection pool usage.
- **Acceptance:** Measurements recorded; chosen default justified.
- **Docs:** `project.md` `PD-10` if defaults change; NFR-03 notes.

### T-8.4 Run two consumer instances
- **Module:** consumer-service · **Prereq:** T-8.1
- **Objective:** Horizontal scaling (NFR-04).
- **Implement:** Start a second instance on another port (`--server.port=8082`).
- **Expected:** UI shows consumers from two connections; load shared.
- **Test:** Publish 50 messages; inspect logs of both; kill one instance mid-run.
- **Acceptance:** All 50 end `SENT`; no lost message after the kill.
- **Docs:** none.

### T-8.5 Concurrency integration test
- **Module:** consumer-service · **Prereq:** T-8.1, T-7.5
- **Objective:** Correctness under parallelism.
- **Implement:** Test publishing 50 mixed EMAIL/SMS messages with concurrency 4.
- **Expected:** 50 `SENT`, 50 attempt rows, 50 `processed_messages`, no duplicates.
- **Test:** Awaitility; repeat several times.
- **Acceptance:** Stable green; no deadlocks or constraint errors in the logs.
- **Docs:** `implementation.md` 15.

**Phase 8 checkpoint:** You can explain how prefetch and concurrency affect fairness, throughput and redelivery blast radius, with your own measurements.

---

## Phase 9 — Monitoring and Observability

### T-9.1 Correlation ID propagation
- **Module:** both · **Prereq:** T-3.5, T-2.10
- **Objective:** Trace one request across services (NFR-10).
- **Implement:** Producer puts `correlationId` in MDC and the message; consumer restores it to MDC for each delivery; retry copies keep it.
- **Expected:** One ID appears in all producer and consumer log lines for a request.
- **Test:** Submit with `X-Correlation-Id: trace-demo-1`; search both logs.
- **Acceptance:** Complete trace including retry attempts.
- **Docs:** `implementation.md` 14.

### T-9.2 Logging conventions and privacy check
- **Module:** both · **Prereq:** T-9.1
- **Objective:** Consistent, safe logs.
- **Implement:** Event names and fields from `implementation.md` 14; masking helper for recipients; never log message bodies.
- **Expected:** No raw recipient or message text in logs.
- **Test:** Automated test capturing logs for a full flow and asserting absence of the recipient and message text.
- **Acceptance:** Test green.
- **Docs:** `implementation.md` 14, 18.

### T-9.3 Health indicators
- **Module:** both · **Prereq:** T-0.5
- **Objective:** Meaningful health checks.
- **Implement:** Ensure RabbitMQ and DB indicators in `/actuator/health`.
- **Expected:** Stopping RabbitMQ or MySQL turns health `DOWN`.
- **Test:** Stop each container and call health.
- **Acceptance:** Correct `DOWN` and recovery to `UP`.
- **Docs:** `api.md` 6.

### T-9.4 Business metrics
- **Module:** both · **Prereq:** T-9.3
- **Objective:** Measure the system.
- **Implement:** Micrometer counters and timer listed in `implementation.md` 14.
- **Expected:** Counters change with traffic.
- **Test:** Submit a mix of outcomes; read `/actuator/metrics/<name>`.
- **Acceptance:** Each metric moves as expected.
- **Docs:** `implementation.md` 14.

### T-9.5 Management UI monitoring runbook
- **Module:** infra · **Prereq:** T-9.4
- **Objective:** FR-B-06 as a repeatable procedure.
- **Implement:** Write a checklist (in your notes or the repository wiki, not a sixth document) for queue depths, unacked, consumer counts, DLQ, connections, unroutable returns; practise on three scenarios.
- **Expected:** You diagnose each scenario from the UI alone.
- **Test:** A teammate or your future self sets up a failure; you name it from the UI.
- **Acceptance:** Three scenarios diagnosed correctly.
- **Docs:** `implementation.md` 14 (watch-list updates).

### T-9.6 Detect stuck `PENDING_PUBLISH` records
- **Module:** producer-service · **Prereq:** T-3.6
- **Objective:** Make the DB-write/publish gap visible (`OQ-02`, NFR-07).
- **Implement:** A repository query and a gauge metric for records in `PENDING_PUBLISH` older than a threshold (e.g. 5 minutes; configurable).
- **Expected:** Gauge is 0 normally and > 0 after simulating a crash between insert and publish.
- **Test:** Insert an old `PENDING_PUBLISH` row by hand; read the gauge.
- **Acceptance:** Gauge reflects the stuck record.
- **Docs:** `project.md` `OQ-02` status.

**Phase 9 checkpoint:** Any notification can be followed end to end by its correlation ID, and the main failure modes are visible without a debugger.

---

## Phase 10 — Integration Testing, Failure Experiments and Final Review

### T-10.1 End-to-end happy path for both channels
- **Module:** both · **Prereq:** T-9.4
- **Objective:** Full-system smoke test.
- **Implement:** Script or test: start infrastructure and both apps; POST an EMAIL and an SMS; poll `GET` until `SENT`; list with filters.
- **Expected:** Both reach `SENT`; list filters return them.
- **Test:** Run the script on a clean environment.
- **Acceptance:** Passes from a fresh clone following only the documentation.
- **Docs:** README-style quick start inside `project.md` if missing steps are found.

### T-10.2 Failure scenario suite (nine scenarios)
- **Module:** both · **Prereq:** T-10.1
- **Objective:** Automate or script every scenario in `implementation.md` 12.2.
- **Implement:** One test or documented experiment per scenario: (1) consumer offline, (2) crash mid-processing, (3) processing failure, (4) broker unavailable, (5) producer connection loss, (6) duplicate delivery, (7) broker restart, (8) concurrent consumers, (9) retries exhausted.
- **Expected:** Each ends in the state listed in the table.
- **Test:** Run the whole suite twice.
- **Acceptance:** Nine scenarios passing or evidenced.
- **Docs:** `implementation.md` 12.2 (adjust any wrong expectation).

### T-10.3 Failure experiment evidence log
- **Module:** infra · **Prereq:** T-10.2
- **Objective:** Evidence for NFR verification.
- **Implement:** For each NFR in `project.md` 11, record the verification you performed and its result (including measured numbers from Phase 8, labelled as measured).
- **Expected:** Every NFR has evidence or a stated gap.
- **Test:** Peer or self review against the "How we verify" column.
- **Acceptance:** No NFR without evidence.
- **Docs:** `project.md` 11 (adjust verification text).

### T-10.4 [Optional experiment] Quorum queues
- **Module:** consumer-service · **Prereq:** T-10.2
- **Objective:** Compare with classic queues (`PD-05`).
- **Implement:** Declare `email.queue` as a quorum queue on a copy of the topology (delete the old queue first); run T-4.4 and T-4.6 again; observe the delivery-limit setting.
- **Expected:** Behaviour matches, plus poison-message protection through the delivery limit.
- **Test:** Compare outcomes.
- **Acceptance:** Written comparison; decide whether to keep classic.
- **Docs:** `project.md` `PD-05`; `implementation.md` 5.4.

### T-10.5 Cross-document consistency review
- **Module:** docs · **Prereq:** T-10.2
- **Objective:** The five documents match the system.
- **Implement:** Check: every API in `api.md` exists; message fields identical in all files; exchange, queue and routing-key names identical; tables support all flows; retry behaviour in `flow.md` equals `implementation.md`; every `FR-*` has a task (section 4 below) and a test; no stale `PD`/`OQ` entries.
- **Expected:** Zero inconsistencies, or each one fixed.
- **Test:** Search each canonical name across files; run through each flow diagram against the code.
- **Acceptance:** Checklist signed off.
- **Docs:** All five.

### T-10.6 Final review and retrospective
- **Module:** all · **Prereq:** T-10.3, T-10.5
- **Objective:** Close the project.
- **Implement:** Re-read the objectives in `project.md` section 3; answer each with evidence; list future enhancements (outbox, `Idempotency-Key`, DLQ replay, tiered backoff, processing lease, retention).
- **Expected:** You can explain, without notes, why exactly-once processing is not a RabbitMQ guarantee.
- **Test:** Explain the nine scenarios aloud.
- **Acceptance:** All objectives answered; future list recorded; `mvn clean verify` green.
- **Docs:** `project.md` section 14 (actual outcome).

---

## 2. Definition of Done (every task)

1. Acceptance criteria met and demonstrated (test output or UI evidence).
2. All earlier tests still pass (`mvn clean verify`).
3. No new warnings about unconfigured properties or schema validation.
4. Documentation listed in the task updated, with names kept consistent.
5. Committed with the task ID.

## 3. Phase Exit Criteria Summary

| Phase | Exit criterion |
|---|---|
| 0 | Build green, infrastructure healthy, apps `UP`. |
| 1 | Exchange/queue/ack/durability concepts demonstrated by hand. |
| 2 | One message flows Producer → RabbitMQ → Consumer → MySQL. |
| 3 | Three APIs work; both channels independent; direct/topic/fanout demonstrated. |
| 4 | Manual acks; survives consumer crash and broker restart. |
| 5 | Retry and DLQ work; no infinite loops. |
| 6 | `202` only after broker confirm; failures give `503`. |
| 7 | Duplicates cannot produce a second outcome. |
| 8 | Concurrency and prefetch tuned and measured. |
| 9 | End-to-end tracing and metrics available. |
| 10 | Nine failure scenarios evidenced; documents consistent. |

## 4. Requirement Coverage Matrix

| Requirement | Tasks |
|---|---|
| FR-P-01 Submission | T-3.7 |
| FR-P-02 Validation | T-3.4, T-3.5 |
| FR-P-03 ID generation | T-3.3 |
| FR-P-04 Message creation | T-2.1, T-3.3 |
| FR-P-05 Routing key selection | T-3.3, T-3.14 |
| FR-P-06 Persistent publishing | T-2.10, T-4.5 |
| FR-P-07 Publisher confirms | T-6.1, T-6.2, T-6.4 |
| FR-P-08 Publish failure handling | T-6.3, T-6.6 |
| FR-P-09 Submission record | T-3.6, T-6.5 |
| FR-P-10 Status retrieval | T-3.8 |
| FR-P-11 Listing and filtering | T-3.9 |
| FR-C-01 Email consumer | T-2.8, T-2.14, T-4.1 |
| FR-C-02 SMS consumer | T-3.1, T-3.2, T-4.2 |
| FR-C-03 Simulated delivery | T-2.13, T-3.2 |
| FR-C-04 Status persistence | T-2.14, T-5.2, T-5.8, T-5.9 |
| FR-C-05 Attempt tracking | T-2.12, T-2.14, T-5.8, T-5.9 |
| FR-C-06 Manual ACK/NACK | T-4.1–T-4.4, T-5.2 |
| FR-C-07 Failure classification | T-5.3 |
| FR-C-08 Retry mechanism | T-5.4–T-5.8, T-6.7 |
| FR-C-09 DLQ handling | T-5.1, T-5.2, T-5.9 |
| FR-C-10 Duplicate protection | T-7.1–T-7.6 |
| FR-C-11 Concurrency and prefetch | T-8.1–T-8.5 |
| FR-C-12 Malformed messages | T-5.10 |
| FR-B-01 Direct routing | T-2.3–T-2.5, T-3.1, T-3.11, T-3.14 |
| FR-B-02 Topic scenario | T-3.12 |
| FR-B-03 Fanout scenario | T-3.13 |
| FR-B-04 Durable topology and DLX args | T-4.6, T-5.1 |
| FR-B-05 Retry topology | T-5.4–T-5.6 |
| FR-B-06 Management UI monitoring | T-1.1, T-9.5 |
| NFR-01…NFR-12 | T-4.x, T-5.x, T-6.x, T-8.x, T-9.x, T-10.2, T-10.3 |
