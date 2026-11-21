#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
WORKTREE_ROOT="${DEV_E2E_WORKTREE_ROOT:-$ROOT_DIR}"
PRIMARY_REPO_ROOT="${DEV_E2E_PRIMARY_REPO_ROOT:-/Users/songmingxu/Projects/ouroboros}"

BACKEND_PORT="${DEV_E2E_BACKEND_PORT:-18080}"
FRONTEND_PORT="${DEV_E2E_FRONTEND_PORT:-18081}"
LOGICFLOW_EDITOR_ENABLED="${DEV_E2E_LOGICFLOW_EDITOR_ENABLED:-false}"
LOGICFLOW_EDITOR_PORT="${DEV_E2E_LOGICFLOW_EDITOR_PORT:-8084}"
WORKFLOW_EDITOR_ENABLED="${DEV_E2E_WORKFLOW_EDITOR_ENABLED:-false}"
WORKFLOW_EDITOR_PORT="${DEV_E2E_WORKFLOW_EDITOR_PORT:-8083}"
TIMEOUT_SECONDS="${DEV_E2E_TIMEOUT_SECONDS:-180}"
FRONTEND_SETTLE_SECONDS="${DEV_E2E_FRONTEND_SETTLE_SECONDS:-2}"
REUSE_EXISTING_BACKEND="${DEV_E2E_REUSE_EXISTING_BACKEND:-false}"
REUSE_EXISTING_FRONTEND="${DEV_E2E_REUSE_EXISTING_FRONTEND:-true}"
REUSE_EXISTING_LOGICFLOW_EDITOR="${DEV_E2E_REUSE_EXISTING_LOGICFLOW_EDITOR:-true}"
REUSE_EXISTING_WORKFLOW_EDITOR="${DEV_E2E_REUSE_EXISTING_WORKFLOW_EDITOR:-true}"
LEGACY_FRONTEND_EXTRA_ARGS="${FRONTEND_EXTRA_ARGS:-}"
FRONTEND_EXTRA_ARGS="${DEV_E2E_FRONTEND_EXTRA_ARGS:-$LEGACY_FRONTEND_EXTRA_ARGS}"
INSTALL_REACTOR="${DEV_E2E_INSTALL_REACTOR:-true}"
INSTALL_COMMAND="${DEV_E2E_INSTALL_COMMAND:-mvn -pl :ouroboros-develop -am -DskipTests -Djacoco.skip=true install}"
REFRESH_CLASSPATH="${DEV_E2E_REFRESH_CLASSPATH:-true}"

BACKEND_DATA_ROOT="${DEV_E2E_BACKEND_DATA_ROOT:-$WORKTREE_ROOT/tmp/playwright-it/backend/data}"
BACKEND_DOCUMENT_ROOT="${DEV_E2E_BACKEND_DOCUMENT_ROOT:-$WORKTREE_ROOT/tmp/playwright-it/backend/document-libraries}"
BACKEND_CLASSPATH_FILE="${DEV_E2E_BACKEND_CLASSPATH_FILE:-$WORKTREE_ROOT/tmp/playwright-it/backend/runtime.classpath}"
BACKEND_PID_FILE="${DEV_E2E_BACKEND_PID_FILE:-$WORKTREE_ROOT/tmp/playwright-it/backend/pids/backend.pid}"
BACKEND_LOG_FILE="${DEV_E2E_BACKEND_LOG_FILE:-$WORKTREE_ROOT/tmp/playwright-it/backend/logs/backend-live.log}"
FRONTEND_PID_FILE="${DEV_E2E_FRONTEND_PID_FILE:-$WORKTREE_ROOT/frontends/web/main-app/tmp/playwright-it/frontend/pids/frontend.pid}"
FRONTEND_LOG_FILE="${DEV_E2E_FRONTEND_LOG_FILE:-$WORKTREE_ROOT/frontends/web/main-app/tmp/playwright-it/frontend/logs/frontend-live.log}"
LOGICFLOW_EDITOR_PID_FILE="${DEV_E2E_LOGICFLOW_EDITOR_PID_FILE:-$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor/tmp/playwright-it/logicflow-editor/pids/logicflow-editor.pid}"
LOGICFLOW_EDITOR_LOG_FILE="${DEV_E2E_LOGICFLOW_EDITOR_LOG_FILE:-$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor/tmp/playwright-it/logicflow-editor/logs/logicflow-editor-live.log}"
WORKFLOW_EDITOR_PID_FILE="${DEV_E2E_WORKFLOW_EDITOR_PID_FILE:-$WORKTREE_ROOT/frontends/web/ouroboros-approval-workflow-designer/tmp/playwright-it/workflow-editor/pids/workflow-editor.pid}"
WORKFLOW_EDITOR_LOG_FILE="${DEV_E2E_WORKFLOW_EDITOR_LOG_FILE:-$WORKTREE_ROOT/frontends/web/ouroboros-approval-workflow-designer/tmp/playwright-it/workflow-editor/logs/workflow-editor-live.log}"
DEBUG_CONFIG_FILE="${DEV_E2E_DEBUG_CONFIG_FILE:-$PRIMARY_REPO_ROOT/platform/ouroboros-develop/application-debug.yml}"
APP_METADATA_ROOT="${DEV_E2E_APP_METADATA_ROOT:-${OB_TEST_APP_ROOT:-}}"
RESULT_DIR="${DEV_E2E_RESULT_DIR:-$WORKTREE_ROOT/tmp/playwright-it/bootstrap}"
RESULT_FILE="${DEV_E2E_RESULT_FILE:-$RESULT_DIR/result.json}"
DEPENDENCY_CP_FILE="${DEV_E2E_DEPENDENCY_CP_FILE:-$RESULT_DIR/runtime.classpath.dependencies.txt}"
SMOKE_APP_NAME="${DEV_E2E_SMOKE_APP_NAME:-playwrightit}"
SMOKE_PROFILE="${DEV_E2E_SMOKE_PROFILE:-admin}"
SMOKE_RUNTIME_CONTAINER_NAME="ouroboros-test-app-${SMOKE_APP_NAME}-${SMOKE_PROFILE}"

