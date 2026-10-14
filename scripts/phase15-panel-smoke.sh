#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
PORT="${PORT:-18095}"
MANAGEMENT_PORT="${MANAGEMENT_PORT:-19095}"
TMP="${TMPDIR:-/tmp}/legit-assistant-phase15"
mkdir -p "$TMP"
LOG="$TMP/runtime.log"
SERVICE_KEY_B64="$(python3 - <<'PY'
import base64
print(base64.b64encode(b'phase15-runtime-service-key-32-bytes-plus-0001').decode())
PY
)"
CONTEXT_KEY_B64="$(python3 - <<'PY'
import base64
print(base64.b64encode(b'phase15-runtime-context-key-32-bytes-plus-0001').decode())
PY
)"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon :assistant-bootstrap:installDist >/dev/null
LAUNCHER="$ROOT/assistant-bootstrap/build/install/legit-assistant/bin/legit-assistant"
LEGIT_ASSISTANT_PANEL_SERVICE_HMAC_KEY_B64="$SERVICE_KEY_B64" \
LEGIT_ASSISTANT_PANEL_CONTEXT_HMAC_KEY_B64="$CONTEXT_KEY_B64" \
LEGIT_ASSISTANT_MANAGEMENT_ADDRESS=127.0.0.1 \
LEGIT_ASSISTANT_MANAGEMENT_PORT="$MANAGEMENT_PORT" \
"$LAUNCHER" --server.port="$PORT" >"$LOG" 2>&1 &
PID=$!
cleanup() {
  kill "$PID" 2>/dev/null || true
  for _ in $(seq 1 20); do
    if ! kill -0 "$PID" 2>/dev/null; then wait "$PID" 2>/dev/null || true; return; fi
    sleep 0.1
  done
  kill -9 "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
}
trap cleanup EXIT
for _ in $(seq 1 60); do
  if curl --fail --silent "http://127.0.0.1:${PORT}/health/live" >/dev/null; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then tail -n 160 "$LOG" >&2; exit 1; fi
  sleep 1
done
curl --fail --silent "http://127.0.0.1:${PORT}/api/assistant/v1/openapi.yaml" >"$TMP/openapi.yaml"

make_headers() {
  local req="$1" trace="$2" actor="$3" network="$4" panel="$5" out="$6"
  SERVICE_KEY_B64="$SERVICE_KEY_B64" CONTEXT_KEY_B64="$CONTEXT_KEY_B64" \
  REQ="$req" TRACE="$trace" ACTOR="$actor" NETWORK="$network" PANEL="$panel" OUT="$out" python3 - <<'PY'
import base64, hashlib, hmac, os, time, uuid, pathlib
b64u=lambda b: base64.urlsafe_b64encode(b).decode().rstrip('=')
service_key=base64.b64decode(os.environ['SERVICE_KEY_B64'])
context_key=base64.b64decode(os.environ['CONTEXT_KEY_B64'])
req=os.environ['REQ']; trace=os.environ['TRACE']; actor=os.environ['ACTOR']; network=os.environ['NETWORK']; panel=os.environ['PANEL']
now=int(time.time()); exp=now+45
sjti=uuid.uuid4().hex
service_payload='\n'.join(['legit-panel-backend','legit-assistant-panel-api','assistant.invoke',str(now),str(exp),sjti,req])
service_encoded=b64u(service_payload.encode())
service_input='v1.'+service_encoded
service_sig=b64u(hmac.new(service_key,service_input.encode(),hashlib.sha256).digest())
service='v1.'+service_encoded+'.'+service_sig
cjti=uuid.uuid4().hex
context_payload='\n'.join([actor,network,panel,req,trace,sjti,'panel-01',str(now),str(exp),cjti])
context_encoded=b64u(context_payload.encode())
context_input='puc1.'+context_encoded
context_sig=b64u(hmac.new(context_key,context_input.encode(),hashlib.sha256).digest())
context='puc1.'+context_encoded+'.'+context_sig
pathlib.Path(os.environ['OUT']).write_text('\n'.join([service,context]))
PY
}

request_status() {
  local req="$1" trace="$2" path="$3" outfile="$4" headers="$5"
  local token context
  token="$(sed -n '1p' "$headers")"; context="$(sed -n '2p' "$headers")"
  curl --silent --output "$outfile" --write-out '%{http_code}' \
    -H "X-Request-Id: $req" -H "X-Trace-Id: $trace" \
    -H "Authorization: Bearer $token" -H "X-Legit-Panel-Context: $context" \
    "http://127.0.0.1:${PORT}${path}"
}

