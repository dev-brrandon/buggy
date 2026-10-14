#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="${GRADLE_CMD:-$ROOT/gradlew}"
cd "$ROOT"
"$GRADLE_CMD" --no-daemon --offline :assistant-pipeline:selfTest :assistant-pipeline:jar
JAR="$(find "$ROOT/assistant-pipeline/build/libs" -maxdepth 1 -name 'assistant-pipeline-*.jar' ! -name '*-sources.jar' | sort | tail -n 1)"
if [[ -z "$JAR" ]]; then
  echo "PHASE13_JDEPS: FAIL (jar not found)" >&2
  exit 1
fi
JDEPS_OUT="$(jdeps --multi-release 25 --ignore-missing-deps --print-module-deps "$JAR")"
case "$JDEPS_OUT" in
  *java.sql*|*java.desktop*|*jdk.unsupported*)
    echo "PHASE13_JDEPS: FAIL ($JDEPS_OUT)" >&2
    exit 1
    ;;
esac
if grep -R -E 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec|/bin/(sh|bash)|cmd\.exe' \
  "$ROOT/assistant-pipeline/src/main/java" >/dev/null; then
  echo "PHASE13_FORBIDDEN_EXEC_CONTRACT: FAIL" >&2
  exit 1
fi
echo "PHASE13_JDEPS: PASS ($JDEPS_OUT)"
echo "PHASE13_FORBIDDEN_EXEC_CONTRACT: PASS"