API_BASE_URL="http://127.0.0.1:${BACKEND_PORT}"
BASE_URL="http://127.0.0.1:${FRONTEND_PORT}"
LOGICFLOW_EDITOR_BASE_URL="http://127.0.0.1:${LOGICFLOW_EDITOR_PORT}"
WORKFLOW_EDITOR_BASE_URL="http://127.0.0.1:${WORKFLOW_EDITOR_PORT}"
DEBUG_CONSOLE_MICRO_WEB="${DEV_E2E_DEBUG_CONSOLE_MICRO_WEB:-http://127.0.0.1:88}"
LOGICFLOW_EDITOR_MICRO_WEB_DEFAULT="$API_BASE_URL"
if [ "$(printf '%s' "$LOGICFLOW_EDITOR_ENABLED" | tr '[:upper:]' '[:lower:]')" = "true" ]; then
  LOGICFLOW_EDITOR_MICRO_WEB_DEFAULT="$LOGICFLOW_EDITOR_BASE_URL"
fi
LOGICFLOW_EDITOR_MICRO_WEB="${DEV_E2E_LOGICFLOW_EDITOR_MICRO_WEB:-$LOGICFLOW_EDITOR_MICRO_WEB_DEFAULT}"
WORKFLOW_EDITOR_MICRO_WEB_DEFAULT="$API_BASE_URL"
if [ "$(printf '%s' "$WORKFLOW_EDITOR_ENABLED" | tr '[:upper:]' '[:lower:]')" = "true" ]; then
  WORKFLOW_EDITOR_MICRO_WEB_DEFAULT="$WORKFLOW_EDITOR_BASE_URL"
fi
WORKFLOW_EDITOR_MICRO_WEB="${DEV_E2E_WORKFLOW_EDITOR_MICRO_WEB:-$WORKFLOW_EDITOR_MICRO_WEB_DEFAULT}"
OTHER_MICRO_WEB="${DEV_E2E_OTHER_MICRO_WEB:-$API_BASE_URL}"
WORKFLOW_EDITOR_API_TARGET="${DEV_E2E_WORKFLOW_EDITOR_API_TARGET:-$API_BASE_URL}"
PLAYWRIGHT_RUNNER_DIR="$WORKTREE_ROOT/tmp/playwright-it/runner"

STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
FINISHED_AT=""
FAILURE_STAGE=""
FAILURE_REASON=""
RESULT_OK="false"
BACKEND_PID=""
FRONTEND_PID=""
LOGICFLOW_EDITOR_PID=""
WORKFLOW_EDITOR_PID=""
BACKEND_REUSED="false"
FRONTEND_REUSED="false"
LOGICFLOW_EDITOR_REUSED="false"
WORKFLOW_EDITOR_REUSED="false"
PROBE_PAYLOAD=""
PROBE_FAILURE_REASON=""

json_escape() {
  printf '%s' "$1" | awk 'BEGIN { ORS = "" } {
    gsub(/\\/, "\\\\");
    gsub(/"/, "\\\"");
    gsub(/\r/, "\\r");
    gsub(/\t/, "\\t");
    if (NR > 1) {
      printf "\\n";
    }
    printf "%s", $0;
  }'
}

json_string() {
  printf '"%s"' "$(json_escape "$1")"
}

json_nullable_string() {
  if [ -n "${1:-}" ]; then
    json_string "$1"
  else
    printf 'null'
  fi
}

json_bool() {
  if [ "${1:-false}" = "true" ]; then
    printf 'true'
  else
    printf 'false'
  fi
}

normalize_bool() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    true|1|yes)
      printf 'true'
      ;;
    false|0|no|'')
      printf 'false'
      ;;
    *)
      return 1
      ;;
  esac
}

normalize_non_negative_int() {
  case "${1:-}" in
    ''|*[!0-9]*)
      return 1
      ;;
    *)
      printf '%s' "$1"
      ;;
  esac
}

single_line() {
  printf '%s' "${1:-}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g; s/^ //; s/ $//'
}

