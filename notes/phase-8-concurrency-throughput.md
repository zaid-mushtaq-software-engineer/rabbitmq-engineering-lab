# Phase 8 — Concurrency 1, 2, 5 throughput (T-8.3)

*Measured on my machine — not an SLA.*

## Setup

- Fix `lab.consumer.email.prefetch=10`
- Set simulator latency for normal recipients to ~200 ms:

```yaml
lab:
  simulation:
    latency-min: 200ms
    latency-max: 200ms
```

- Publish **200** EMAIL messages (normal recipients) via producer API or script

## Measurements

Restart consumer between runs with these settings (`concurrency` = `max-concurrency`):

| concurrency | Time to drain 200 msgs (s) | Notes |
|-------------|----------------------------|-------|
| 1 | _fill in_ | baseline |
| 2 | _fill in_ | ~2× threads |
| 5 | _fill in_ | watch DB pool / CPU |

## Interpretation

Throughput should rise with concurrency until another bottleneck appears (Hikari pool size, CPU, or broker). **PD-10** defaults remain 2–4 consumers / prefetch 10 unless your numbers show a clear reason to change them—document here if you adjust defaults.

Watch **Queues → Consumers** in the UI while draining to confirm the active consumer count matches configuration.