# Phase 4 — Requeue loop experiment (T-4.3)

**Goal:** See infinite redelivery when a listener throws and the container requeues by default.

1. Temporarily change [`ChannelNotificationListenerSupport`](../consumer-service/src/main/java/com/example/notificationlab/consumer/listener/ChannelNotificationListenerSupport.java) to `throw new RuntimeException("lab-requeue-test")` before processing (do not commit this).
2. Publish one message to `email.queue` via Management UI or the lab runner.
3. Watch **Redelivered** and logs — delivery count climbs rapidly (hot loop).

**Safe default (keep in code):** unexpected errors map to `basicNack(..., requeue=false)`. Without a DLX (Phase 5), the message is dropped instead of looping.

Revert any throw-after-experiment before committing.