write_result() {
  mkdir -p "$(dirname "$RESULT_FILE")"
  FINISHED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  {
    printf '{\n'
    printf '  "ok": %s,\n' "$(json_bool "$RESULT_OK")"
    printf '  "startedAt": %s,\n' "$(json_string "$STARTED_AT")"
    printf '  "finishedAt": %s,\n' "$(json_string "$FINISHED_AT")"
    printf '  "workspace": %s,\n' "$(json_string "$WORKTREE_ROOT")"
    printf '  "primaryRepoRoot": %s,\n' "$(json_string "$PRIMARY_REPO_ROOT")"
    printf '  "baseUrl": %s,\n' "$(json_string "$BASE_URL")"
    printf '  "apiBaseUrl": %s,\n' "$(json_string "$API_BASE_URL")"
    printf '  "backendPid": %s,\n' "$(json_nullable_string "$BACKEND_PID")"
    printf '  "frontendPid": %s,\n' "$(json_nullable_string "$FRONTEND_PID")"
    printf '  "logicflowEditorPid": %s,\n' "$(json_nullable_string "$LOGICFLOW_EDITOR_PID")"
    printf '  "workflowEditorPid": %s,\n' "$(json_nullable_string "$WORKFLOW_EDITOR_PID")"
    printf '  "microFrontends": {\n'
    printf '    "debugConsole": %s,\n' "$(json_string "$DEBUG_CONSOLE_MICRO_WEB")"
    printf '    "logicflowEditor": %s,\n' "$(json_string "$LOGICFLOW_EDITOR_MICRO_WEB")"
    printf '    "workflowEditor": %s,\n' "$(json_string "$WORKFLOW_EDITOR_MICRO_WEB")"
    printf '    "other": %s\n' "$(json_string "$OTHER_MICRO_WEB")"
    printf '  },\n'
    printf '  "artifacts": {\n'
    printf '    "backendLog": %s,\n' "$(json_string "$BACKEND_LOG_FILE")"
    printf '    "backendPidFile": %s,\n' "$(json_string "$BACKEND_PID_FILE")"
    printf '    "frontendLog": %s,\n' "$(json_string "$FRONTEND_LOG_FILE")"
    printf '    "frontendPidFile": %s,\n' "$(json_string "$FRONTEND_PID_FILE")"
    printf '    "logicflowEditorLog": %s,\n' "$(json_string "$LOGICFLOW_EDITOR_LOG_FILE")"
    printf '    "logicflowEditorPidFile": %s,\n' "$(json_string "$LOGICFLOW_EDITOR_PID_FILE")"
    printf '    "workflowEditorLog": %s,\n' "$(json_string "$WORKFLOW_EDITOR_LOG_FILE")"
    printf '    "workflowEditorPidFile": %s,\n' "$(json_string "$WORKFLOW_EDITOR_PID_FILE")"
    printf '    "resultFile": %s\n' "$(json_string "$RESULT_FILE")"
    printf '  },\n'
    printf '  "failure": '
    if [ -n "$FAILURE_STAGE" ] || [ -n "$FAILURE_REASON" ]; then
      printf '{\n'
      printf '    "stage": %s,\n' "$(json_nullable_string "$FAILURE_STAGE")"
      printf '    "reason": %s\n' "$(json_nullable_string "$FAILURE_REASON")"
      printf '  }\n'
    else
      printf 'null\n'
    fi
    printf '}\n'
  } > "$RESULT_FILE"
}

finish_and_exit() {
  write_result
  exit "$1"
}

fail_with() {
  FAILURE_STAGE="$1"
  FAILURE_REASON="$2"
  RESULT_OK="false"
  finish_and_exit "$3"
}

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail_with "preflight" "required command not found: $1" 10
  fi
}

ensure_file() {
  if [ ! -f "$1" ]; then
    fail_with "preflight" "required file not found: $1" 10
  fi
}

ensure_dir() {
  if [ ! -d "$1" ]; then
    fail_with "preflight" "required directory not found: $1" 10
  fi
}

kill_pid_file_if_running() {
  local pid_file="$1"
  if [ ! -f "$pid_file" ]; then
    return 0
  fi

  local pid=""
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    rm -f "$pid_file"
    return 0
  fi

  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
  fi
  rm -f "$pid_file"
}

reset_bootstrap_artifacts() {
  mkdir -p \
    "$(dirname "$BACKEND_LOG_FILE")" \
    "$(dirname "$FRONTEND_LOG_FILE")" \
    "$(dirname "$LOGICFLOW_EDITOR_LOG_FILE")" \
    "$(dirname "$WORKFLOW_EDITOR_LOG_FILE")"
  : > "$BACKEND_LOG_FILE"
  : > "$FRONTEND_LOG_FILE"
  : > "$LOGICFLOW_EDITOR_LOG_FILE"
  : > "$WORKFLOW_EDITOR_LOG_FILE"
}

check_port_listening() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

get_port_listener_pid() {
  lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
}

describe_pid_command() {
  ps -p "$1" -o command= 2>/dev/null | sed 's/^[[:space:]]*//'
}

ensure_port_available() {
  local port="$1"
  local stage="$2"
  local exit_code="$3"
  local pid=""
  local command=""

  pid="$(get_port_listener_pid "$port")"
  if [ -z "$pid" ]; then
    return 0
  fi

  command="$(describe_pid_command "$pid")"
  if [ -n "$command" ]; then
    fail_with "$stage" "${stage} port ${port} already occupied before launch by pid ${pid}: ${command}" "$exit_code"
  fi

  fail_with "$stage" "${stage} port ${port} already occupied before launch by pid ${pid}" "$exit_code"
}

