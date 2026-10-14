#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
PORT="${PORT:-18081}"
MANAGEMENT_PORT="${MANAGEMENT_PORT:-19092}"
LOG_FILE="${TMPDIR:-/tmp}/legit-assistant-smoke.log"
LIVE_FILE="${TMPDIR:-/tmp}/legit-assistant-live.json"
READY_FILE="${TMPDIR:-/tmp}/legit-assistant-ready.json"
ACT_READY_FILE="${TMPDIR:-/tmp}/legit-assistant-actuator-ready.json"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon :assistant-bootstrap:installDist >/dev/null
LAUNCHER="$ROOT/assistant-bootstrap/build/install/legit-assistant/bin/legit-assistant"
LEGIT_ASSISTANT_MANAGEMENT_ADDRESS=127.0.0.1 \
LEGIT_ASSISTANT_MANAGEMENT_PORT="$MANAGEMENT_PORT" \
"$LAUNCHER" --server.port="$PORT" >"$LOG_FILE" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do
  if curl --fail --silent "http://127.0.0.1:${PORT}/health/live" >"$LIVE_FILE"; then
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Smoke test process exited early. Last log lines:" >&2
    tail -n 120 "$LOG_FILE" >&2
    exit 1
  fi
  sleep 1
done

curl --fail --silent "http://127.0.0.1:${PORT}/health/live" >"$LIVE_FILE"
READY_CODE="$(curl --silent --output "$READY_FILE" --write-out '%{http_code}' "http://127.0.0.1:${PORT}/health/ready")"
ACT_READY_CODE="$(curl --silent --output "$ACT_READY_FILE" --write-out '%{http_code}' "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/readiness")"

python3 - "$LIVE_FILE" "$READY_FILE" "$ACT_READY_FILE" "$READY_CODE" "$ACT_READY_CODE" <<'PY'
import json
import pathlib
import sys
live = json.loads(pathlib.Path(sys.argv[1]).read_text())
ready = json.loads(pathlib.Path(sys.argv[2]).read_text())
act_ready = json.loads(pathlib.Path(sys.argv[3]).read_text())
ready_code, act_ready_code = sys.argv[4], sys.argv[5]
assert live.get("status") == "UP" and live.get("live") is True
# In the foundation runtime NetworkCore/DB are intentionally not wired yet.
assert ready_code == "503" and ready.get("status") == "NOT_READY"
assert act_ready_code == "503" and act_ready.get("status") == "OUT_OF_SERVICE"
assert act_ready["components"]["phase6Dependencies"]["status"] == "OUT_OF_SERVICE"
print("PHASE6_RUNTIME_SMOKE: PASS")
PY
