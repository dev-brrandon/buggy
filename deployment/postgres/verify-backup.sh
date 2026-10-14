#!/usr/bin/env bash
set -euo pipefail

BACKUP="${1:?backup .dump path required}"
PG_RESTORE_BIN="${PG_RESTORE_BIN:-pg_restore}"
DIR="$(cd "$(dirname "$BACKUP")" && pwd)"
BASE="$(basename "$BACKUP")"
(
  cd "$DIR"
  sha256sum -c "$BASE.sha256"
)
TOC="$($PG_RESTORE_BIN --list "$BACKUP")"
for schema in assistant bugs knowledge requests audit; do
  grep -Eq "SCHEMA .* ${schema}($| )| ${schema} " <<<"$TOC" || {
    echo "backup missing schema: $schema" >&2
    exit 1
  }
done
echo "PHASE17_BACKUP_VERIFY: PASS backup=$BACKUP"