wait_for_port() {
  local port="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if check_port_listening "$port"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_log_line() {
  local log_file="$1"
  local pattern="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -f "$log_file" ] && grep -q "$pattern" "$log_file"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_log_pattern() {
  local log_file="$1"
  local pattern="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -f "$log_file" ] && grep -Eq "$pattern" "$log_file"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

curl_json() {
  curl -fsS "$@"
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

extract_access_token() {
  printf '%s' "$1" | tr -d '\n' | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*{[^}]*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

wait_for_json_probe() {
  local failure_reason="$1"
  local payload_pattern="$2"
  shift 2

  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local payload=""
  local stderr_file=""
  local curl_stderr=""
  local last_observation=""

  PROBE_PAYLOAD=""
  PROBE_FAILURE_REASON="$failure_reason"

  while [ "$SECONDS" -lt "$deadline" ]; do
    stderr_file="$RESULT_DIR/.curl-stderr.$$"
    : > "$stderr_file"
    if payload="$(curl_json "$@" 2>"$stderr_file")"; then
      :
    else
      payload=""
    fi
    curl_stderr="$(cat "$stderr_file" 2>/dev/null || true)"
    rm -f "$stderr_file"

    if [ -n "$payload" ] && printf '%s' "$payload" | grep -q "$payload_pattern"; then
      PROBE_PAYLOAD="$payload"
      PROBE_FAILURE_REASON=""
      return 0
    fi

    if [ -n "$payload" ]; then
      last_observation="last payload: $(single_line "$payload")"
    elif [ -n "$curl_stderr" ]; then
      last_observation="last curl error: $(single_line "$curl_stderr")"
    else
      last_observation="last probe returned no payload"
    fi
    sleep 2
  done

  if [ -n "$last_observation" ]; then
    PROBE_FAILURE_REASON="${failure_reason}; ${last_observation}"
  fi
  return 1
}

wait_for_http_probe() {
  local failure_reason="$1"
  local payload_pattern="$2"
  local url="$3"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local payload=""
  local stderr_file=""
  local curl_stderr=""
  local last_observation=""

  PROBE_PAYLOAD=""
  PROBE_FAILURE_REASON="$failure_reason"

  while [ "$SECONDS" -lt "$deadline" ]; do
    stderr_file="$RESULT_DIR/.curl-stderr.$$"
    : > "$stderr_file"
    if payload="$(curl_json "$url" 2>"$stderr_file")"; then
      :
    else
      payload=""
    fi
    curl_stderr="$(cat "$stderr_file" 2>/dev/null || true)"
    rm -f "$stderr_file"

    if [ -n "$payload" ] && printf '%s' "$payload" | grep -q "$payload_pattern"; then
      PROBE_PAYLOAD="$payload"
      PROBE_FAILURE_REASON=""
      return 0
    fi

    if [ -n "$payload" ]; then
      last_observation="last payload: $(single_line "$payload")"
    elif [ -n "$curl_stderr" ]; then
      last_observation="last curl error: $(single_line "$curl_stderr")"
    else
      last_observation="last probe returned no payload"
    fi
    sleep 2
  done

  if [ -n "$last_observation" ]; then
    PROBE_FAILURE_REASON="${failure_reason}; ${last_observation}"
  fi
  return 1
}

wait_for_backend_readiness() {
  if ! wait_for_port "$BACKEND_PORT"; then
    fail_with "backend" "backend port ${BACKEND_PORT} did not start listening" 30
  fi
  if ! wait_for_log_pattern "$BACKEND_LOG_FILE" "Tomcat started on port(\\(s\\))?[: ]${BACKEND_PORT}"; then
    fail_with "backend" "backend log missing Tomcat startup marker" 30
  fi
  if ! wait_for_log_line "$BACKEND_LOG_FILE" "Started OuroborosDevelopApplication"; then
    fail_with "backend" "backend log missing application startup marker" 30
  fi

  probe_backend_api_readiness
}

probe_backend_api_readiness() {
  local login_payload=""
  local access_token=""

  if ! wait_for_json_probe \
    "login readiness probe failed" \
    '"code"[[:space:]]*:[[:space:]]*0' \
    -H 'Content-Type: application/json' \
    -X POST \
    -d '{"username":"admin","password":"666666"}' \
    "${API_BASE_URL}/api/auth/login"; then
    fail_with "backend" "$PROBE_FAILURE_REASON" 30
  fi
  login_payload="$PROBE_PAYLOAD"
  access_token="$(extract_access_token "$login_payload")"
  if [ -z "$access_token" ]; then
    fail_with "backend" "login readiness probe succeeded but access token is missing from response" 30
  fi

  if ! wait_for_json_probe \
    "docker image options readiness probe failed" \
    '"code"[[:space:]]*:[[:space:]]*0' \
    -H "Authorization: Bearer ${access_token}" \
    "${API_BASE_URL}/api/app/container/images?backendType=DOCKER"; then
    fail_with "backend" "$PROBE_FAILURE_REASON" 30
  fi
}

wait_for_frontend_readiness() {
  local html=""

  if ! wait_for_port "$FRONTEND_PORT"; then
    fail_with "frontend" "frontend port ${FRONTEND_PORT} did not start listening" 40
  fi
  if ! wait_for_log_line "$FRONTEND_LOG_FILE" "Project is running at:"; then
    fail_with "frontend" "frontend log missing webpack-dev-server startup marker" 40
  fi
  if ! wait_for_log_line "$FRONTEND_LOG_FILE" "Loopback: http://127.0.0.1:${FRONTEND_PORT}/"; then
    fail_with "frontend" "frontend log missing bound url marker" 40
  fi
  if [ -f "$FRONTEND_LOG_FILE" ] && grep -Eq 'ERROR in|Failed to compile' "$FRONTEND_LOG_FILE"; then
    fail_with "frontend" "frontend log contains compilation errors" 40
  fi

  html="$(curl_json "${BASE_URL}/")" || fail_with "frontend" "frontend root probe failed" 40
  if ! printf '%s' "$html" | grep -q 'ouroboros-mothership-app'; then
    fail_with "frontend" "frontend root probe missing app shell marker" 40
  fi

  if [ "$FRONTEND_SETTLE_SECONDS" -gt 0 ]; then
    local deadline=$((SECONDS + FRONTEND_SETTLE_SECONDS))
    while [ "$SECONDS" -lt "$deadline" ]; do
      if [ -f "$FRONTEND_LOG_FILE" ] && grep -Eq 'ERROR in|Failed to compile' "$FRONTEND_LOG_FILE"; then
        fail_with "frontend" "frontend log contains compilation errors" 40
      fi
      if ! check_port_listening "$FRONTEND_PORT"; then
        fail_with "frontend" "frontend port ${FRONTEND_PORT} stopped listening during settle window" 40
      fi
      sleep 1
    done
  fi

  if [ -f "$FRONTEND_LOG_FILE" ] && grep -Eq 'ERROR in|Failed to compile' "$FRONTEND_LOG_FILE"; then
    fail_with "frontend" "frontend log contains compilation errors" 40
  fi
}

wait_for_workflow_editor_readiness() {
  if ! wait_for_port "$WORKFLOW_EDITOR_PORT"; then
    fail_with "workflow-editor" "workflow editor port ${WORKFLOW_EDITOR_PORT} did not start listening" 35
  fi
  if ! wait_for_log_line "$WORKFLOW_EDITOR_LOG_FILE" "Project is running at:"; then
    fail_with "workflow-editor" "workflow editor log missing webpack-dev-server startup marker" 35
  fi
  if ! wait_for_log_line "$WORKFLOW_EDITOR_LOG_FILE" "Loopback: ${WORKFLOW_EDITOR_BASE_URL}/"; then
    fail_with "workflow-editor" "workflow editor log missing bound url marker" 35
  fi
  if [ -f "$WORKFLOW_EDITOR_LOG_FILE" ] && grep -Eq 'ERROR in|Failed to compile' "$WORKFLOW_EDITOR_LOG_FILE"; then
    fail_with "workflow-editor" "workflow editor log contains compilation errors" 35
  fi
  if ! wait_for_http_probe \
    "workflow editor root probe failed" \
    "工作流图形编辑器" \
    "${WORKFLOW_EDITOR_BASE_URL}/micro-workflow-editor/"; then
    fail_with "workflow-editor" "$PROBE_FAILURE_REASON" 35
  fi
}

wait_for_logicflow_editor_readiness() {
  if ! wait_for_port "$LOGICFLOW_EDITOR_PORT"; then
    fail_with "logicflow-editor" "logicflow editor port ${LOGICFLOW_EDITOR_PORT} did not start listening" 36
  fi
  if ! wait_for_log_line "$LOGICFLOW_EDITOR_LOG_FILE" "Project is running at:"; then
    fail_with "logicflow-editor" "logicflow editor log missing webpack-dev-server startup marker" 36
  fi
  if ! wait_for_log_line "$LOGICFLOW_EDITOR_LOG_FILE" "Loopback: ${LOGICFLOW_EDITOR_BASE_URL}/"; then
    fail_with "logicflow-editor" "logicflow editor log missing bound url marker" 36
  fi
  if [ -f "$LOGICFLOW_EDITOR_LOG_FILE" ] && grep -Eq 'ERROR in|Failed to compile' "$LOGICFLOW_EDITOR_LOG_FILE"; then
    fail_with "logicflow-editor" "logicflow editor log contains compilation errors" 36
  fi
  if ! wait_for_http_probe \
    "logicflow editor root probe failed" \
    "logicflow-container" \
    "${LOGICFLOW_EDITOR_BASE_URL}/micro-logicflow-editor/"; then
    fail_with "logicflow-editor" "$PROBE_FAILURE_REASON" 36
  fi
}

reuse_existing_frontend_if_running() {
  local pid=""
  pid="$(get_port_listener_pid "$FRONTEND_PORT")"
  if [ -z "$pid" ]; then
    return 1
  fi
  if ! wait_for_http_probe \
    "frontend root probe failed for existing listener" \
    "ouroboros-mothership-app" \
    "${BASE_URL}/"; then
    fail_with "frontend" "frontend port ${FRONTEND_PORT} already occupied by pid ${pid}, but health probe failed: ${PROBE_FAILURE_REASON}" 40
  fi
  FRONTEND_PID="$pid"
  FRONTEND_REUSED="true"
  mkdir -p "$(dirname "$FRONTEND_PID_FILE")"
  printf '%s\n' "$FRONTEND_PID" > "$FRONTEND_PID_FILE"
  return 0
}

reuse_existing_backend_if_running() {
  local pid=""
  pid="$(get_port_listener_pid "$BACKEND_PORT")"
  if [ -z "$pid" ]; then
    return 1
  fi

  probe_backend_api_readiness

  BACKEND_PID="$pid"
  BACKEND_REUSED="true"
  mkdir -p "$(dirname "$BACKEND_PID_FILE")"
  printf '%s\n' "$BACKEND_PID" > "$BACKEND_PID_FILE"
  return 0
}

reuse_existing_workflow_editor_if_running() {
  local pid=""
  pid="$(get_port_listener_pid "$WORKFLOW_EDITOR_PORT")"
  if [ -z "$pid" ]; then
    return 1
  fi
  if ! wait_for_http_probe \
    "workflow editor root probe failed for existing listener" \
    "工作流图形编辑器" \
    "${WORKFLOW_EDITOR_BASE_URL}/micro-workflow-editor/"; then
    fail_with "workflow-editor" "workflow editor port ${WORKFLOW_EDITOR_PORT} already occupied by pid ${pid}, but health probe failed: ${PROBE_FAILURE_REASON}" 35
  fi
  WORKFLOW_EDITOR_PID="$pid"
  WORKFLOW_EDITOR_REUSED="true"
  mkdir -p "$(dirname "$WORKFLOW_EDITOR_PID_FILE")"
  printf '%s\n' "$WORKFLOW_EDITOR_PID" > "$WORKFLOW_EDITOR_PID_FILE"
  return 0
}

reuse_existing_logicflow_editor_if_running() {
  local pid=""
  pid="$(get_port_listener_pid "$LOGICFLOW_EDITOR_PORT")"
  if [ -z "$pid" ]; then
    return 1
  fi
  if ! wait_for_http_probe \
    "logicflow editor root probe failed for existing listener" \
    "logicflow-container" \
    "${LOGICFLOW_EDITOR_BASE_URL}/micro-logicflow-editor/"; then
    fail_with "logicflow-editor" "logicflow editor port ${LOGICFLOW_EDITOR_PORT} already occupied by pid ${pid}, but health probe failed: ${PROBE_FAILURE_REASON}" 36
  fi
  LOGICFLOW_EDITOR_PID="$pid"
  LOGICFLOW_EDITOR_REUSED="true"
  mkdir -p "$(dirname "$LOGICFLOW_EDITOR_PID_FILE")"
  printf '%s\n' "$LOGICFLOW_EDITOR_PID" > "$LOGICFLOW_EDITOR_PID_FILE"
  return 0
}

cleanup_smoke_runtime_container() {
  local delete_status=""

  delete_status="$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE \
    "http://localhost:2375/containers/${SMOKE_RUNTIME_CONTAINER_NAME}?force=1&v=1")" \
    || fail_with "preflight" "failed to remove smoke runtime container via docker api: ${SMOKE_RUNTIME_CONTAINER_NAME}" 10

  case "$delete_status" in
    204|404)
      return 0
      ;;
    *)
      fail_with "preflight" "unexpected docker api status while removing smoke runtime container ${SMOKE_RUNTIME_CONTAINER_NAME}: ${delete_status}" 10
      ;;
  esac
}

run_install_if_needed() {
  if [ "$INSTALL_REACTOR" != "true" ]; then
    return 0
  fi

  (
    cd "$WORKTREE_ROOT"
    eval "$INSTALL_COMMAND"
  ) || fail_with "install" "reactor install command failed: $INSTALL_COMMAND" 20
}

refresh_backend_classpath() {
  local generated_cp=""

  if [ "$REFRESH_CLASSPATH" != "true" ]; then
    return 0
  fi

  (
    cd "$WORKTREE_ROOT"
    mvn -q -pl :ouroboros-develop -DincludeScope=runtime dependency:build-classpath -Dmdep.outputFile="$DEPENDENCY_CP_FILE"
  ) || fail_with "classpath" "failed to refresh backend runtime.classpath" 25

  generated_cp="$(cat "$DEPENDENCY_CP_FILE")"
  if [ -z "$generated_cp" ]; then
    fail_with "classpath" "generated dependency classpath is empty" 25
  fi

  printf '%s:%s\n' "$WORKTREE_ROOT/platform/ouroboros-develop/target/classes" "$generated_cp" > "$BACKEND_CLASSPATH_FILE"
}

normalize_backend_classpath() {
  local expected_prefix="${WORKTREE_ROOT}/platform/ouroboros-develop/target/classes"
  local existing_cp=""
  local dependency_cp=""

  existing_cp="$(cat "$BACKEND_CLASSPATH_FILE")"
  if [ -z "$existing_cp" ]; then
    fail_with "classpath" "backend runtime.classpath is empty" 25
  fi

  case "$existing_cp" in
    "${expected_prefix}:"*)
      return 0
      ;;
    *:*)
      dependency_cp="${existing_cp#*:}"
      ;;
    *)
      fail_with "classpath" "backend runtime.classpath is missing dependency entries" 25
      ;;
  esac

  if [ -L "$BACKEND_CLASSPATH_FILE" ]; then
    rm -f "$BACKEND_CLASSPATH_FILE"
  fi

  printf '%s:%s\n' "$expected_prefix" "$dependency_cp" > "$BACKEND_CLASSPATH_FILE"
}

