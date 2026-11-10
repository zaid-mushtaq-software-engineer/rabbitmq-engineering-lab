# Phase 1 — RabbitMQ fundamentals (personal notes)

UI: http://localhost:15672 (`lab_app` / see `.env.example`).

## T-1.1 — Management UI tour

- **Connection:** TCP session from client to broker (one per app process).
- **Channel:** Lightweight virtual connection inside a connection; AMQP commands run on channels.
- **Ready / Unacked / Total:** On the **Queues** tab, select a queue — message counts show **Ready** (delivered to queue, not yet to consumer or not yet acked), **Unacked** (delivered to consumer, not acknowledged), **Total** = Ready + Unacked.
- **Default exchange:** Name `""`, type `direct`; routing key must equal queue name for point-to-point publish.
- **Built-in types:** `amq.direct`, `amq.topic`, `amq.fanout`, `amq.headers` (and `amq.match`).

## T-1.2 — Default exchange

- Durable queue `lab.hello.queue`; publish with routing key = queue name.
- **Get messages** with *Nack requeue true* → message returns to queue → **Ready** stays 1.
- **Get messages** with *Automatic ack* → message removed → **Ready** 0.

## T-1.3 — Direct exchange `lab.direct`

| Routing key | lab.a | lab.b |
|-------------|-------|-------|
| key-a | 1 | 0 |
| key-b | 0 | 1 |
| key-c | 0 | 0 (unroutable) |

## T-1.4 — Durability vs persistence (after broker restart)

| Queue durable | Message persistent | Survives restart |
|---------------|-------------------|------------------|
| yes | yes | yes |
| yes | no | queue yes, message no |
| no | yes | queue gone |
| no | no | queue gone |

Cleanup: delete all `lab.*` exchanges, queues, and bindings when experiments end.
