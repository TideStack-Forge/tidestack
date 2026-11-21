#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[logicflow-editor-static] %s\n' "$*"
}

fail() {
  log "$*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

launch_detached_process() {
  local log_file="$1"
  shift

  python3 - "$log_file" "$@" <<'PY'
import subprocess
import sys

log_path = sys.argv[1]
command = sys.argv[2:]

if not command:
    raise SystemExit("missing detached command")

with open(log_path, "ab", buffering=0) as log_file:
    process = subprocess.Popen(
        command,
        stdout=log_file,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        start_new_session=True,
    )

print(process.pid)
PY
}

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../" && pwd)
EDITOR_ROOT="${ROOT_DIR}/frontends/web/ouroboros-logicflow-editor"
EDITOR_DIST_DIR="${EDITOR_ROOT}/dist"
RUNTIME_ROOT="${OB_TEST_RUNTIME_ROOT:-${ROOT_DIR}/tmp/testing-runtime/logicflow-editor-static}"
STATIC_ROOT="${RUNTIME_ROOT}/root"
TARGET_DIR="${STATIC_ROOT}/micro-logicflow-editor"
PID_DIR="${RUNTIME_ROOT}/pids"
LOG_DIR="${RUNTIME_ROOT}/logs"
PID_FILE="${PID_DIR}/http-server.pid"
STATIC_LOG_FILE="${LOG_DIR}/http-server.log"
BUILD_LOG_FILE="${LOG_DIR}/build.log"
SOURCE_SIGNATURE_FILE="${RUNTIME_ROOT}/source-signature.sha256"
HOST="${SMOKE_FRONTEND_HOST:-127.0.0.1}"
PORT="${SMOKE_LOGICFLOW_EDITOR_PORT:-19088}"
HOST_BASE_URL="${SMOKE_LOGICFLOW_EDITOR_HOST_BASE_URL:-http://${HOST}:${PORT}}"
ENTRY_URL="${HOST_BASE_URL%/}/micro-logicflow-editor/"

need python3
need npm
need curl
need lsof

mkdir -p "${STATIC_ROOT}" "${PID_DIR}" "${LOG_DIR}"

compute_source_signature() {
  python3 - "${EDITOR_ROOT}" <<'PY'
import hashlib
import os
import sys

root = sys.argv[1]
directories = ("src", "public", "tests")
files = (
    "package.json",
    "package-lock.json",
    "webpack.config.js",
    "babel.config.js",
)

digest = hashlib.sha256()

for relative_path in files:
    absolute_path = os.path.join(root, relative_path)
    if not os.path.isfile(absolute_path):
        continue
    digest.update(relative_path.encode("utf-8"))
    with open(absolute_path, "rb") as handle:
        digest.update(handle.read())

for directory in directories:
    absolute_directory = os.path.join(root, directory)
    if not os.path.isdir(absolute_directory):
        continue
    for current_root, dir_names, file_names in os.walk(absolute_directory):
        dir_names.sort()
        file_names.sort()
        for file_name in file_names:
            absolute_path = os.path.join(current_root, file_name)
            relative_path = os.path.relpath(absolute_path, root)
            digest.update(relative_path.encode("utf-8"))
            with open(absolute_path, "rb") as handle:
                digest.update(handle.read())

print(digest.hexdigest())
PY
}

needs_build=0
current_source_signature="$(compute_source_signature)"
if [[ ! -f "${EDITOR_DIST_DIR}/index.html" ]]; then
  needs_build=1
elif [[ ! -f "${SOURCE_SIGNATURE_FILE}" ]]; then
  needs_build=1
elif [[ "$(cat "${SOURCE_SIGNATURE_FILE}")" != "${current_source_signature}" ]]; then
  needs_build=1
fi

if (( needs_build )); then
  log "检测到 logicflow editor 源码签名变化，执行 npm run package"
  : >"${BUILD_LOG_FILE}"
  if ! (
    cd "${EDITOR_ROOT}"
    npm run package >"${BUILD_LOG_FILE}" 2>&1
  ); then
    tail -n 80 "${BUILD_LOG_FILE}" || true
    fail "logicflow editor 构建失败，请检查日志: ${BUILD_LOG_FILE}"
  fi
  printf '%s\n' "${current_source_signature}" >"${SOURCE_SIGNATURE_FILE}"
fi

rm -rf "${TARGET_DIR}"
ln -s "${EDITOR_DIST_DIR}" "${TARGET_DIR}"

if [[ -f "${PID_FILE}" ]]; then
  pid=$(cat "${PID_FILE}" || true)
  if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
    if curl -fsS --max-time 2 "${ENTRY_URL}" >/dev/null 2>&1; then
      log "logicflow editor 静态服务已在运行: ${ENTRY_URL}"
      printf '%s\n' "${ENTRY_URL}"
      exit 0
    fi
    kill "${pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${PID_FILE}"
fi

existing_pid=$(lsof -ti tcp:"${PORT}" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)
if [[ -n "${existing_pid}" ]]; then
  if curl -fsS --max-time 2 "${ENTRY_URL}" >/dev/null 2>&1; then
    printf '%s\n' "${existing_pid}" >"${PID_FILE}"
    log "复用已存在的静态服务进程 ${existing_pid}: ${ENTRY_URL}"
    printf '%s\n' "${ENTRY_URL}"
    exit 0
  fi
  fail "端口 ${PORT} 已被其他进程占用，且未提供可用的 logicflow editor 静态入口"
fi

: >"${STATIC_LOG_FILE}"
(
  cd "${STATIC_ROOT}"
  launch_detached_process "${STATIC_LOG_FILE}" \
    python3 -m http.server "${PORT}" \
    --bind "${HOST}" \
    --directory "${STATIC_ROOT}" \
    >"${PID_FILE}"
)

for _ in $(seq 1 60); do
  if curl -fsS --max-time 2 "${ENTRY_URL}" >/dev/null 2>&1; then
    log "logicflow editor 静态服务已启动: ${ENTRY_URL}"
    log "启动 develop 壳时请注入 DEV_E2E_FRONTEND_EXTRA_ARGS=\"--logicflow_editor ${HOST_BASE_URL}\"（兼容旧 FRONTEND_EXTRA_ARGS）"
    printf '%s\n' "${ENTRY_URL}"
    exit 0
  fi
  sleep 1
done

if [[ -f "${PID_FILE}" ]]; then
  pid=$(cat "${PID_FILE}" || true)
  if [[ -n "${pid}" ]]; then
    kill "${pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${PID_FILE}"
fi
tail -n 80 "${STATIC_LOG_FILE}" || true
fail "logicflow editor 静态服务启动失败，请检查日志: ${STATIC_LOG_FILE}"
