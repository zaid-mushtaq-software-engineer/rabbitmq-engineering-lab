# Phase 8 — Prefetch 1 vs 10 experiment (T-8.2)

*Measured on my machine — not an SLA.*

## Setup

- `lab.consumer.email.concurrency=2`, `max-concurrency=2`
- Publish **40** messages to EMAIL: 20× `slow@example.com` (15 s simulated delay) mixed with 20× normal recipients
- Use producer API or `LabNotificationPublisherRunner` in batches

## Run A — prefetch 1

```bash
export LAB_CONSUMER_EMAIL_PREFETCH=1
```

Restart consumer. Record:

| Metric | Run A (prefetch 1) |
|--------|---------------------|
| Time to drain queue (s) | _fill in_ |
| Max Unacked on consumer 1 | _fill in_ |
| Max Unacked on consumer 2 | _fill in_ |
| Observed fairness (slow spread across consumers?) | _fill in_ |

## Run B — prefetch 10

```bash
export LAB_CONSUMER_EMAIL_PREFETCH=10
```

Repeat the same 40-message batch (purge queues between runs).

| Metric | Run B (prefetch 10) |
|--------|---------------------|
| Time to drain queue (s) | _fill in_ |
| Max Unacked on consumer 1 | _fill in_ |
| Max Unacked on consumer 2 | _fill in_ |
| Observed fairness | _fill in_ |

## What to expect (theory)

- **Prefetch 1:** broker sends one unacked message per consumer at a time → better **fair** split when some messages are slow; more round trips.
- **Prefetch 10:** one consumer can hoard up to 10 unacked messages → higher **throughput** when work is uniform, but a slow message can block other ready messages on that channel (hol blocking within the prefetch window).

See `documentation/implementation.md` section 11.5.