# Phase 8 — Per-channel listener settings (T-8.1)

## Configure asymmetric EMAIL vs SMS

Set environment variables before starting the consumer (or override in `application-local.yml`):

```bash
export LAB_CONSUMER_EMAIL_CONCURRENCY=3
export LAB_CONSUMER_EMAIL_MAX_CONCURRENCY=3
export LAB_CONSUMER_EMAIL_PREFETCH=5
export LAB_CONSUMER_SMS_CONCURRENCY=1
export LAB_CONSUMER_SMS_MAX_CONCURRENCY=1
export LAB_CONSUMER_SMS_PREFETCH=1
```

Spring maps these to `lab.consumer.email.*` / `lab.consumer.sms.*` (see `application.yml`).

Start broker + MySQL (`docker compose up -d` in `docker/`), then run the consumer on port 8081.

## Verify in Management UI

1. Open http://localhost:15672 → **Queues**.
2. **`email.queue`** → **Consumers** tab: expect **3** consumer channels with **prefetch 5** each (QoS).
3. **`sms.queue`** → **Consumers** tab: expect **1** consumer with **prefetch 1**.

Automated check: `ConsumerListenerConfigTest` asserts the same values on the Spring listener containers.

## Property reference

| Property | EMAIL example | SMS example |
|----------|---------------|-------------|
| `lab.consumer.<ch>.concurrency` | 3 | 1 |
| `lab.consumer.<ch>.max-concurrency` | 3 | 1 |
| `lab.consumer.<ch>.prefetch` | 5 | 1 |