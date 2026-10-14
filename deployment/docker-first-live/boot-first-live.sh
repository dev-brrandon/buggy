#!/bin/sh
set -eu

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$DIR"

./preflight.sh
if [ ! -f .env ]; then
  ./generate-first-live-env.sh
  echo "Generated .env. External integrations are intentionally blank for the first boot-only acceptance."
fi

docker compose --env-file .env build assistant
if ! docker compose --env-file .env up -d; then
  echo "FIRST_LIVE_BOOT: docker compose up failed; collecting diagnostics..." >&2
  docker compose --env-file .env ps -a || true
  docker compose --env-file .env logs --no-color --tail=200 postgres || true
  exit 1
fi
./first-live-check.sh
