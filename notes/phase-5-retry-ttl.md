# Phase 5 — Watch a message expire and return (T-5.5)

1. In the Management UI, publish a `NotificationMessage` JSON to `notification.retry.exchange` with routing key `email.retry`.
2. Stop the email consumer (or set `lab.consumer.email` listener off) so the message is not consumed from `email.queue` when it returns.
3. Observe `email.retry.queue` **Ready** count = 1, then after ~`lab.retry.delay` (default 10 s) the message moves back to `email.queue` via the DLX → `notification.direct.exchange` → `notification.email` binding.
4. Note the routing key on the returning message matches `notification.email` (queue-level DLX routing key).