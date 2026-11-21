#!/bin/bash

set -euo pipefail

BASE_URL="http://127.0.0.1:8888"
ENDPOINT="metrics"
METRIC_NAME=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --endpoint)
      ENDPOINT="$2"
      shift 2
      ;;
    --metric)
      METRIC_NAME="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage: bash scripts/agent-feedback/query-metrics.sh [--base-url URL] [--endpoint metrics|prometheus] [--metric NAME]
EOF
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [ "$ENDPOINT" = "prometheus" ]; then
  curl -fsS "${BASE_URL%/}/actuator/prometheus"
  exit 0
fi

TARGET_URL="${BASE_URL%/}/actuator/metrics"
if [ -n "$METRIC_NAME" ]; then
  TARGET_URL="$TARGET_URL/$METRIC_NAME"
fi

curl -fsS "$TARGET_URL"
