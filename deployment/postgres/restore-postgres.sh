#!/usr/bin/env bash
set -euo pipefail

BACKUP="${1:?backup .dump path required}"
: "${PGDATABASE:?target PGDATABASE required}"
: "${LEGIT_ASSISTANT_RESTORE_CONFIRM:?set to RESTORE to proceed}"
[[ "$LEGIT_ASSISTANT_RESTORE_CONFIRM" == "RESTORE" ]] || {
  echo "restore confirmation mismatch" >&2
  exit 2
}
PG_RESTORE_BIN="${PG_RESTORE_BIN:-pg_restore}"
"$(dirname "$0")/verify-backup.sh" "$BACKUP"
"$PG_RESTORE_BIN" \
  --exit-on-error \
  --no-owner \
  --no-privileges \
  --dbname="$PGDATABASE" \
  "$BACKUP"
echo "PHASE17_RESTORE: PASS database=$PGDATABASE"
