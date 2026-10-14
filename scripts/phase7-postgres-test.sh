#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
: "${LEGIT_ASSISTANT_POSTGRES_TEST_URL:?required}"
: "${LEGIT_ASSISTANT_POSTGRES_TEST_USER:?required}"
export LEGIT_ASSISTANT_POSTGRES_TEST_PASSWORD="${LEGIT_ASSISTANT_POSTGRES_TEST_PASSWORD:-}"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon :assistant-persistence:postgresSelfTest
"$GRADLE_CMD" --no-daemon :assistant-integrations:conversationPostgresSelfTest
"$GRADLE_CMD" --no-daemon :assistant-integrations:phase6PostgresObservabilitySelfTest
"$GRADLE_CMD" --no-daemon :assistant-integrations:knowledgePostgresSelfTest