# Missing gateway credentials on a protected route must not reach the backend.
NOAUTH_CODE="$(curl --silent --output "$TMP/noauth.json" --write-out '%{http_code}' \
  -H 'X-Request-Id: phase15-noauth' -H 'X-Trace-Id: 15000000000000000000000000000001' \
  "http://127.0.0.1:${PORT}/api/assistant/v1/panel/status")"

make_headers phase15-status 15000000000000000000000000000002 actor-runtime network-runtime panel-runtime "$TMP/status.headers"
STATUS_CODE="$(request_status phase15-status 15000000000000000000000000000002 /api/assistant/v1/panel/status "$TMP/status.json" "$TMP/status.headers")"

# Exact replay of the already claimed service/context credentials must fail.
REPLAY_CODE="$(request_status phase15-status 15000000000000000000000000000002 /api/assistant/v1/panel/status "$TMP/replay.json" "$TMP/status.headers")"

# Fresh service token + tampered user context must fail independently.
make_headers phase15-forged 15000000000000000000000000000003 actor-runtime network-runtime panel-runtime "$TMP/forged.headers"
python3 - "$TMP/forged.headers" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); rows=p.read_text().splitlines(); token=rows[1]
rows[1]=token[:-1]+('A' if token[-1] != 'A' else 'B')
p.write_text('\n'.join(rows))
PY
FORGED_CODE="$(request_status phase15-forged 15000000000000000000000000000003 /api/assistant/v1/panel/status "$TMP/forged.json" "$TMP/forged.headers")"

# Authenticated domain read reaches the backend boundary, which is intentionally unavailable in standalone runtime.
make_headers phase15-degraded 15000000000000000000000000000004 actor-runtime network-runtime panel-runtime "$TMP/degraded.headers"
DEGRADED_CODE="$(request_status phase15-degraded 15000000000000000000000000000004 /api/assistant/v1/assistants "$TMP/degraded.json" "$TMP/degraded.headers")"

# SSE must be authenticated and establish a stream with an initial connected comment.
make_headers phase15-sse 15000000000000000000000000000005 actor-runtime network-runtime panel-runtime "$TMP/sse.headers"
token="$(sed -n '1p' "$TMP/sse.headers")"; context="$(sed -n '2p' "$TMP/sse.headers")"
set +e
curl --silent --no-buffer --max-time 2 \
  -H 'X-Request-Id: phase15-sse' -H 'X-Trace-Id: 15000000000000000000000000000005' \
  -H "Authorization: Bearer $token" -H "X-Legit-Panel-Context: $context" \
  "http://127.0.0.1:${PORT}/api/assistant/v1/panel/events" >"$TMP/sse.txt"
SSE_CURL=$?
set -e
# curl timeout is expected for a live SSE connection.
if [[ "$SSE_CURL" -ne 0 && "$SSE_CURL" -ne 28 ]]; then exit "$SSE_CURL"; fi

python3 - "$TMP/noauth.json" "$TMP/status.json" "$TMP/replay.json" "$TMP/forged.json" "$TMP/degraded.json" "$TMP/sse.txt" \
  "$NOAUTH_CODE" "$STATUS_CODE" "$REPLAY_CODE" "$FORGED_CODE" "$DEGRADED_CODE" <<'PY'
import json, pathlib, sys
noauth,status,replay,forged,degraded,sse=map(lambda x:pathlib.Path(x).read_text(), sys.argv[1:7])
codes=sys.argv[7:12]
assert codes[0] == '401', (codes[0], noauth)
assert codes[1] == '200', (codes[1], status)
body=json.loads(status)
assert body['state'] == 'DEGRADED'
assert body['surfaces']['BUGS']['availability'] == 'UNAVAILABLE'
assert codes[2] == '401', (codes[2], replay)
assert codes[3] in ('401','403'), (codes[3], forged)
assert codes[4] == '503', (codes[4], degraded)
assert json.loads(degraded)['errorCode'] == 'API.BACKEND_UNAVAILABLE'
assert ':connected' in sse.replace(' ', ''), sse
print('PHASE15_PANEL_HTTP_SMOKE: PASS')
PY
