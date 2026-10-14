#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
: "${LEGIT_ASSISTANT_POSTGRES_TEST_URL:?required dedicated test database URL}"
: "${LEGIT_ASSISTANT_POSTGRES_TEST_USER:?required dedicated test database user}"
export LEGIT_ASSISTANT_POSTGRES_TEST_PASSWORD="${LEGIT_ASSISTANT_POSTGRES_TEST_PASSWORD:-}"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon :assistant-persistence:postgresSelfTest
"$GRADLE_CMD" --no-daemon :assistant-integrations:buggyIdentityPostgresSelfTest
