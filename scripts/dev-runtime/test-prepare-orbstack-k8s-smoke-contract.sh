#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
HELPER_PATH="$ROOT_DIR/scripts/dev-runtime/prepare-orbstack-k8s-smoke.sh"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
STDOUT_FILE="$TMP_DIR/stdout.txt"
STDERR_FILE="$TMP_DIR/stderr.txt"
STATE_DIR="$TMP_DIR/state"

mkdir -p "$FAKE_BIN" "$STATE_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$FAKE_BIN/kubectl" <<'EOF'
#!/bin/bash

set -euo pipefail

STATE_DIR="${FAKE_STATE_DIR:?}"
TARGET_NAMESPACE="${K8S_SMOKE_NAMESPACE:?}"
TARGET_PVC="${K8S_SMOKE_PVC:?}"

if [ "$#" -ge 2 ] && [ "$1" = "config" ] && [ "$2" = "current-context" ]; then
  echo "orbstack"
  exit 0
fi

if [ "$#" -ge 1 ] && [ "$1" = "cluster-info" ]; then
  echo "Kubernetes control plane is running"
  exit 0
fi

if [ "$#" -ge 3 ] && [ "$1" = "get" ] && [ "$2" = "namespace" ]; then
  if [ -f "$STATE_DIR/namespace-$3" ]; then
    echo "$3"
    exit 0
  fi
  exit 1
fi

if [ "$#" -ge 3 ] && [ "$1" = "create" ] && [ "$2" = "namespace" ]; then
  touch "$STATE_DIR/namespace-$3"
  echo "$3" >> "$STATE_DIR/namespace-create.log"
  exit 0
fi

if [ "$#" -ge 3 ] && [ "$1" = "get" ] && [ "$2" = "pvc" ]; then
  if [ ! -f "$STATE_DIR/pvc-${TARGET_NAMESPACE}-${TARGET_PVC}" ]; then
    exit 1
  fi
  if printf '%s\n' "$*" | grep -q "jsonpath="; then
    if [ -f "$STATE_DIR/pvc-${TARGET_NAMESPACE}-${TARGET_PVC}-bound" ]; then
      echo "Bound"
    else
      echo "Pending"
    fi
  else
    echo "$TARGET_PVC"
  fi
  exit 0
fi

if [ "$#" -ge 3 ] && [ "$1" = "apply" ] && [ "$2" = "-f" ] && [ "$3" = "-" ]; then
  manifest_file="$STATE_DIR/last-apply.yaml"
  cat > "$manifest_file"
  if grep -q "^kind: PersistentVolumeClaim$" "$manifest_file"; then
    cp "$manifest_file" "$STATE_DIR/pvc-apply.yaml"
    touch "$STATE_DIR/pvc-${TARGET_NAMESPACE}-${TARGET_PVC}"
    echo "${TARGET_NAMESPACE}/${TARGET_PVC}" >> "$STATE_DIR/pvc-apply.log"
    exit 0
  fi
  if grep -q "^kind: Pod$" "$manifest_file"; then
    cp "$manifest_file" "$STATE_DIR/binder-apply.yaml"
    touch "$STATE_DIR/pvc-${TARGET_NAMESPACE}-${TARGET_PVC}-bound"
    echo "${TARGET_NAMESPACE}/${TARGET_PVC}" >> "$STATE_DIR/binder-apply.log"
    exit 0
  fi
  echo "unexpected manifest applied" >&2
  exit 98
fi

if [ "$#" -ge 3 ] && [ "$1" = "delete" ] && [ "$2" = "pod" ]; then
  exit 0
fi

echo "unexpected kubectl invocation: $*" >&2
exit 99
EOF

cat > "$FAKE_BIN/docker" <<'EOF'
#!/bin/bash

set -euo pipefail

if [ "$#" -ge 2 ] && [ "$1" = "images" ] && [ "$2" = "--format" ]; then
  echo "registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:1.0.0-beta.2-SNAPSHOT-arm64"
  exit 0
fi

echo "unexpected docker invocation: $*" >&2
exit 99
EOF

chmod +x "$FAKE_BIN/kubectl" "$FAKE_BIN/docker"

set +e
PATH="$FAKE_BIN:$PATH" \
FAKE_STATE_DIR="$STATE_DIR" \
K8S_SMOKE_NAMESPACE="dev-runtime-smoke-contract" \
K8S_SMOKE_PVC="dev-runtime-pvc-contract" \
K8S_SMOKE_PVC_SIZE="2Gi" \
K8S_SMOKE_STORAGE_CLASS="local-path" \
K8S_SMOKE_PREPARE_TIMEOUT_SECONDS="4" \
"$HELPER_PATH" >"$STDOUT_FILE" 2>"$STDERR_FILE"
exit_code=$?
set -e

if [ "$exit_code" -ne 0 ]; then
  echo "expected helper to exit 0, got $exit_code" >&2
  cat "$STDERR_FILE" >&2
  exit 1
fi

if [ ! -f "$STATE_DIR/namespace-create.log" ]; then
  echo "expected namespace to be created" >&2
  exit 1
fi

if [ ! -f "$STATE_DIR/pvc-apply.log" ]; then
  echo "expected pvc manifest to be applied" >&2
  exit 1
fi

if [ ! -f "$STATE_DIR/binder-apply.log" ]; then
  echo "expected binder pod manifest to be applied for WaitForFirstConsumer pvc" >&2
  exit 1
fi

if ! grep -q "name: dev-runtime-pvc-contract" "$STATE_DIR/pvc-apply.yaml"; then
  echo "expected pvc manifest to contain the pvc name" >&2
  exit 1
fi

if ! grep -q "storage: 2Gi" "$STATE_DIR/pvc-apply.yaml"; then
  echo "expected pvc manifest to contain the pvc size" >&2
  exit 1
fi

if ! grep -q "storageClassName: local-path" "$STATE_DIR/pvc-apply.yaml"; then
  echo "expected pvc manifest to contain the storage class" >&2
  exit 1
fi

if ! grep -q "claimName: dev-runtime-pvc-contract" "$STATE_DIR/binder-apply.yaml"; then
  echo "expected binder pod manifest to mount the pvc" >&2
  exit 1
fi

if ! grep -q "K8S_SMOKE_NAMESPACE=dev-runtime-smoke-contract" "$STDOUT_FILE"; then
  echo "expected suggested smoke command to contain the namespace" >&2
  exit 1
fi

if ! grep -q "K8S_SMOKE_PVC=dev-runtime-pvc-contract" "$STDOUT_FILE"; then
  echo "expected suggested smoke command to contain the pvc" >&2
  exit 1
fi

if ! grep -q "K8S_SMOKE_IMAGE=registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:1.0.0-beta.2-SNAPSHOT-arm64" "$STDOUT_FILE"; then
  echo "expected suggested smoke command to contain the discovered image" >&2
  exit 1
fi

if ! grep -q "./scripts/dev-runtime/smoke-k8s-runtime.sh" "$STDOUT_FILE"; then
  echo "expected suggested smoke command to reference the smoke script" >&2
  exit 1
fi

echo "contract test passed"