ensure_backend_entrypoint_class() {
  local entrypoint="$WORKTREE_ROOT/platform/ouroboros-develop/target/classes/com/ouroboros/develop/OuroborosDevelopApplication.class"
  if [ ! -f "$entrypoint" ]; then
    fail_with "classpath" "backend entrypoint class not found after build: $entrypoint" 25
  fi
}

launch_backend() {
  mkdir -p "$(dirname "$BACKEND_LOG_FILE")" "$(dirname "$BACKEND_PID_FILE")" "$BACKEND_DATA_ROOT" "$BACKEND_DOCUMENT_ROOT/tmp"
  : > "$BACKEND_LOG_FILE"

  local metadata_arg=()
  if [ -n "$APP_METADATA_ROOT" ]; then
    metadata_arg=("--ouroboros.metadata.path=${APP_METADATA_ROOT}")
  fi

  BACKEND_PID="$(
    launch_detached_process "$BACKEND_LOG_FILE" \
      /usr/bin/env java -cp "$(cat "$BACKEND_CLASSPATH_FILE")" \
      com.ouroboros.develop.OuroborosDevelopApplication \
      --server.port="${BACKEND_PORT}" \
      --spring.config.additional-location="file:${DEBUG_CONFIG_FILE}" \
      --spring.main.allow-bean-definition-overriding=true \
      --spring.main.allow-circular-references=true \
      --root-path="${BACKEND_DATA_ROOT}" \
      --ouroboros.document-management.storage.filesystem.base-path="${BACKEND_DOCUMENT_ROOT}" \
      --ouroboros.document-management.storage.filesystem.temp-path="${BACKEND_DOCUMENT_ROOT}/tmp" \
      --ouroboros.security.external-auth.publicBaseUrl="${BASE_URL}" \
      "${metadata_arg[@]+"${metadata_arg[@]}"}"
  )" || fail_with "backend" "failed to launch backend process" 30
  if [ -z "$BACKEND_PID" ]; then
    fail_with "backend" "backend launch did not return a pid" 30
  fi
  printf '%s\n' "$BACKEND_PID" > "$BACKEND_PID_FILE"
}

