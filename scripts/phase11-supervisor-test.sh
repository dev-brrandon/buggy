#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon :assistant-supervisor:selfTest
