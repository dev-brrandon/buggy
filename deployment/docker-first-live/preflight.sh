#!/bin/sh
set -eu

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 1; }
}

need docker
need curl
need openssl

docker version >/dev/null
docker compose version >/dev/null

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$DIR"

test -f ../../gradlew
test -f ../../assistant-persistence/src/main/resources/db/migration/V100_005__create_buggy_self_development.sql

echo "Docker: $(docker --version)"
echo "Compose: $(docker compose version --short)"
echo "FIRST_LIVE_PREFLIGHT: PASS"
