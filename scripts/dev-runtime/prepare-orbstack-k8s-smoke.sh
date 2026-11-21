#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SMOKE_SCRIPT="$ROOT_DIR/scripts/dev-runtime/smoke-k8s-runtime.sh"

K8S_SMOKE_CONTEXT="${K8S_SMOKE_CONTEXT:-orbstack}"
K8S_SMOKE_NAMESPACE="${K8S_SMOKE_NAMESPACE:-dev-runtime-smoke}"
K8S_SMOKE_PVC="${K8S_SMOKE_PVC:-dev-runtime-pvc}"
K8S_SMOKE_PVC_SIZE="${K8S_SMOKE_PVC_SIZE:-1Gi}"
K8S_SMOKE_STORAGE_CLASS="${K8S_SMOKE_STORAGE_CLASS:-}"
K8S_SMOKE_PREPARE_TIMEOUT_SECONDS="${K8S_SMOKE_PREPARE_TIMEOUT_SECONDS:-60}"
K8S_SMOKE_BINDER_IMAGE="${K8S_SMOKE_BINDER_IMAGE:-rancher/mirrored-library-busybox:1.36.1}"
BINDER_POD_NAME="${K8S_SMOKE_PVC}-binder"
BINDER_USED="false"

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "required command not found: $1"
  fi
}

discover_image_candidates() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi
  docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
    | grep '^registry\.cn-hangzhou\.aliyuncs\.com/ouroboros/ouroboros-mothership:' \
    | grep -v ':<none>$' \
    | awk '!seen[$0]++'
}

ensure_namespace() {
  if kubectl get namespace "$K8S_SMOKE_NAMESPACE" >/dev/null 2>&1; then
    printf 'existing'
    return 0
  fi
  kubectl create namespace "$K8S_SMOKE_NAMESPACE" >/dev/null
  printf 'created'
}

apply_pvc() {
  if [ -n "$K8S_SMOKE_STORAGE_CLASS" ]; then
    cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ${K8S_SMOKE_PVC}
  namespace: ${K8S_SMOKE_NAMESPACE}
spec:
  accessModes:
  - ReadWriteOnce
  storageClassName: ${K8S_SMOKE_STORAGE_CLASS}
  resources:
    requests:
      storage: ${K8S_SMOKE_PVC_SIZE}
EOF
    return 0
  fi

  cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ${K8S_SMOKE_PVC}
  namespace: ${K8S_SMOKE_NAMESPACE}
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: ${K8S_SMOKE_PVC_SIZE}
EOF
}

ensure_pvc() {
  if kubectl get pvc "$K8S_SMOKE_PVC" -n "$K8S_SMOKE_NAMESPACE" >/dev/null 2>&1; then
    printf 'existing'
    return 0
  fi
  apply_pvc
  printf 'created'
}

create_binder_pod() {
  cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: ${BINDER_POD_NAME}
  namespace: ${K8S_SMOKE_NAMESPACE}
spec:
  restartPolicy: Never
  terminationGracePeriodSeconds: 0
  containers:
  - name: binder
    image: ${K8S_SMOKE_BINDER_IMAGE}
    command:
    - /bin/sh
    - -c
    - sleep 300
    volumeMounts:
    - name: workspace
      mountPath: /workspace
  volumes:
  - name: workspace
    persistentVolumeClaim:
      claimName: ${K8S_SMOKE_PVC}
EOF
}

delete_binder_pod() {
  kubectl delete pod "$BINDER_POD_NAME" -n "$K8S_SMOKE_NAMESPACE" --ignore-not-found=true --wait=true >/dev/null 2>&1 || true
}

current_pvc_phase() {
  kubectl get pvc "$K8S_SMOKE_PVC" -n "$K8S_SMOKE_NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || true
}

wait_for_pvc_bound() {
  local elapsed=0
  local phase=""

  while [ "$elapsed" -lt "$K8S_SMOKE_PREPARE_TIMEOUT_SECONDS" ]; do
    phase="$(kubectl get pvc "$K8S_SMOKE_PVC" -n "$K8S_SMOKE_NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    if [ "$phase" = "Bound" ]; then
      printf '%s' "$phase"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  fail "pvc did not become Bound within ${K8S_SMOKE_PREPARE_TIMEOUT_SECONDS}s: ${K8S_SMOKE_NAMESPACE}/${K8S_SMOKE_PVC}"
}

