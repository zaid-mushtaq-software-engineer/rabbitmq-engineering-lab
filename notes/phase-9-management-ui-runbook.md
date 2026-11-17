# Phase 9 — Management UI monitoring runbook (T-9.5)

Broker UI: `http://localhost:15672` (lab user from `.env`).

## Watch each session

| Area | What to read |
|------|----------------|
| Queues | **Ready**, **Unacked**, **Total** per `email.queue`, `sms.queue`, `notification.retry.*`, `notification.dlq` |
| Consumers | Count and **prefetch** on each queue (should match `lab.consumer.*` in consumer `application.yml`) |
| Connections / Channels | Two apps → two connections; channel count scales with listener concurrency |
| Exchanges → returned | Unroutable publishes if mandatory/returns misconfigured |

## Scenario 1 — Consumer offline

1. Stop consumer (`8081` process) or disable email listener.
2. `POST` several EMAIL notifications via producer API.
3. **UI:** `email.queue` **Ready** rises; **Unacked** stays 0; no consumers on queue.
4. **Diagnosis:** backlog while consumer absent; producer `202` still works.

## Scenario 2 — Slow message / prefetch pile-up

1. Consumer running with `lab.consumer.email.prefetch=10`, concurrency 2.
2. Publish mix of `slow@example.com` and normal recipients.
3. **UI:** One consumer shows high **Unacked** while another may sit idle (head-of-line behind slow prefetch window).
4. Repeat with `prefetch=1` → **Unacked** spreads more evenly across consumers.

## Scenario 3 — Dead letter

1. `POST` with recipient `perm-fail@example.com`.
2. **UI:** `notification.dlq` depth **1**; primary email queue drains.
3. `GET /api/v1/notifications/{id}` → `DEAD_LETTERED`.

**Acceptance:** Each scenario identifiable from the UI without attaching a debugger.