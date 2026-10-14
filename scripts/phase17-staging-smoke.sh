#!/usr/bin/env bash
set -euo pipefail

DIST_DIR="${1:?path to built legit-assistant distribution required}"
JAVA_BIN="${JAVA_BIN:-java}"
APP_PORT="${PHASE17_APP_PORT:-18080}"
MGMT_PORT="${PHASE17_MANAGEMENT_PORT:-19091}"
STAGE_ROOT="${PHASE17_STAGE_ROOT:-$(mktemp -d)}"
APP_ROOT="$STAGE_ROOT/app"
TMP_ROOT="$STAGE_ROOT/tmp"
HOME_ROOT="$STAGE_ROOT/home"
LOG_FILE="$STAGE_ROOT/staging.log"
RUN_UID="${PHASE17_RUN_UID:-65534}"
RUN_GID="${PHASE17_RUN_GID:-65534}"
PID=""

cleanup() {
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    kill -TERM "$PID" 2>/dev/null || true
    for _ in $(seq 1 60); do
      kill -0 "$PID" 2>/dev/null || break
      sleep 0.1
    done
    kill -KILL "$PID" 2>/dev/null || true
  fi
  chmod -R u+w "$STAGE_ROOT" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "$APP_ROOT" "$TMP_ROOT" "$HOME_ROOT"
chmod 0755 "$STAGE_ROOT" "$APP_ROOT"
cp -a "$DIST_DIR"/. "$APP_ROOT"/
chown -R "$RUN_UID:$RUN_GID" "$TMP_ROOT" "$HOME_ROOT"
chmod -R a-w "$APP_ROOT"
chmod -R a+rX "$APP_ROOT"

start_app() {
  : >"$LOG_FILE"
  setpriv \
    --reuid="$RUN_UID" \
    --regid="$RUN_GID" \
    --clear-groups \
    env \
      HOME="$HOME_ROOT" \
      TMPDIR="$TMP_ROOT" \
      SERVER_PORT="$APP_PORT" \
      LEGIT_ASSISTANT_MANAGEMENT_PORT="$MGMT_PORT" \
      JAVA_HOME="${JAVA_HOME:-}" \
      PATH="$PATH" \
      JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMP_ROOT" \
      "$APP_ROOT/bin/legit-assistant" >"$LOG_FILE" 2>&1 &
  PID=$!

  for _ in $(seq 1 200); do
    if curl --fail --silent "http://127.0.0.1:${APP_PORT}/health/live" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "staging process exited before liveness" >&2
      cat "$LOG_FILE" >&2 || true
      return 1
    fi
    sleep 0.1
  done
  echo "staging liveness timeout" >&2
  cat "$LOG_FILE" >&2 || true
  return 1
}

stop_gracefully() {
  local started_ms now_ms elapsed_ms
  started_ms="$(date +%s%3N)"
  kill -TERM "$PID"
  for _ in $(seq 1 300); do
    if ! kill -0 "$PID" 2>/dev/null; then
      wait "$PID" || true
      now_ms="$(date +%s%3N)"
      elapsed_ms=$((now_ms - started_ms))
      [[ "$elapsed_ms" -lt 30000 ]] || {
        echo "graceful shutdown exceeded 30 seconds: ${elapsed_ms}ms" >&2
        return 1
      }
      echo "PHASE17_GRACEFUL_SHUTDOWN: PASS elapsedMs=$elapsed_ms"
      PID=""
      return 0
    fi
    sleep 0.1
  done
  echo "process did not stop gracefully" >&2
  return 1
}

start_app
STATUS_UID="$(awk '/^Uid:/ {print $2}' "/proc/$PID/status")"
[[ "$STATUS_UID" == "$RUN_UID" ]] || {
  echo "staging process is not running as expected non-root uid: $STATUS_UID" >&2
  exit 1
}
[[ "$STATUS_UID" != "0" ]] || {
  echo "staging process must not run as root" >&2
  exit 1
}

LIVE_CODE="$(curl --silent --output "$STAGE_ROOT/live.json" --write-out '%{http_code}' "http://127.0.0.1:${APP_PORT}/health/live")"
READY_CODE="$(curl --silent --output "$STAGE_ROOT/ready.json" --write-out '%{http_code}' "http://127.0.0.1:${APP_PORT}/health/ready")"
[[ "$LIVE_CODE" == "200" ]] || { echo "liveness expected 200, got $LIVE_CODE" >&2; exit 1; }
[[ "$READY_CODE" == "503" ]] || { echo "dependency-outage readiness expected 503, got $READY_CODE" >&2; exit 1; }

if setpriv --reuid="$RUN_UID" --regid="$RUN_GID" --clear-groups sh -c "touch '$APP_ROOT/.write-test'" 2>/dev/null; then
  echo "read-only app tree accepted non-root write" >&2
  exit 1
fi

echo "PHASE17_STAGING_NON_ROOT_READ_ONLY: PASS uid=$STATUS_UID"
echo "PHASE17_DEPENDENCY_OUTAGE: PASS live=$LIVE_CODE ready=$READY_CODE"
stop_gracefully

start_app
SECOND_LIVE_CODE="$(curl --silent --output "$STAGE_ROOT/live-restart.json" --write-out '%{http_code}' "http://127.0.0.1:${APP_PORT}/health/live")"
[[ "$SECOND_LIVE_CODE" == "200" ]] || { echo "restart liveness expected 200, got $SECOND_LIVE_CODE" >&2; exit 1; }
echo "PHASE17_RESTART_SAFE: PASS live=$SECOND_LIVE_CODE"
stop_gracefully

echo "PHASE17_STAGING_DEPLOYMENT: PASS"
