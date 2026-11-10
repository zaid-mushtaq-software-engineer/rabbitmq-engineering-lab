# Phase 3 — Direct exchange lab notes (T-3.11)

Manual checks via RabbitMQ Management UI (`http://localhost:15672`):

1. Publish to `notification.direct.exchange` with routing key `notification.email` → message appears only in `email.queue`.
2. Same with `notification.sms` → only `sms.queue`.
3. Routing keys are case-sensitive (`Notification.email` does not match).
4. Key `notification.push` has no binding; without mandatory return handling in Phase 3 the message may be dropped (mandatory + returns arrive in Phase 6).
