# Phase 5 — Head-of-line blocking with per-message TTL (T-5.6)

1. Create throwaway queue `lab.retry.shared` (durable, DLX to a lab queue, no consumers).
2. Publish message A with per-message `expiration` 60000 ms, then message B with `expiration` 5000 ms.
3. Message B does **not** expire until message A expires — RabbitMQ only checks expiry at the head of the queue.
4. **Why the lab uses queue-level `x-message-ttl` on `email.retry.queue` / `sms.retry.queue`:** every message waits the same delay and the head always expires first.

Delete lab queues when finished.