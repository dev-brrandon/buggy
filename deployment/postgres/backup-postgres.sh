#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${LEGIT_ASSISTANT_BACKUP_DIR:?required}"
: "${PGDATABASE:?required}"

PG_DUMP_BIN="${PG_DUMP_BIN:-pg_dump}"
PG_RESTORE_BIN="${PG_RESTORE_BIN:-pg_restore}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$LEGIT_ASSISTANT_BACKUP_DIR"
TMP="$LEGIT_ASSISTANT_BACKUP_DIR/.legit-assistant-${STAMP}.dump.tmp"
OUT="$LEGIT_ASSISTANT_BACKUP_DIR/legit-assistant-${STAMP}.dump"

"$PG_DUMP_BIN" \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges \
  --schema=assistant \
  --schema=bugs \
  --schema=knowledge \
  --schema=requests \
  --schema=audit \
  --file="$TMP"

"$PG_RESTORE_BIN" --list "$TMP" >/dev/null
mv "$TMP" "$OUT"
(
  cd "$LEGIT_ASSISTANT_BACKUP_DIR"
  sha256sum "$(basename "$OUT")" >"$(basename "$OUT").sha256"
)
"$PG_RESTORE_BIN" --list "$OUT" >"$OUT.toc"
printf '%s\n' "$OUT"
