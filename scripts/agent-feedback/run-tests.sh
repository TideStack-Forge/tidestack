#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$ROOT_DIR/tmp/agent-feedback/tests/$TIMESTAMP"
LOG_FILE="$OUTPUT_DIR/command.log"
RESULT_FILE="$OUTPUT_DIR/result.json"

SUITE="architecture"
MODULE_FILE=""
SELECTOR=""
PRE_COMMAND=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --suite)
      SUITE="$2"
      shift 2
      ;;
    --file)
      MODULE_FILE="$2"
      shift 2
      ;;
    --selector)
      SELECTOR="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage: bash scripts/agent-feedback/run-tests.sh [--suite architecture|all|module] [--file MODULE_POM] [--selector TEST_CLASS]
EOF
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

mkdir -p "$OUTPUT_DIR"

case "$SUITE" in
  architecture)
    PRE_COMMAND=(mvn -DskipTests test-compile)
    COMMAND=(mvn -pl testing/architecture-tests -am test -Dsurefire.failIfNoSpecifiedTests=false)
    ;;
  all)
    COMMAND=(mvn test)
    ;;
  module)
    if [ -z "$MODULE_FILE" ]; then
      echo "--file is required when --suite module" >&2
      exit 1
    fi
    COMMAND=(mvn test --file "$MODULE_FILE")
    ;;
  *)
    echo "unsupported suite: $SUITE" >&2
    exit 1
    ;;
esac

if [ -n "$SELECTOR" ]; then
  COMMAND+=("-Dtest=$SELECTOR")
  COMMAND+=("-Dsurefire.failIfNoSpecifiedTests=false")
fi

STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
PRE_COMMAND_SERIALIZED=""

if [ "${#PRE_COMMAND[@]}" -gt 0 ]; then
  PRE_COMMAND_SERIALIZED="${PRE_COMMAND[*]}"
fi

set +e
(
  cd "$ROOT_DIR"
  if [ "${#PRE_COMMAND[@]}" -gt 0 ]; then
    "${PRE_COMMAND[@]}" || exit "$?"
  fi
  "${COMMAND[@]}"
) >"$LOG_FILE" 2>&1
EXIT_CODE=$?
set -e

FINISHED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"

python3 - "$RESULT_FILE" "$SUITE" "$STARTED_AT" "$FINISHED_AT" "$LOG_FILE" "$EXIT_CODE" "$PRE_COMMAND_SERIALIZED" "${COMMAND[@]}" <<'PY'
import json
import pathlib
import sys

result_file = pathlib.Path(sys.argv[1])
suite = sys.argv[2]
started_at = sys.argv[3]
finished_at = sys.argv[4]
log_file = sys.argv[5]
exit_code = int(sys.argv[6])
pre_command = sys.argv[7]
command = sys.argv[8:]

payload = {
    "ok": exit_code == 0,
    "suite": suite,
    "startedAt": started_at,
    "finishedAt": finished_at,
    "exitCode": exit_code,
    "preCommand": pre_command,
    "command": command,
    "logFile": log_file,
}

result_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2))
print(json.dumps(payload, ensure_ascii=False, indent=2))
PY

exit "$EXIT_CODE"
