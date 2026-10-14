#!/bin/sh
set -eu

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$DIR"

if [ ! -f .env ]; then
  echo "Missing $DIR/.env" >&2
  exit 1
fi

set -a
. ./.env
set +a

HTTP_PORT=${LEGIT_ASSISTANT_HTTP_PORT:-18080}
BASE="http://127.0.0.1:${HTTP_PORT}"

echo "=== containers ==="
docker compose --env-file .env ps

echo
echo "=== liveness ==="
LIVE_FILE=$(mktemp)
LIVE_ERR=$(mktemp)
trap 'rm -f "$LIVE_FILE" "$LIVE_ERR" "${READY_FILE:-}"' EXIT HUP INT TERM

LIVE_OK=0
ATTEMPT=1
MAX_ATTEMPTS=${LEGIT_ASSISTANT_FIRST_LIVE_LIVENESS_ATTEMPTS:-45}
while [ "$ATTEMPT" -le "$MAX_ATTEMPTS" ]; do
  : >"$LIVE_FILE"
  : >"$LIVE_ERR"
  if curl --fail --silent --show-error --connect-timeout 2 --max-time 3 \
      --output "$LIVE_FILE" "$BASE/health/live" 2>"$LIVE_ERR"; then
    LIVE_OK=1
    break
  fi

  printf 'waiting for Assistant liveness (%s/%s)...\n' "$ATTEMPT" "$MAX_ATTEMPTS"
  ATTEMPT=$((ATTEMPT + 1))
  sleep 2
done

if [ "$LIVE_OK" -ne 1 ]; then
  echo "Assistant did not become live within the bounded startup window." >&2
  if [ -s "$LIVE_ERR" ]; then
    cat "$LIVE_ERR" >&2
  fi
  echo >&2
  echo "=== assistant container diagnostics ===" >&2
  docker compose --env-file .env ps -a >&2 || true
  docker compose --env-file .env logs --no-color --tail=250 assistant >&2 || true
  exit 1
fi

cat "$LIVE_FILE"
echo

echo
echo "=== readiness ==="
READY_FILE=$(mktemp)
trap 'rm -f "$LIVE_FILE" "$LIVE_ERR" "$READY_FILE"' EXIT HUP INT TERM
READY_CODE=$(curl --silent --show-error --output "$READY_FILE" --write-out '%{http_code}' "$BASE/health/ready" || true)
cat "$READY_FILE"
echo
echo "readiness_http=$READY_CODE"

case "$READY_CODE" in
  200) echo "readiness=UP (Database + NetworkCore authority reachable)" ;;
  503) echo "readiness=NOT_READY (expected for boot-only test when NetworkCore authority is not configured/reachable)" ;;
  *) echo "Unexpected readiness HTTP code: $READY_CODE" >&2; exit 1 ;;
esac

echo
echo "=== persistent Buggy identity ==="
docker compose --env-file .env exec -T postgres \
  psql -U postgres -d "${LEGIT_ASSISTANT_DB_NAME:-legit_assistant}" -Atc \
  "SELECT assistant_id || '|' || head_revision FROM assistant.buggy_identity_head ORDER BY assistant_id;"

echo
echo "=== database schemas ==="
docker compose --env-file .env exec -T postgres \
  psql -U postgres -d "${LEGIT_ASSISTANT_DB_NAME:-legit_assistant}" -Atc \
  "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('assistant','bugs','knowledge','requests','audit') ORDER BY schema_name;"

echo
echo "=== local management health (inside container) ==="
docker compose --env-file .env exec -T assistant \
  curl --silent --show-error http://127.0.0.1:9091/actuator/health/readiness || true
echo

echo
echo "FIRST_LIVE_CHECK: PASS"