print_smoke_command() {
  local suggested_image="$1"

  log "Next smoke command:"
  printf 'K8S_SMOKE_NAMESPACE=%s \\\n' "$K8S_SMOKE_NAMESPACE"
  printf 'K8S_SMOKE_PVC=%s \\\n' "$K8S_SMOKE_PVC"
  printf 'K8S_SMOKE_IMAGE=%s \\\n' "$suggested_image"
  printf './scripts/dev-runtime/smoke-k8s-runtime.sh\n'
}

need_command kubectl

if [ ! -x "$SMOKE_SCRIPT" ]; then
  fail "smoke script not found or not executable: $SMOKE_SCRIPT"
fi

CURRENT_CONTEXT="$(kubectl config current-context 2>/dev/null || true)"
if [ -z "$CURRENT_CONTEXT" ]; then
  fail "kubectl current-context is unavailable"
fi

if [ "$CURRENT_CONTEXT" != "$K8S_SMOKE_CONTEXT" ]; then
  fail "current context is '$CURRENT_CONTEXT', expected '$K8S_SMOKE_CONTEXT'; switch context before running this helper"
fi

if ! kubectl cluster-info >/dev/null 2>&1; then
  fail "cluster is not reachable for context '$K8S_SMOKE_CONTEXT'; open OrbStack Kubernetes and try again"
fi

NAMESPACE_STATUS="$(ensure_namespace)"
PVC_STATUS="$(ensure_pvc)"
PVC_PHASE="$(current_pvc_phase)"
if [ "$PVC_PHASE" != "Bound" ]; then
  create_binder_pod
  BINDER_USED="true"
fi
trap 'if [ "$BINDER_USED" = "true" ]; then delete_binder_pod; fi' EXIT
PVC_PHASE="$(wait_for_pvc_bound)"
if [ "$BINDER_USED" = "true" ]; then
  delete_binder_pod
fi

IMAGE_CANDIDATES="$(discover_image_candidates || true)"
SUGGESTED_IMAGE="${K8S_SMOKE_IMAGE:-}"
IMAGE_CANDIDATE_COUNT=0

if [ -n "$IMAGE_CANDIDATES" ]; then
  IMAGE_CANDIDATE_COUNT="$(printf '%s\n' "$IMAGE_CANDIDATES" | sed '/^$/d' | wc -l | tr -d ' ')"
fi

if [ -z "$SUGGESTED_IMAGE" ] && [ "$IMAGE_CANDIDATE_COUNT" = "1" ]; then
  SUGGESTED_IMAGE="$(printf '%s\n' "$IMAGE_CANDIDATES" | sed -n '1p')"
fi

log "OrbStack Kubernetes smoke prerequisites are ready."
log "Context: $CURRENT_CONTEXT"
log "Namespace: $K8S_SMOKE_NAMESPACE ($NAMESPACE_STATUS)"
log "PVC: $K8S_SMOKE_PVC ($PVC_STATUS, phase=$PVC_PHASE)"
if [ "$BINDER_USED" = "true" ]; then
  log "Binder image: $K8S_SMOKE_BINDER_IMAGE"
fi

if [ -n "$K8S_SMOKE_STORAGE_CLASS" ]; then
  log "StorageClass: $K8S_SMOKE_STORAGE_CLASS"
fi

if [ -z "$SUGGESTED_IMAGE" ]; then
  if [ "$IMAGE_CANDIDATE_COUNT" -gt 1 ]; then
    log "Discovered local mothership images:"
    printf '%s\n' "$IMAGE_CANDIDATES"
  elif ! command -v docker >/dev/null 2>&1; then
    log "Docker is unavailable, so no local image candidate could be discovered."
  else
    log "No local mothership image candidate was discovered."
  fi
  SUGGESTED_IMAGE="<set-K8S_SMOKE_IMAGE>"
else
  log "Suggested image: $SUGGESTED_IMAGE"
fi

print_smoke_command "$SUGGESTED_IMAGE"
