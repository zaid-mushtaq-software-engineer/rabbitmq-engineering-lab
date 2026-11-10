# RabbitMQ Engineering Lab — REST API Contract

> **Owner:** `producer-service` (port 8080)
> **Related:** `project.md` (requirements FR-P-01 to FR-P-11), `implementation.md` (how it is built), `flow.md` (flows 1, 2, 15)

This document is detailed enough to implement and test the APIs without guessing. Decisions not fixed by the original requirements are marked **Proposed**.

---

## 1. Core Principle: Accepted Is Not Delivered

The API is asynchronous. Three different facts must never be confused:

| Fact | Meaning | Where the client sees it |
|---|---|---|
| **Accepted by the API** | The request passed validation and was stored. | `POST` returned a response. |
| **Accepted by RabbitMQ** | The broker confirmed it took responsibility for the message. Status `QUEUED`. | `POST` returns `202` with `status: QUEUED`. |
| **Delivered** | A consumer processed the message and the (simulated) delivery succeeded. Status `SENT`. | Only visible later via `GET`. |

`POST` never returns `SENT`. A response of `202 Accepted` means *"queued for asynchronous processing"* only.

## 2. API Conventions

| Topic | Convention |
|---|---|
| Base path | `/api/v1` |
| Format | JSON, UTF-8. Request `Content-Type: application/json`. |
| Property naming | `camelCase`. |
| Identifiers | UUID strings (lowercase, hyphenated). |
| Timestamps | ISO-8601 in UTC with milliseconds and `Z`, e.g. `2026-11-09T10:15:30.123Z`. |
| Enumerations | Uppercase, case-sensitive: `EMAIL`, `SMS`; statuses listed in section 3. |
| Null handling | Fields with no value are returned as `null` (not omitted), except where stated. |
| Unknown JSON properties in requests | Rejected with `400 INVALID_REQUEST` (**Proposed**: strict contract). |
| Unknown query parameters | Ignored (**Proposed**). |
| Versioning | Path-based (`/v1`). Breaking changes require `/v2`. |
| Authentication | None (out of scope). |
| API documentation | OpenAPI 3 document at `/v3/api-docs`, Swagger UI at `/swagger-ui.html` (enabled in the `local` profile only). |

### 2.1 Common Request Headers

| Header | Applies to | Required | Description |
|---|---|---|---|
| `Content-Type: application/json` | `POST` | Yes | Otherwise `415 UNSUPPORTED_MEDIA_TYPE`. |
| `Accept: application/json` | All | No | Responses are always JSON. |
| `X-Correlation-Id` | All | No | 1–64 characters from `A-Z a-z 0-9 - _ .`. If absent, the server generates one. If present but malformed, `400 VALIDATION_FAILED`. |

### 2.2 Common Response Headers

| Header | Description |
|---|---|
| `Content-Type: application/json` | Always. |
| `X-Correlation-Id` | The effective correlation ID. Same value is stored in `notifications.correlation_id`, placed in the message and written to every log line. |
| `Location` | On `202` from `POST`: `/api/v1/notifications/{notificationId}`. |
| `Retry-After` | On `503`: suggested seconds to wait (**Proposed** default `5`). |

### 2.3 Notification Status Values

| Status | Visible to clients as | Final? |
|---|---|---|
| `PENDING_PUBLISH` | Stored, broker confirmation not yet received (normally brief; visible only if the Producer crashed or is mid-request). | No |
| `QUEUED` | Broker confirmed receipt. Not delivered. | No |
| `PUBLISH_FAILED` | Producer could not get a broker confirmation. | Yes (client may resubmit) |
| `PROCESSING` | A consumer is processing an attempt. | No |
| `RETRY_SCHEDULED` | Temporary failure; waiting for the next attempt. | No |
| `SENT` | Simulated delivery succeeded. | Yes |
| `DEAD_LETTERED` | Permanent failure or retries exhausted. | Yes |

### 2.4 Standard Error Response

All errors use this structure.

| Field | Type | Description |
|---|---|---|
| `timestamp` | string | Time of the error (UTC). |
| `status` | integer | HTTP status code. |
| `error` | string | HTTP reason phrase, e.g. `Bad Request`. |
| `code` | string | Stable machine-readable error code (section 2.5). |
| `message` | string | Human-readable summary. Never contains stack traces or internal details. |
| `path` | string | Request path. |
| `correlationId` | string | Effective correlation ID. |
| `details` | array | Zero or more `{ "field": string or null, "reason": string }` entries. Empty array when not applicable. |
| `notificationId` | string | **Optional.** Present only when a notification record was already created (e.g. `MESSAGE_PUBLISH_FAILED`). |

