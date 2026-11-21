#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
DEFAULT_BACKEND_LOG="$ROOT_DIR/tmp/playwright-it/backend/logs/backend-live.log"
DEFAULT_FRONTEND_LOG="$ROOT_DIR/frontends/web/main-app/tmp/playwright-it/frontend/logs/frontend-live.log"

PRESET="backend"
TARGET_FILE=""
LINES="200"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --preset)
      PRESET="$2"
      shift 2
      ;;
    --file)
      TARGET_FILE="$2"
      shift 2
      ;;
    --lines)
      LINES="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage: bash scripts/agent-feedback/query-logs.sh [--preset backend|frontend] [--file PATH] [--lines N]
EOF
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

case "$PRESET" in
  backend)
    DEFAULT_FILE="$DEFAULT_BACKEND_LOG"
    ;;
  frontend)
    DEFAULT_FILE="$DEFAULT_FRONTEND_LOG"
    ;;
  *)
    echo "unsupported preset: $PRESET" >&2
    exit 1
    ;;
esac

if [ -z "$TARGET_FILE" ]; then
  TARGET_FILE="$DEFAULT_FILE"
fi

if [ ! -f "$TARGET_FILE" ]; then
  echo "log file not found: $TARGET_FILE" >&2
  exit 1
fi

python3 - "$TARGET_FILE" "$LINES" "$PRESET" <<'PY'
import json
import pathlib
import shlex
import sys

file_path = pathlib.Path(sys.argv[1])
lines = int(sys.argv[2])
source = sys.argv[3]

all_lines = file_path.read_text(errors="replace").splitlines()
start = max(0, len(all_lines) - lines)

for line_number, raw in enumerate(all_lines[start:], start=start + 1):
    event = {
        "source": source,
        "file": str(file_path),
        "lineNumber": line_number,
        "raw": raw,
    }
    try:
        tokens = shlex.split(raw)
    except ValueError:
        tokens = []

    fields = {}
    for token in tokens:
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        if key:
            fields[key] = value

    if fields:
        event["fields"] = fields

    print(json.dumps(event, ensure_ascii=False))
PY
