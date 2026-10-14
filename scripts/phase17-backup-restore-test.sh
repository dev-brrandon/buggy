#!/usr/bin/env bash
set -euo pipefail

: "${PHASE17_PG_BIN:?directory containing PostgreSQL client binaries required}"
: "${PGHOST:?required}"
: "${PGPORT:?required}"
: "${PGUSER:?required}"
: "${PHASE17_SOURCE_DB:?required}"
: "${PHASE17_RESTORE_DB:?required}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
START_EPOCH="$(date +%s)"
BACKUP_DIR="${PHASE17_BACKUP_DIR:-$(mktemp -d)}"
PSQL="$PHASE17_PG_BIN/psql"
CREATEDB="$PHASE17_PG_BIN/createdb"
DROPDB="$PHASE17_PG_BIN/dropdb"
PG_DUMP_BIN="$PHASE17_PG_BIN/pg_dump"
PG_RESTORE_BIN="$PHASE17_PG_BIN/pg_restore"

export PG_DUMP_BIN PG_RESTORE_BIN
export LEGIT_ASSISTANT_BACKUP_DIR="$BACKUP_DIR"
export PGDATABASE="$PHASE17_SOURCE_DB"

BACKUP="$($ROOT/deployment/postgres/backup-postgres.sh | tail -n 1)"
"$ROOT/deployment/postgres/verify-backup.sh" "$BACKUP"

"$DROPDB" --if-exists "$PHASE17_RESTORE_DB" >/dev/null 2>&1 || true
"$CREATEDB" "$PHASE17_RESTORE_DB"
export PGDATABASE="$PHASE17_RESTORE_DB"
export LEGIT_ASSISTANT_RESTORE_CONFIRM=RESTORE
"$ROOT/deployment/postgres/restore-postgres.sh" "$BACKUP"

schemas=(assistant bugs knowledge requests audit)
for schema in "${schemas[@]}"; do
  SOURCE_OBJECTS="$($PSQL --dbname="$PHASE17_SOURCE_DB" --tuples-only --no-align --command="SELECT count(*) FROM information_schema.tables WHERE table_schema='${schema}';")"
  RESTORE_OBJECTS="$($PSQL --dbname="$PHASE17_RESTORE_DB" --tuples-only --no-align --command="SELECT count(*) FROM information_schema.tables WHERE table_schema='${schema}';")"
  [[ "$SOURCE_OBJECTS" == "$RESTORE_OBJECTS" ]] || {
    echo "table count mismatch schema=$schema source=$SOURCE_OBJECTS restore=$RESTORE_OBJECTS" >&2
    exit 1
  }
done

for table in \
  assistant.conversations \
  assistant.conversation_messages \
  knowledge.documents \
  knowledge.revisions \
  bugs.records \
  bugs.history \
  requests.records \
  requests.payload_revisions \
  requests.approvals \
  requests.history; do
  SOURCE_COUNT="$($PSQL --dbname="$PHASE17_SOURCE_DB" --tuples-only --no-align --command="SELECT count(*) FROM ${table};")"
  RESTORE_COUNT="$($PSQL --dbname="$PHASE17_RESTORE_DB" --tuples-only --no-align --command="SELECT count(*) FROM ${table};")"
  [[ "$SOURCE_COUNT" == "$RESTORE_COUNT" ]] || {
    echo "row count mismatch table=$table source=$SOURCE_COUNT restore=$RESTORE_COUNT" >&2
    exit 1
  }
done

SOURCE_META="$($PSQL --dbname="$PHASE17_SOURCE_DB" --tuples-only --no-align --command="SELECT count(*) FROM assistant.platform_metadata;")"
RESTORE_META="$($PSQL --dbname="$PHASE17_RESTORE_DB" --tuples-only --no-align --command="SELECT count(*) FROM assistant.platform_metadata;")"
[[ "$SOURCE_META" == "$RESTORE_META" ]] || {
  echo "metadata row count mismatch source=$SOURCE_META restore=$RESTORE_META" >&2
  exit 1
}

ELAPSED_SECONDS=$(( $(date +%s) - START_EPOCH ))
[[ "$ELAPSED_SECONDS" -lt 3600 ]] || {
  echo "restore drill exceeded RTO: ${ELAPSED_SECONDS}s" >&2
  exit 1
}
echo "PHASE17_RESTORE_TEST: PASS source=$PHASE17_SOURCE_DB restore=$PHASE17_RESTORE_DB elapsedSeconds=$ELAPSED_SECONDS rtoSeconds=3600"
echo "PHASE17_BACKUP_VERIFIED: PASS backup=$BACKUP"