```json
{
  "timestamp": "2026-11-09T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/notifications",
  "correlationId": "req-20261109-0002",
  "details": [
    { "field": "recipient", "reason": "must not be blank" }
  ]
}
```

### 2.5 Error Code Catalogue

| Code | HTTP | Meaning |
|---|---|---|
| `INVALID_REQUEST` | 400 | Body missing, not valid JSON, wrong JSON types, or unknown properties. |
| `VALIDATION_FAILED` | 400 | One or more fields failed validation (includes missing recipient, bad path ID, bad header). |
| `UNSUPPORTED_CHANNEL` | 400 | `channel` is present but not `EMAIL` or `SMS`. |
| `INVALID_PAGINATION` | 400 | `page`, `size` or `sort` is invalid. |
| `INVALID_FILTER` | 400 | A filter value is invalid (status, channel, dates). |
| `NOTIFICATION_NOT_FOUND` | 404 | No notification with that ID. |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported on the path. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` is not `application/json`. |
| `MESSAGE_PUBLISH_FAILED` | 503 | The message was not confirmed by RabbitMQ (broker unavailable, nack, confirm timeout, or unroutable). |
| `INTERNAL_ERROR` | 500 | Unexpected server error (e.g. database failure before publishing). |

### 2.6 Status Code Summary

| Code | Used for |
|---|---|
| `200 OK` | Successful `GET`. |
| `202 Accepted` | Successful `POST` (queued, not delivered). |
| `400 Bad Request` | Validation, parsing, pagination or filter errors. |
| `404 Not Found` | Unknown notification ID. |
| `405 Method Not Allowed` | Unsupported method. |
| `415 Unsupported Media Type` | Wrong content type. |
| `500 Internal Server Error` | Unexpected failure. |
| `503 Service Unavailable` | Message publishing failed. |

---

## 3. POST /api/v1/notifications — Submit a Notification

**Purpose:** Accept a notification request and publish it to RabbitMQ for asynchronous processing.

| Item | Value |
|---|---|
| Method / endpoint | `POST /api/v1/notifications` |
| Path parameters | None |
| Query parameters | None |
| Request headers | `Content-Type: application/json` (required), `X-Correlation-Id` (optional) |

### 3.1 Request Body

| Field | Type | Required | Rules |
|---|---|---|---|
| `channel` | string | Yes | Must be exactly `EMAIL` or `SMS`. |
| `recipient` | string | Yes | Not blank. For `EMAIL`: valid email address, max 254 characters. For `SMS`: E.164 number (`+` followed by 7–15 digits, first digit not `0`). Leading/trailing whitespace is **not** trimmed; it causes a validation error. |
| `message` | string | Yes | Not blank; 1–1000 characters after trimming. |

### 3.2 Validation Order

1. Content type and JSON parsing → `415` / `400 INVALID_REQUEST`.
2. `channel` present → else `400 VALIDATION_FAILED`; value supported → else `400 UNSUPPORTED_CHANNEL`.
3. `recipient` and `message` field rules (channel-specific recipient rule applied after the channel is known) → `400 VALIDATION_FAILED` with *all* field failures listed in `details`.

If `channel` itself is invalid, only `UNSUPPORTED_CHANNEL` is returned (recipient format cannot be judged without a channel).

### 3.3 Business Rules

1. Nothing is published unless validation passes.
2. The Producer creates the `notifications` row with status `PENDING_PUBLISH` **before** publishing (`PD-01`).
3. The message is published as persistent, with the mandatory flag, to `notification.direct.exchange` using routing key `notification.email` or `notification.sms`.
4. The Producer waits for the broker confirm (default timeout 5 s, `PD-08`).
5. Confirm ack → status `QUEUED` → `202`.
6. Nack, timeout, broker unavailable or unroutable return → status `PUBLISH_FAILED` → `503 MESSAGE_PUBLISH_FAILED`.
7. If the broker confirm succeeded but the status update to `QUEUED` fails, the API still returns `202` (the message is safely queued; the Consumer will advance the status) and logs a warning.
8. The response never contains `SENT` or any delivery claim.

### 3.4 Successful Response — `202 Accepted`

Headers: `Location: /api/v1/notifications/7c9e6679-7425-40de-944b-e07fc1f90ae7`, `X-Correlation-Id: req-20261109-0001`

| Field | Type | Description |
|---|---|---|
| `notificationId` | string | ID to use with `GET`. |
| `messageId` | string | ID of the RabbitMQ message. |
| `channel` | string | Echo of the channel. |
| `status` | string | Always `QUEUED` on success. |
| `correlationId` | string | Effective correlation ID. |
| `acceptedAt` | string | Time the broker confirmed the message. |
| `detail` | string | Fixed text: `Notification accepted for asynchronous processing. Delivery has not yet been attempted.` |

**Example request**

```http
POST /api/v1/notifications HTTP/1.1
Content-Type: application/json
X-Correlation-Id: req-20261109-0001

