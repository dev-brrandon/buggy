#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
PORT="${PORT:-18094}"
MANAGEMENT_PORT="${MANAGEMENT_PORT:-19094}"
TMP="${TMPDIR:-/tmp}/legit-assistant-phase14-$$"
mkdir -p "$TMP"
cd "$ROOT"
"$GRADLE_CMD" --offline --no-daemon :assistant-bootstrap:installDist >/dev/null
LAUNCHER="$ROOT/assistant-bootstrap/build/install/legit-assistant/bin/legit-assistant"
LEGIT_ASSISTANT_MANAGEMENT_ADDRESS=127.0.0.1 \
LEGIT_ASSISTANT_MANAGEMENT_PORT="$MANAGEMENT_PORT" \
"$LAUNCHER" --server.port="$PORT" >"$TMP/runtime.log" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; rm -rf "$TMP"' EXIT
for _ in $(seq 1 60); do
  if curl --fail --silent "http://127.0.0.1:${PORT}/health/live" >/dev/null; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then tail -n 120 "$TMP/runtime.log" >&2; exit 1; fi
  sleep 1
done

OPENAPI_CODE="$(curl --silent --output "$TMP/openapi.yaml" --write-out '%{http_code}' "http://127.0.0.1:${PORT}/api/assistant/v1/openapi.yaml")"
ASSISTANTS_CODE="$(curl --silent --dump-header "$TMP/assistants.headers" --output "$TMP/assistants.json" --write-out '%{http_code}' "http://127.0.0.1:${PORT}/api/assistant/v1/assistants?pageSize=25")"
MISSING_IDEMPOTENCY_CODE="$(curl --silent --header 'Content-Type: application/json' --data '{"data":{}}' --output "$TMP/missing-idem.json" --write-out '%{http_code}' "http://127.0.0.1:${PORT}/api/assistant/v1/conversations")"
WITH_IDEMPOTENCY_CODE="$(curl --silent --header 'Content-Type: application/json' --header 'Idempotency-Key: phase14-smoke-1' --data '{"data":{}}' --output "$TMP/with-idem.json" --write-out '%{http_code}' "http://127.0.0.1:${PORT}/api/assistant/v1/conversations")"

python3 - "$TMP" "$OPENAPI_CODE" "$ASSISTANTS_CODE" "$MISSING_IDEMPOTENCY_CODE" "$WITH_IDEMPOTENCY_CODE" <<'PY'
import json, pathlib, sys
root=pathlib.Path(sys.argv[1])
openapi_code, assistants_code, missing_code, with_code = sys.argv[2:]
assert openapi_code == '200'
spec=(root/'openapi.yaml').read_text()
assert 'openapi: 3.1.0' in spec and 'url: /api/assistant/v1' in spec
assert assistants_code == '503'
assistants=json.loads((root/'assistants.json').read_text())
assert assistants['errorCode'] == 'API.BACKEND_UNAVAILABLE'
assert assistants['retryable'] is True
headers=(root/'assistants.headers').read_text().lower()
assert 'x-request-id:' in headers and 'x-trace-id:' in headers
assert missing_code == '400'
missing=json.loads((root/'missing-idem.json').read_text())
assert missing['errorCode'] == 'API.INVALID_ARGUMENT'
assert missing['requestId'] != 'unavailable'
assert with_code == '503'
with_idem=json.loads((root/'with-idem.json').read_text())
assert with_idem['errorCode'] == 'API.BACKEND_UNAVAILABLE'
print('PHASE14_API_RUNTIME_SMOKE: PASS')
PY
