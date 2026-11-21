#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[logicflow-editor-static] %s\n' "$*"
}

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../" && pwd)
RUNTIME_ROOT="${OB_TEST_RUNTIME_ROOT:-${ROOT_DIR}/tmp/testing-runtime/logicflow-editor-static}"
PID_FILE="${RUNTIME_ROOT}/pids/http-server.pid"
PORT="${SMOKE_LOGICFLOW_EDITOR_PORT:-19088}"

if [[ -f "${PID_FILE}" ]]; then
  pid=$(cat "${PID_FILE}" || true)
  if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}" >/dev/null 2>&1 || true
    log "已停止静态服务进程 ${pid}"
  fi
  rm -f "${PID_FILE}"
fi

existing_pid=$(lsof -ti tcp:"${PORT}" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)
if [[ -n "${existing_pid}" ]]; then
  kill "${existing_pid}" >/dev/null 2>&1 || true
  log "已停止端口 ${PORT} 上的残留进程 ${existing_pid}"
fi