{
  "channel": "EMAIL",
  "recipient": "alice@example.com",
  "message": "Your order #1042 has shipped."
}
```

**Example response**

```json
{
  "notificationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "messageId": "0b8f4e2a-5d3c-4c1e-9a7f-2f6d1c8e4b90",
  "channel": "EMAIL",
  "status": "QUEUED",
  "correlationId": "req-20261109-0001",
  "acceptedAt": "2026-11-09T10:15:30.456Z",
  "detail": "Notification accepted for asynchronous processing. Delivery has not yet been attempted."
}
```

### 3.5 Error Scenarios

| Scenario | HTTP | Code | Example `details` |
|---|---|---|---|
| Body missing or malformed JSON | 400 | `INVALID_REQUEST` | `[{"field": null, "reason": "Request body is missing or not valid JSON"}]` |
| Unknown property in body | 400 | `INVALID_REQUEST` | `[{"field": "priority", "reason": "unknown property"}]` |
| `channel` missing | 400 | `VALIDATION_FAILED` | `[{"field": "channel", "reason": "must not be null"}]` |
| `channel` is `PUSH` or lowercase `email` | 400 | `UNSUPPORTED_CHANNEL` | `[{"field": "channel", "reason": "must be one of EMAIL, SMS"}]` |
| `recipient` missing or blank | 400 | `VALIDATION_FAILED` | `[{"field": "recipient", "reason": "must not be blank"}]` |
| `recipient` not a valid email for `EMAIL` | 400 | `VALIDATION_FAILED` | `[{"field": "recipient", "reason": "must be a valid email address"}]` |
| `recipient` not E.164 for `SMS` | 400 | `VALIDATION_FAILED` | `[{"field": "recipient", "reason": "must be in E.164 format, e.g. +15550100099"}]` |
| `message` blank or over 1000 characters | 400 | `VALIDATION_FAILED` | `[{"field": "message", "reason": "length must be between 1 and 1000"}]` |
| Malformed `X-Correlation-Id` | 400 | `VALIDATION_FAILED` | `[{"field": "X-Correlation-Id", "reason": "must match [A-Za-z0-9._-]{1,64}"}]` |
| Content type not JSON | 415 | `UNSUPPORTED_MEDIA_TYPE` | `[]` |
| Broker unavailable, nack, timeout or unroutable | 503 | `MESSAGE_PUBLISH_FAILED` | `[{"field": null, "reason": "Broker did not confirm the message within 5000 ms"}]` plus `notificationId` |
| Database failure before publish | 500 | `INTERNAL_ERROR` | `[]` |

**Example — validation error (two failures reported together)**

```json
{
  "timestamp": "2026-11-09T10:16:02.001Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/notifications",
  "correlationId": "req-20261109-0002",
  "details": [
    { "field": "recipient", "reason": "must be in E.164 format, e.g. +15550100099" },
    { "field": "message", "reason": "length must be between 1 and 1000" }
  ]
}
```

**Example — unsupported channel**

```json
{
  "timestamp": "2026-11-09T10:16:40.310Z",
  "status": 400,
  "error": "Bad Request",
  "code": "UNSUPPORTED_CHANNEL",
  "message": "Channel 'PUSH' is not supported.",
  "path": "/api/v1/notifications",
  "correlationId": "req-20261109-0003",
  "details": [
    { "field": "channel", "reason": "must be one of EMAIL, SMS" }
  ]
}
```

**Example — publishing failure** (`Retry-After: 5`)

```json
{
  "timestamp": "2026-11-09T10:17:11.870Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "MESSAGE_PUBLISH_FAILED",
  "message": "The notification could not be queued. It was not accepted for processing.",
  "path": "/api/v1/notifications",
  "correlationId": "req-20261109-0004",
  "details": [
    { "field": null, "reason": "Broker did not confirm the message within 5000 ms" }
  ],
  "notificationId": "0f3c1a52-91de-4f84-8a0d-6c7a2b1e9d11"
}
```

> **Important caveat.** A confirm *timeout* is ambiguous: the broker may still have accepted the message. The notification's record is set to `PUBLISH_FAILED`, but if the message was in fact queued, the Consumer will process it and move the status forward. A client that resubmits after a `503` can therefore cause two notifications. An `Idempotency-Key` header would remove this risk (**Future**, `OQ-05`).

---

## 4. GET /api/v1/notifications/{id} — Get Notification Status

**Purpose:** Return the current state and processing history of one notification.

| Item | Value |
|---|---|
| Method / endpoint | `GET /api/v1/notifications/{id}` |
| Request headers | `X-Correlation-Id` (optional) |
| Query parameters | None |
| Request body | None |

### 4.1 Path Parameter

| Name | Type | Rules |
|---|---|---|
| `id` | UUID string | Must be a valid UUID, else `400 VALIDATION_FAILED`. This is the `notificationId`. |

### 4.2 Business Rules

1. Data is read from the `notifications` and `notification_attempts` tables.
2. The response reflects the **last committed** state. Because processing is asynchronous, status may change between two calls.
3. `attempts` is ordered by `attemptNumber` ascending and is empty until the first attempt finishes.
4. A notification created by `POST` is immediately retrievable (status `PENDING_PUBLISH` or `QUEUED`), even if no consumer is running (`PD-01`).
5. `recipient` and `message` are returned as stored (no authentication in scope; mask them in any production variant).

### 4.3 Successful Response — `200 OK`

| Field | Type | Description |
|---|---|---|
| `notificationId`, `messageId` | string | Identifiers. |
| `channel` | string | `EMAIL` or `SMS`. |
| `recipient`, `message` | string | Stored request values. |
| `status` | string | See section 2.3. |
| `statusDescription` | string | Human text, e.g. `Queued. Delivery has not yet been attempted.` for `QUEUED`; `Delivered (simulated).` for `SENT`. |
| `retryCount` | integer | Number of retries scheduled so far. |
| `attemptCount` | integer | Number of finished processing attempts. |
| `lastError` | string or null | Last failure reason. |
| `correlationId` | string | Correlation ID. |
| `createdAt`, `updatedAt` | string | Timestamps. |
| `queuedAt` | string or null | When the broker confirmed. |
| `completedAt` | string or null | When a final status was reached (`SENT`, `DEAD_LETTERED`, `PUBLISH_FAILED`). |
| `attempts` | array | Items: `attemptNumber`, `consumerName`, `outcome` (`SUCCESS`, `TEMPORARY_FAILURE`, `PERMANENT_FAILURE`), `errorCode`, `errorMessage`, `redelivered`, `startedAt`, `finishedAt`. |

**Example — a notification that succeeded after two temporary failures**

```json
{
  "notificationId": "5d1f2c9a-3b7e-4a64-8e0b-9c4d7a21f3aa",
  "messageId": "a41e7d02-6c55-4b1d-b0aa-3d2f8e9c7b10",
  "channel": "EMAIL",
  "recipient": "flaky@example.com",
  "message": "Welcome aboard!",
  "status": "SENT",
  "statusDescription": "Delivered (simulated).",
  "retryCount": 2,
  "attemptCount": 3,
  "lastError": null,
  "correlationId": "req-20261109-0005",
  "createdAt": "2026-11-09T10:20:00.100Z",
  "updatedAt": "2026-11-09T10:20:21.940Z",
  "queuedAt": "2026-11-09T10:20:00.180Z",
  "completedAt": "2026-11-09T10:20:21.940Z",
  "attempts": [
    { "attemptNumber": 1, "consumerName": "email-consumer", "outcome": "TEMPORARY_FAILURE", "errorCode": "SIMULATED_TIMEOUT", "errorMessage": "Simulated provider timeout", "redelivered": false, "startedAt": "2026-11-09T10:20:00.300Z", "finishedAt": "2026-11-09T10:20:00.620Z" },
    { "attemptNumber": 2, "consumerName": "email-consumer", "outcome": "TEMPORARY_FAILURE", "errorCode": "SIMULATED_TIMEOUT", "errorMessage": "Simulated provider timeout", "redelivered": false, "startedAt": "2026-11-09T10:20:10.700Z", "finishedAt": "2026-11-09T10:20:11.020Z" },
    { "attemptNumber": 3, "consumerName": "email-consumer", "outcome": "SUCCESS", "errorCode": null, "errorMessage": null, "redelivered": false, "startedAt": "2026-11-09T10:20:21.100Z", "finishedAt": "2026-11-09T10:20:21.940Z" }
  ]
}
```

**Example — just after submission (no attempts yet)**

```json
{
  "notificationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "messageId": "0b8f4e2a-5d3c-4c1e-9a7f-2f6d1c8e4b90",
  "channel": "EMAIL",
  "recipient": "alice@example.com",
  "message": "Your order #1042 has shipped.",
  "status": "QUEUED",
  "statusDescription": "Queued. Delivery has not yet been attempted.",
  "retryCount": 0,
  "attemptCount": 0,
  "lastError": null,
  "correlationId": "req-20261109-0001",
  "createdAt": "2026-11-09T10:15:30.123Z",
  "updatedAt": "2026-11-09T10:15:30.456Z",
  "queuedAt": "2026-11-09T10:15:30.456Z",
  "completedAt": null,
  "attempts": []
}
```

### 4.4 Error Scenarios

| Scenario | HTTP | Code |
|---|---|---|
| `id` is not a UUID | 400 | `VALIDATION_FAILED` (`details[0].field = "id"`) |
| No notification with that ID | 404 | `NOTIFICATION_NOT_FOUND` |
| Database failure | 500 | `INTERNAL_ERROR` |

```json
{
  "timestamp": "2026-11-09T10:25:00.000Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOTIFICATION_NOT_FOUND",
  "message": "Notification 11111111-2222-3333-4444-555555555555 was not found.",
  "path": "/api/v1/notifications/11111111-2222-3333-4444-555555555555",
  "correlationId": "req-20261109-0006",
  "details": []
}
```

---

## 5. GET /api/v1/notifications — List Notifications

**Purpose:** Return a paginated, filterable list of notifications for operational overview.

| Item | Value |
|---|---|
| Method / endpoint | `GET /api/v1/notifications` |
| Path parameters | None |
| Request headers | `X-Correlation-Id` (optional) |
| Request body | None |

### 5.1 Query Parameters

| Parameter | Type | Default | Rules |
|---|---|---|---|
| `status` | enum | none | One of the statuses in section 2.3. Invalid → `400 INVALID_FILTER`. |
| `channel` | enum | none | `EMAIL` or `SMS`. Invalid → `400 INVALID_FILTER`. |
| `createdFrom` | ISO-8601 instant | none | Inclusive lower bound on `createdAt`. Invalid → `400 INVALID_FILTER`. |
| `createdTo` | ISO-8601 instant | none | Exclusive upper bound on `createdAt`. Must be after `createdFrom` if both are given, else `400 INVALID_FILTER`. |
| `page` | integer | `0` | Zero-based. Must be ≥ 0. |
| `size` | integer | `20` | 1 to 100. |
| `sort` | string | `createdAt,desc` | Format `field,direction`. Fields: `createdAt`, `updatedAt`. Direction: `asc` or `desc`. A stable tie-breaker on `id` is always applied. |

Filters combine with logical AND. There is no filter on `recipient` (avoids turning the API into a personal-data search). Any invalid `page`, `size` or `sort` gives `400 INVALID_PAGINATION`.

### 5.2 Business Rules

1. An empty result is `200 OK` with `content: []` and `totalElements: 0`.
2. `page` beyond the last page returns `200` with an empty `content` and correct totals.
3. Items are summaries: no `attempts` array (use `GET /{id}` for history).
4. Because processing is asynchronous, a list is a point-in-time snapshot.

### 5.3 Successful Response — `200 OK`

| Field | Type | Description |
|---|---|---|
| `content` | array | Items: `notificationId`, `channel`, `recipient`, `status`, `retryCount`, `attemptCount`, `lastError`, `createdAt`, `updatedAt`. |
| `page`, `size` | integer | Effective values. |
| `totalElements`, `totalPages` | integer | Totals for the filter. |
| `first`, `last` | boolean | Position flags. |

**Example request**

```http
GET /api/v1/notifications?status=DEAD_LETTERED&channel=EMAIL&page=0&size=2&sort=createdAt,desc HTTP/1.1
```

**Example response**

```json
{
  "content": [
    {
      "notificationId": "9a2b6c10-4e8d-41f7-8f33-0b5d6e7f8a21",
      "channel": "EMAIL",
      "recipient": "temp-fail@example.com",
      "status": "DEAD_LETTERED",
      "retryCount": 3,
      "attemptCount": 4,
      "lastError": "TEMPORARY_FAILURE: retries exhausted after 4 attempts",
      "createdAt": "2026-11-09T10:30:00.000Z",
      "updatedAt": "2026-11-09T10:30:32.500Z"
    },
    {
      "notificationId": "3c7d8e90-1a2b-4c5d-9e6f-7a8b9c0d1e2f",
      "channel": "EMAIL",
      "recipient": "perm-fail@example.com",
      "status": "DEAD_LETTERED",
      "retryCount": 0,
      "attemptCount": 1,
      "lastError": "PERMANENT_FAILURE: recipient rejected by provider (simulated)",
      "createdAt": "2026-11-09T10:28:10.000Z",
      "updatedAt": "2026-11-09T10:28:10.400Z"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### 5.4 Error Scenarios

| Scenario | HTTP | Code | Example `details` |
|---|---|---|---|
| `page=-1` or `page=abc` | 400 | `INVALID_PAGINATION` | `[{"field": "page", "reason": "must be an integer >= 0"}]` |
| `size=0` or `size=500` | 400 | `INVALID_PAGINATION` | `[{"field": "size", "reason": "must be between 1 and 100"}]` |
| `sort=recipient,asc` or `sort=createdAt,up` | 400 | `INVALID_PAGINATION` | `[{"field": "sort", "reason": "allowed: createdAt, updatedAt with asc or desc"}]` |
| `status=DONE` | 400 | `INVALID_FILTER` | `[{"field": "status", "reason": "must be one of PENDING_PUBLISH, QUEUED, ..."}]` |
| `channel=PUSH` | 400 | `INVALID_FILTER` | `[{"field": "channel", "reason": "must be one of EMAIL, SMS"}]` |
| `createdTo` earlier than `createdFrom` | 400 | `INVALID_FILTER` | `[{"field": "createdTo", "reason": "must be after createdFrom"}]` |
| Database failure | 500 | `INTERNAL_ERROR` | `[]` |

---

## 6. Operational Endpoints (not part of the business API)

These support observability (NFR-10). They are on the same ports as the applications.

| Application | Endpoint | Purpose |
|---|---|---|
| producer-service | `/actuator/health` | Liveness and readiness including RabbitMQ and MySQL connectivity. |
| producer-service | `/actuator/metrics` | Micrometer metrics. |
| producer-service | `/v3/api-docs`, `/swagger-ui.html` | OpenAPI document and UI (local profile only). |
| consumer-service (8081) | `/actuator/health`, `/actuator/metrics` | Same. The Consumer exposes **no business API**. |

Actuator endpoints other than `health` and `metrics` stay disabled (**Proposed**).

## 7. Reserved Test Recipients (simulation)

Delivery is simulated and driven by the recipient value (`PD-12`; detail in `implementation.md` section 10). These values pass validation and make examples and tests reproducible.

| Channel | Recipient | Simulated behaviour |
|---|---|---|
| EMAIL | any normal address, e.g. `alice@example.com` | Succeeds after a short simulated latency. |
| EMAIL | `temp-fail@example.com` | Every attempt fails temporarily → retries exhausted → `DEAD_LETTERED`. |
| EMAIL | `flaky@example.com` | First two attempts fail temporarily, third succeeds → `SENT`. |
| EMAIL | `perm-fail@example.com` | Permanent failure on first attempt → `DEAD_LETTERED`. |
| EMAIL | `slow@example.com` | Succeeds after about 15 seconds (used for crash and concurrency experiments). |
| SMS | `+15550100099` (or any other valid number) | Succeeds after a short simulated latency. |
| SMS | `+15550100001` / `+15550100002` / `+15550100003` / `+15550100004` | Temporary-always-fail / flaky / permanent failure / slow, matching the EMAIL rows above in that order. |

## 8. Implementation Checklist for the API

- [ ] Every response (including errors) carries `X-Correlation-Id`.
- [ ] Every error uses the structure in section 2.4 and a code from section 2.5.
- [ ] `POST` never returns a status other than `QUEUED` on success.
- [ ] Validation reports all field errors in one response.
- [ ] `GET` of a freshly submitted notification never returns `404`.
- [ ] No stack traces, SQL or broker details in error bodies.
- [ ] OpenAPI document matches this file.
