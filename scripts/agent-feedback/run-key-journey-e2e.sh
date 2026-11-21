#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"

SCENARIO="lowcode-full-app-lifecycle"
MODE="plan"
SCRIPT_ID=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --scenario)
      SCENARIO="$2"
      shift 2
      ;;
    --mode)
      MODE="$2"
      shift 2
      ;;
    --script-id)
      SCRIPT_ID="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
        Usage: bash scripts/agent-feedback/run-key-journey-e2e.sh [--scenario lowcode-full-app-lifecycle|auth-ldap-login|statistics-reporting-runtime-query-and-bi] [--mode plan|run] [--script-id ID]
EOF
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

case "$SCENARIO" in
  lowcode-full-app-lifecycle)
    MANIFEST="testing/e2e/smoke/lowcode/full-app-lifecycle/e2e.yaml"
    ;;
  auth-ldap-login)
    MANIFEST="testing/e2e/smoke/auth/ldap-login/e2e.yaml"
    ;;
  statistics-reporting-runtime-query-and-bi)
    MANIFEST="testing/e2e/regression/statistics-reporting/runtime-query-and-bi/e2e.yaml"
    ;;
  *)
    echo "unsupported scenario: $SCENARIO" >&2
    exit 1
    ;;
esac

OUTPUT_DIR="$ROOT_DIR/tmp/agent-feedback/e2e/$SCENARIO/$TIMESTAMP"
LOG_FILE="$OUTPUT_DIR/command.log"
RESULT_FILE="$OUTPUT_DIR/result.json"
mkdir -p "$OUTPUT_DIR"

COMMAND=(python3 testing/common/runners/playwright/browser_asset_bridge.py --e2e-manifest "$MANIFEST" --mode "$MODE")
if [ -n "$SCRIPT_ID" ]; then
  COMMAND+=(--script-id "$SCRIPT_ID")
fi

STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"

set +e
(
  cd "$ROOT_DIR"
  "${COMMAND[@]}"
) >"$LOG_FILE" 2>&1
EXIT_CODE=$?
set -e

FINISHED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"

python3 - "$RESULT_FILE" "$SCENARIO" "$MODE" "$MANIFEST" "$STARTED_AT" "$FINISHED_AT" "$LOG_FILE" "$EXIT_CODE" "${COMMAND[@]}" <<'PY'
import json
import pathlib
import sys

result_file = pathlib.Path(sys.argv[1])
scenario = sys.argv[2]
mode = sys.argv[3]
manifest = sys.argv[4]
started_at = sys.argv[5]
finished_at = sys.argv[6]
log_file = sys.argv[7]
exit_code = int(sys.argv[8])
command = sys.argv[9:]

payload = {
    "ok": exit_code == 0,
    "scenario": scenario,
    "mode": mode,
    "manifest": manifest,
    "startedAt": started_at,
    "finishedAt": finished_at,
    "exitCode": exit_code,
    "command": command,
    "logFile": log_file,
}

result_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2))
print(json.dumps(payload, ensure_ascii=False, indent=2))
PY

exit "$EXIT_CODE"
