#!/usr/bin/env bash
# T-10.1 — End-to-end smoke: Producer (8080) + Consumer (8081) + Docker infra.
#
# Prerequisites:
#   1. docker compose -f docker/docker-compose.yml up -d  (healthy rabbitmq + mysql)
#   2. Copy .env.example to .env and adjust if needed
#   3. Start consumer:  mvn -pl consumer-service spring-boot:run   (port 8081, profile local)
#   4. Start producer:  mvn -pl producer-service spring-boot:run    (port 8080, profile local)
#   Start order: infrastructure → consumer → producer (queues must exist before publish)
#
# Requires: curl, jq

set -euo pipefail

PRODUCER_URL="${PRODUCER_URL:-http://localhost:8080}"
CONSUMER_URL="${CONSUMER_URL:-http://localhost:8081}"
POLL_TIMEOUT_SEC="${POLL_TIMEOUT_SEC:-60}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-2}"

die() {
  echo "e2e-smoke: $*" >&2
  exit 1
}

command -v curl >/dev/null 2>&1 || die "curl is required"
command -v jq >/dev/null 2>&1 || die "jq is required"

health_up() {
  local base="$1"
  local name="$2"
  local status
  status="$(curl -sf "${base}/actuator/health" | jq -r '.status // empty')"
  [[ "${status}" == "UP" ]] || die "${name} health expected UP, got: ${status:-<missing>}"
}

echo "Checking actuator health..."
health_up "${PRODUCER_URL}" "producer"
health_up "${CONSUMER_URL}" "consumer"

submit() {
  local channel="$1"
  local recipient="$2"
  local corr="$3"
  curl -sf -X POST "${PRODUCER_URL}/api/v1/notifications" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: ${corr}" \
    -d "{\"channel\":\"${channel}\",\"recipient\":\"${recipient}\",\"message\":\"e2e-smoke\"}"
}

echo "Submitting EMAIL..."
email_resp="$(submit EMAIL "alice@example.com" "e2e-smoke-email")"
email_id="$(echo "${email_resp}" | jq -r '.notificationId')"
[[ -n "${email_id}" && "${email_id}" != "null" ]] || die "EMAIL submit missing notificationId"
echo "  notificationId=${email_id}"

echo "Submitting SMS..."
sms_resp="$(submit SMS "+15550100111" "e2e-smoke-sms")"
sms_id="$(echo "${sms_resp}" | jq -r '.notificationId')"
[[ -n "${sms_id}" && "${sms_id}" != "null" ]] || die "SMS submit missing notificationId"
echo "  notificationId=${sms_id}"

wait_sent() {
  local id="$1"
  local label="$2"
  local elapsed=0
  while (( elapsed < POLL_TIMEOUT_SEC )); do
    local status
    status="$(curl -sf "${PRODUCER_URL}/api/v1/notifications/${id}" | jq -r '.status')"
    if [[ "${status}" == "SENT" ]]; then
      echo "  ${label} reached SENT (${elapsed}s)"
      return 0
    fi
    sleep "${POLL_INTERVAL_SEC}"
    elapsed=$((elapsed + POLL_INTERVAL_SEC))
  done
  die "${label} did not reach SENT within ${POLL_TIMEOUT_SEC}s"
}

echo "Polling until SENT..."
wait_sent "${email_id}" "EMAIL"
wait_sent "${sms_id}" "SMS"

echo "Listing EMAIL SENT..."
email_list="$(curl -sf "${PRODUCER_URL}/api/v1/notifications?channel=EMAIL&status=SENT&size=100")"
echo "${email_list}" | jq -e --arg id "${email_id}" '.content[] | select(.notificationId == $id)' >/dev/null \
  || die "EMAIL list filter did not contain ${email_id}"

echo "Listing SMS SENT..."
sms_list="$(curl -sf "${PRODUCER_URL}/api/v1/notifications?channel=SMS&status=SENT&size=100")"
echo "${sms_list}" | jq -e --arg id "${sms_id}" '.content[] | select(.notificationId == $id)' >/dev/null \
  || die "SMS list filter did not contain ${sms_id}"

echo "e2e-smoke: OK (EMAIL and SMS SENT, list filters matched)"