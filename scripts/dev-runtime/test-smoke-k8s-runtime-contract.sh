#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/dev-runtime/smoke-k8s-runtime.sh"
OUTPUT_DIR="$ROOT_DIR/tmp/dev-runtime/k8s-smoke-contract-test"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
trap 'rm -rf "$OUTPUT_DIR" /tmp/test-smoke-k8s-runtime-contract.stdout /tmp/test-smoke-k8s-runtime-contract.stderr' EXIT

set +e
K8S_SMOKE_OUTPUT_DIR="$OUTPUT_DIR" "$SCRIPT_PATH" >/tmp/test-smoke-k8s-runtime-contract.stdout 2>/tmp/test-smoke-k8s-runtime-contract.stderr
exit_code=$?
set -e

if [ "$exit_code" -ne 10 ]; then
  echo "expected exit code 10, got $exit_code" >&2
  exit 1
fi

if [ ! -f "$OUTPUT_DIR/result.json" ]; then
  echo "expected result.json to be written" >&2
  exit 1
fi

if ! grep -q '"ok":[[:space:]]*false' "$OUTPUT_DIR/result.json"; then
  echo "expected ok=false in result.json" >&2
  exit 1
fi

if ! grep -q '"stage":[[:space:]]*"preflight"' "$OUTPUT_DIR/result.json"; then
  echo "expected failure.stage=preflight in result.json" >&2
  exit 1
fi

echo "contract test passed"