launch_frontend() {
  mkdir -p "$(dirname "$FRONTEND_LOG_FILE")" "$(dirname "$FRONTEND_PID_FILE")"
  : > "$FRONTEND_LOG_FILE"
  local frontend_extra_args=()
  if [ -n "$FRONTEND_EXTRA_ARGS" ]; then
    # shellcheck disable=SC2206
    frontend_extra_args=($FRONTEND_EXTRA_ARGS)
  fi
  FRONTEND_PID="$(
    cd "$WORKTREE_ROOT/frontends/web/main-app"
    launch_detached_process "$FRONTEND_LOG_FILE" \
      /usr/bin/env node scripts/webpack-serve.js \
      --api "${API_BASE_URL}" \
      --debug_console "${DEBUG_CONSOLE_MICRO_WEB}" \
      --logicflow_editor "${LOGICFLOW_EDITOR_MICRO_WEB}" \
      --workflow_editor "${WORKFLOW_EDITOR_MICRO_WEB}" \
      --other "${OTHER_MICRO_WEB}" \
      --host 127.0.0.1 \
      --port "${FRONTEND_PORT}" \
      "${frontend_extra_args[@]+"${frontend_extra_args[@]}"}"
  )" || fail_with "frontend" "failed to launch frontend process" 40
  if [ -z "$FRONTEND_PID" ]; then
    fail_with "frontend" "frontend launch did not return a pid" 40
  fi
  printf '%s\n' "$FRONTEND_PID" > "$FRONTEND_PID_FILE"
}

