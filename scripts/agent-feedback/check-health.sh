#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$ROOT_DIR/tmp/agent-feedback/health/$TIMESTAMP"
RESULT_FILE="$OUTPUT_DIR/result.json"

BASE_URL="http://127.0.0.1:8888"
HEALTH_PATH="/actuator/health"
TIMEOUT_SECONDS="120"
MAX_LATENCY_MILLIS="3000"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --path)
      HEALTH_PATH="$2"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --max-latency-millis)
      MAX_LATENCY_MILLIS="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage: bash scripts/agent-feedback/check-health.sh [--base-url URL] [--path /actuator/health] [--timeout-seconds N] [--max-latency-millis N]
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
TARGET_URL="${BASE_URL%/}${HEALTH_PATH}"
START_TS="$(date +%s)"
LAST_BODY=""
LAST_CODE="000"
LAST_LATENCY="0"
LAST_STATUS="UNKNOWN"
OK="false"

while true; do
  NOW_TS="$(date +%s)"
  if [ $((NOW_TS - START_TS)) -ge "$TIMEOUT_SECONDS" ]; then
    break
  fi
  REMAINING_SECONDS=$((TIMEOUT_SECONDS - NOW_TS + START_TS))

  RESPONSE_FILE="$OUTPUT_DIR/health-response.json"
  META_FILE="$OUTPUT_DIR/health-meta.txt"

  set +e
  curl -sS --max-time "$REMAINING_SECONDS" -o "$RESPONSE_FILE" -w '%{http_code} %{time_total}' "$TARGET_URL" >"$META_FILE"
  CURL_EXIT=$?
  set -e

  if [ "$CURL_EXIT" -eq 0 ]; then
    LAST_CODE="$(awk '{print $1}' "$META_FILE")"
    LAST_LATENCY="$(awk '{printf "%d", $2 * 1000}' "$META_FILE")"
    LAST_BODY="$(cat "$RESPONSE_FILE")"
    LAST_STATUS="$(python3 - "$RESPONSE_FILE" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
try:
    payload = json.loads(path.read_text())
except Exception:
    print("UNKNOWN")
    raise SystemExit(0)

print(str(payload.get("status", "UNKNOWN")))
PY
)"

    if [ "$LAST_CODE" = "200" ] && [ "$LAST_STATUS" = "UP" ] && [ "$LAST_LATENCY" -le "$MAX_LATENCY_MILLIS" ]; then
      OK="true"
      break
    fi
  fi

  REMAINING_SECONDS=$((TIMEOUT_SECONDS - $(date +%s) + START_TS))
  if [ "$REMAINING_SECONDS" -le 0 ]; then
    break
  fi
  if [ "$REMAINING_SECONDS" -lt 2 ]; then
    sleep "$REMAINING_SECONDS"
  else
    sleep 2
  fi
done

python3 - "$RESULT_FILE" "$OK" "$TARGET_URL" "$TIMEOUT_SECONDS" "$MAX_LATENCY_MILLIS" "$LAST_CODE" "$LAST_STATUS" "$LAST_LATENCY" <<'PY'
import json
import pathlib
import sys

result_file = pathlib.Path(sys.argv[1])
ok = sys.argv[2] == "true"
payload = {
    "ok": ok,
    "url": sys.argv[3],
    "timeoutSeconds": int(sys.argv[4]),
    "maxLatencyMillis": int(sys.argv[5]),
    "lastHttpCode": sys.argv[6],
    "lastStatus": sys.argv[7],
    "lastLatencyMillis": int(sys.argv[8]),
}
result_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2))
print(json.dumps(payload, ensure_ascii=False, indent=2))
PY

if [ "$OK" != "true" ]; then
  exit 1
fi