launch_logicflow_editor() {
  mkdir -p "$(dirname "$LOGICFLOW_EDITOR_LOG_FILE")" "$(dirname "$LOGICFLOW_EDITOR_PID_FILE")"
  : > "$LOGICFLOW_EDITOR_LOG_FILE"
  LOGICFLOW_EDITOR_PID="$(
    cd "$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor"
    launch_detached_process "$LOGICFLOW_EDITOR_LOG_FILE" \
      /usr/bin/env npm run dev -- \
      --host 127.0.0.1 \
      --port "${LOGICFLOW_EDITOR_PORT}"
  )" || fail_with "logicflow-editor" "failed to launch logicflow editor process" 36
  if [ -z "$LOGICFLOW_EDITOR_PID" ]; then
    fail_with "logicflow-editor" "logicflow editor launch did not return a pid" 36
  fi
  printf '%s\n' "$LOGICFLOW_EDITOR_PID" > "$LOGICFLOW_EDITOR_PID_FILE"
}

launch_workflow_editor() {
  mkdir -p "$(dirname "$WORKFLOW_EDITOR_LOG_FILE")" "$(dirname "$WORKFLOW_EDITOR_PID_FILE")"
  : > "$WORKFLOW_EDITOR_LOG_FILE"
  WORKFLOW_EDITOR_PID="$(
    cd "$WORKTREE_ROOT/frontends/web/ouroboros-approval-workflow-designer"
    launch_detached_process "$WORKFLOW_EDITOR_LOG_FILE" \
      /usr/bin/env "WORKFLOW_EDITOR_API_TARGET=${WORKFLOW_EDITOR_API_TARGET}" npm run serve -- \
      --host 127.0.0.1 \
      --port "${WORKFLOW_EDITOR_PORT}"
  )" || fail_with "workflow-editor" "failed to launch workflow editor process" 35
  if [ -z "$WORKFLOW_EDITOR_PID" ]; then
    fail_with "workflow-editor" "workflow editor launch did not return a pid" 35
  fi
  printf '%s\n' "$WORKFLOW_EDITOR_PID" > "$WORKFLOW_EDITOR_PID_FILE"
}

INSTALL_REACTOR="$(normalize_bool "$INSTALL_REACTOR")" || fail_with "preflight" "invalid DEV_E2E_INSTALL_REACTOR value" 10
REFRESH_CLASSPATH="$(normalize_bool "$REFRESH_CLASSPATH")" || fail_with "preflight" "invalid DEV_E2E_REFRESH_CLASSPATH value" 10
LOGICFLOW_EDITOR_ENABLED="$(normalize_bool "$LOGICFLOW_EDITOR_ENABLED")" || fail_with "preflight" "invalid DEV_E2E_LOGICFLOW_EDITOR_ENABLED value" 10
WORKFLOW_EDITOR_ENABLED="$(normalize_bool "$WORKFLOW_EDITOR_ENABLED")" || fail_with "preflight" "invalid DEV_E2E_WORKFLOW_EDITOR_ENABLED value" 10
REUSE_EXISTING_BACKEND="$(normalize_bool "$REUSE_EXISTING_BACKEND")" || fail_with "preflight" "invalid DEV_E2E_REUSE_EXISTING_BACKEND value" 10
REUSE_EXISTING_FRONTEND="$(normalize_bool "$REUSE_EXISTING_FRONTEND")" || fail_with "preflight" "invalid DEV_E2E_REUSE_EXISTING_FRONTEND value" 10
REUSE_EXISTING_LOGICFLOW_EDITOR="$(normalize_bool "$REUSE_EXISTING_LOGICFLOW_EDITOR")" || fail_with "preflight" "invalid DEV_E2E_REUSE_EXISTING_LOGICFLOW_EDITOR value" 10
REUSE_EXISTING_WORKFLOW_EDITOR="$(normalize_bool "$REUSE_EXISTING_WORKFLOW_EDITOR")" || fail_with "preflight" "invalid DEV_E2E_REUSE_EXISTING_WORKFLOW_EDITOR value" 10
FRONTEND_SETTLE_SECONDS="$(normalize_non_negative_int "$FRONTEND_SETTLE_SECONDS")" || fail_with "preflight" "invalid DEV_E2E_FRONTEND_SETTLE_SECONDS value" 10

mkdir -p "$RESULT_DIR"

need_command curl
need_command lsof
need_command java
need_command node
need_command python3
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ] || [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  need_command npm
fi
if [ "$INSTALL_REACTOR" = "true" ] || [ "$REFRESH_CLASSPATH" = "true" ]; then
  need_command mvn
fi

ensure_dir "$WORKTREE_ROOT/platform/ouroboros-develop/target/classes"
ensure_dir "$WORKTREE_ROOT/frontends/web/main-app/node_modules"
if [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  ensure_dir "$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor/node_modules"
fi
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ]; then
  ensure_dir "$WORKTREE_ROOT/frontends/web/ouroboros-approval-workflow-designer/node_modules"
fi
ensure_file "$DEBUG_CONFIG_FILE"
ensure_file "$BACKEND_CLASSPATH_FILE"
if [ -n "$APP_METADATA_ROOT" ]; then
  ensure_dir "$APP_METADATA_ROOT"
fi

docker_ping="$(curl_json "http://localhost:2375/_ping")" || fail_with "preflight" "docker api probe failed at http://localhost:2375/_ping" 10
if [ "$docker_ping" != "OK" ]; then
  fail_with "preflight" "docker api probe returned unexpected payload: $docker_ping" 10
fi

cleanup_smoke_runtime_container
kill_pid_file_if_running "$BACKEND_PID_FILE"
kill_pid_file_if_running "$FRONTEND_PID_FILE"
kill_pid_file_if_running "$LOGICFLOW_EDITOR_PID_FILE"
kill_pid_file_if_running "$WORKFLOW_EDITOR_PID_FILE"
reset_bootstrap_artifacts
if [ "$REUSE_EXISTING_BACKEND" = "true" ] && reuse_existing_backend_if_running; then
  :
else
  ensure_port_available "$BACKEND_PORT" "backend" 30
fi
if [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  if [ "$REUSE_EXISTING_LOGICFLOW_EDITOR" = "true" ] && reuse_existing_logicflow_editor_if_running; then
    :
  else
    ensure_port_available "$LOGICFLOW_EDITOR_PORT" "logicflow-editor" 36
  fi
fi
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ]; then
  if [ "$REUSE_EXISTING_WORKFLOW_EDITOR" = "true" ] && reuse_existing_workflow_editor_if_running; then
    :
  else
    ensure_port_available "$WORKFLOW_EDITOR_PORT" "workflow-editor" 35
  fi
fi
if [ "$REUSE_EXISTING_FRONTEND" = "true" ] && reuse_existing_frontend_if_running; then
  :
else
  ensure_port_available "$FRONTEND_PORT" "frontend" 40
fi

if [ "$BACKEND_REUSED" != "true" ]; then
  run_install_if_needed
  refresh_backend_classpath
  normalize_backend_classpath
  ensure_backend_entrypoint_class
  launch_backend
  wait_for_backend_readiness
fi
if [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  if [ "$LOGICFLOW_EDITOR_REUSED" != "true" ]; then
    launch_logicflow_editor
    wait_for_logicflow_editor_readiness
  fi
fi
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ]; then
  if [ "$WORKFLOW_EDITOR_REUSED" != "true" ]; then
    launch_workflow_editor
    wait_for_workflow_editor_readiness
  fi
fi
if [ "$FRONTEND_REUSED" != "true" ]; then
  launch_frontend
  wait_for_frontend_readiness
fi
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ]; then
  if ! wait_for_http_probe \
    "frontend proxy to workflow editor failed" \
    "工作流图形编辑器" \
    "${BASE_URL}/micro-workflow-editor/"; then
    fail_with "frontend" "$PROBE_FAILURE_REASON" 40
  fi
fi
if [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  if ! wait_for_http_probe \
    "frontend proxy to logicflow editor failed" \
    "logicflow-container" \
    "${BASE_URL}/micro-logicflow-editor/"; then
    fail_with "frontend" "$PROBE_FAILURE_REASON" 40
  fi
fi

RESULT_OK="true"
write_result

printf 'Local bootstrap completed.\n'
printf 'E2E_BASE_URL=%s\n' "$BASE_URL"
printf 'E2E_API_BASE_URL=%s\n' "$API_BASE_URL"
printf 'E2E_USERNAME=admin\n'
printf 'E2E_PASSWORD=666666\n'
if [ "$LOGICFLOW_EDITOR_ENABLED" = "true" ]; then
  printf 'E2E_LOGICFLOW_EDITOR_URL=%s\n' "${LOGICFLOW_EDITOR_BASE_URL}/micro-logicflow-editor/"
fi
if [ "$WORKFLOW_EDITOR_ENABLED" = "true" ]; then
  printf 'E2E_WORKFLOW_EDITOR_URL=%s\n' "${WORKFLOW_EDITOR_BASE_URL}/micro-workflow-editor/"
fi
printf 'PLAYWRIGHT_RUNNER_DIR=%s\n' "$PLAYWRIGHT_RUNNER_DIR"
if [ -n "$APP_METADATA_ROOT" ]; then
  printf 'OUROBOROS_METADATA_PATH=%s\n' "$APP_METADATA_ROOT"
fi
printf 'RESULT_FILE=%s\n' "$RESULT_FILE"
