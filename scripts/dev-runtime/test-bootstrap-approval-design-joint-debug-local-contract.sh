#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/dev-runtime/bootstrap-approval-design-joint-debug-local.sh"
TMP_DIR="$(mktemp -d)"
WORKTREE_ROOT="$TMP_DIR/worktree"
PRIMARY_REPO_ROOT="$TMP_DIR/primary-repo"
FAKE_BIN="$TMP_DIR/bin"
STATE_DIR="$TMP_DIR/state"
STDOUT_FILE="$TMP_DIR/stdout.txt"
STDERR_FILE="$TMP_DIR/stderr.txt"

mkdir -p \
  "$WORKTREE_ROOT/platform/ouroboros-develop/target/classes/com/ouroboros/develop" \
  "$WORKTREE_ROOT/frontends/web/main-app/node_modules/.bin" \
  "$WORKTREE_ROOT/frontends/web/main-app/scripts" \
  "$WORKTREE_ROOT/frontends/web/ouroboros-approval-workflow-designer/node_modules/.bin" \
  "$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor/node_modules/.bin" \
  "$WORKTREE_ROOT/tmp/playwright-it/backend" \
  "$PRIMARY_REPO_ROOT/platform/ouroboros-develop" \
  "$FAKE_BIN" \
  "$STATE_DIR"

trap 'rm -rf "$TMP_DIR"' EXIT

printf '/tmp/fake/stale-classpath-entry' > "$WORKTREE_ROOT/tmp/playwright-it/backend/runtime.classpath"
printf 'debug-config' > "$PRIMARY_REPO_ROOT/platform/ouroboros-develop/application-debug.yml"
: > "$WORKTREE_ROOT/platform/ouroboros-develop/target/classes/com/ouroboros/develop/OuroborosDevelopApplication.class"

cat > "$FAKE_BIN/mvn" <<'EOF'
#!/bin/bash

set -euo pipefail

printf '%s\n' "$*" >> "${FAKE_STATE_DIR:?}/mvn.log"

case "$*" in
  *"dependency:build-classpath"*)
    output_file=""
    for arg in "$@"; do
      case "$arg" in
        -Dmdep.outputFile=*)
          output_file="${arg#-Dmdep.outputFile=}"
          ;;
      esac
    done
    mkdir -p "$(dirname "$output_file")"
    printf '/tmp/fake/dependencies/ouroboros-dev-runtime-core.jar:/tmp/fake/dependencies/other.jar' > "$output_file"
    ;;
esac

exit 0
EOF

cat > "$FAKE_BIN/java" <<'EOF'
#!/bin/bash

set -euo pipefail

printf '%s\n' "$*" > "${FAKE_STATE_DIR:?}/java.log"
touch "${FAKE_STATE_DIR:?}/backend-listening"
printf 'Tomcat started on port(s): 18880 (http)\n'
printf 'Started OuroborosDevelopApplication\n'
exit 0
EOF

cat > "$FAKE_BIN/node" <<'EOF'
#!/bin/bash

set -euo pipefail

{
  printf 'DEV_E2E_REUSE_EXISTING_FRONTEND=%s\n' "${DEV_E2E_REUSE_EXISTING_FRONTEND:-}"
  printf '%s\n' "$*"
} > "${FAKE_STATE_DIR:?}/node.log"
touch "${FAKE_STATE_DIR:?}/frontend-listening"
printf 'Project is running at:\n'
printf 'Loopback: http://127.0.0.1:18081/\n'
exit 0
EOF

cat > "$FAKE_BIN/npm" <<'EOF'
#!/bin/bash

set -euo pipefail

{
  printf 'PWD=%s\n' "$PWD"
  printf 'WORKFLOW_EDITOR_API_TARGET=%s\n' "${WORKFLOW_EDITOR_API_TARGET:-}"
  printf '%s\n' "$*"
} >> "${FAKE_STATE_DIR:?}/npm.log"

case "$PWD" in
  */frontends/web/ouroboros-approval-workflow-designer)
    touch "${FAKE_STATE_DIR:?}/workflow-editor-listening"
    printf 'Project is running at:\n'
    printf 'Loopback: http://127.0.0.1:18083/\n'
    ;;
  */frontends/web/ouroboros-logicflow-editor)
    touch "${FAKE_STATE_DIR:?}/logicflow-editor-listening"
    printf 'Project is running at:\n'
    printf 'Loopback: http://127.0.0.1:18084/\n'
    ;;
  *)
    printf 'unexpected npm cwd: %s\n' "$PWD" >&2
    exit 99
    ;;
esac
exit 0
EOF

cat > "$FAKE_BIN/lsof" <<'EOF'
#!/bin/bash

set -euo pipefail

case "$*" in
  *18880*)
    if [ -f "${FAKE_STATE_DIR:?}/backend-listening" ]; then
      if [[ "$*" == *"-t"* ]]; then
        printf '12345\n'
        exit 0
      fi
      printf 'java 12345 test 0u IPv4 0 0t0 TCP *:18880 (LISTEN)\n'
      exit 0
    fi
    exit 1
    ;;
  *18081*)
    if [ -f "${FAKE_STATE_DIR:?}/frontend-listening" ]; then
      if [[ "$*" == *"-t"* ]]; then
        printf '23456\n'
        exit 0
      fi
      printf 'node 23456 test 0u IPv4 0 0t0 TCP 127.0.0.1:18081 (LISTEN)\n'
      exit 0
    fi
    exit 1
    ;;
  *18083*)
    if [ -f "${FAKE_STATE_DIR:?}/workflow-editor-listening" ]; then
      if [[ "$*" == *"-t"* ]]; then
        printf '34567\n'
        exit 0
      fi
      printf 'npm 34567 test 0u IPv4 0 0t0 TCP 127.0.0.1:18083 (LISTEN)\n'
      exit 0
    fi
    exit 1
    ;;
  *18084*)
    if [ -f "${FAKE_STATE_DIR:?}/logicflow-editor-listening" ]; then
      if [[ "$*" == *"-t"* ]]; then
        printf '45678\n'
        exit 0
      fi
      printf 'npm 45678 test 0u IPv4 0 0t0 TCP 127.0.0.1:18084 (LISTEN)\n'
      exit 0
    fi
    exit 1
    ;;
  *)
    exit 1
    ;;
esac
EOF

cat > "$FAKE_BIN/ps" <<'EOF'
#!/bin/bash

set -euo pipefail

case "$*" in
  *"23456"*)
    printf 'node fake-main-app-dev-server\n'
    ;;
  *"34567"*)
    printf 'npm fake-workflow-editor-dev-server\n'
    ;;
  *"45678"*)
    printf 'npm fake-logicflow-editor-dev-server\n'
    ;;
  *"12345"*)
    printf 'java fake-backend\n'
    ;;
  *)
    exit 1
    ;;
esac
EOF

cat > "$FAKE_BIN/curl" <<'EOF'
#!/bin/bash

set -euo pipefail

args="$*"
printf '%s\n' "$args" >> "${FAKE_STATE_DIR:?}/curl.log"

case "$args" in
  *"http://localhost:2375/_ping"*)
    printf 'OK'
    ;;
  *"/containers/ouroboros-test-app-playwrightit-admin?force=1&v=1"*)
    printf '404'
    ;;
  *"/api/auth/login"*)
    printf '{"code":0,"data":{"accessToken":{"token":"fake-token"}}}'
    ;;
  *"/api/app/container/images?backendType=DOCKER"*)
    printf '{"code":0,"data":{"items":[{"value":"fake-image:latest","label":"fake-image:latest"}]}}'
    ;;
  *"http://127.0.0.1:18083/micro-workflow-editor/"*)
    printf '<html><head><title>工作流图形编辑器</title></head><body></body></html>'
    ;;
  *"http://127.0.0.1:18084/micro-logicflow-editor/"*)
    printf '<html><head><title>logicflow</title></head><body><div id="logicflow-container"></div></body></html>'
    ;;
  *"http://127.0.0.1:18081/micro-logicflow-editor/"*)
    printf '<html><head><title>logicflow</title></head><body><div id="logicflow-container"></div></body></html>'
    ;;
  *"http://127.0.0.1:18081/micro-workflow-editor/"*)
    printf '<html><head><title>工作流图形编辑器</title></head><body></body></html>'
    ;;
  *"http://127.0.0.1:18081/"*)
    printf '<html><body>ouroboros-mothership-app</body></html>'
    ;;
  *)
    printf 'unexpected curl invocation: %s\n' "$args" >&2
    exit 99
    ;;
esac
EOF

chmod +x "$FAKE_BIN/mvn" "$FAKE_BIN/java" "$FAKE_BIN/node" "$FAKE_BIN/npm" "$FAKE_BIN/lsof" "$FAKE_BIN/ps" "$FAKE_BIN/curl"

set +e
PATH="$FAKE_BIN:$PATH" \
FAKE_STATE_DIR="$STATE_DIR" \
DEV_E2E_WORKTREE_ROOT="$WORKTREE_ROOT" \
DEV_E2E_PRIMARY_REPO_ROOT="$PRIMARY_REPO_ROOT" \
DEV_E2E_TIMEOUT_SECONDS="3" \
DEV_E2E_LOGICFLOW_EDITOR_PORT="18084" \
DEV_E2E_WORKFLOW_EDITOR_PORT="18083" \
"$SCRIPT_PATH" >"$STDOUT_FILE" 2>"$STDERR_FILE"
exit_code=$?
set -e

if [ "$exit_code" -ne 0 ]; then
  echo "expected helper to exit 0, got $exit_code" >&2
  cat "$STDERR_FILE" >&2
  exit 1
fi

if ! grep -q -- "scripts/webpack-serve.js --api http://127.0.0.1:18880 --debug_console http://127.0.0.1:88 --logicflow_editor http://127.0.0.1:18084 --workflow_editor http://127.0.0.1:18083 --other http://127.0.0.1:88 --host 127.0.0.1 --port 18081" "$STATE_DIR/node.log"; then
  echo "expected main app launch command to use split micro-frontend topology" >&2
  exit 1
fi

if ! grep -q -- "^DEV_E2E_REUSE_EXISTING_FRONTEND=false$" "$STATE_DIR/node.log"; then
  echo "expected approval joint debug bootstrap to disable stale frontend reuse" >&2
  exit 1
fi

if ! grep -q -- "^WORKFLOW_EDITOR_API_TARGET=http://127.0.0.1:18880$" "$STATE_DIR/npm.log"; then
  echo "expected workflow editor launch environment to target local backend" >&2
  exit 1
fi

if ! grep -q -- "^run serve -- --host 127.0.0.1 --port 18083$" "$STATE_DIR/npm.log"; then
  echo "expected workflow editor launch command to use the local dev server port" >&2
  exit 1
fi

if ! grep -q -- "^PWD=$WORKTREE_ROOT/frontends/web/ouroboros-logicflow-editor$" "$STATE_DIR/npm.log"; then
  echo "expected logicflow editor to launch from its package root" >&2
  exit 1
fi

if ! grep -q -- "^run dev -- --host 127.0.0.1 --port 18084$" "$STATE_DIR/npm.log"; then
  echo "expected logicflow editor launch command to use the local dev server port" >&2
  exit 1
fi

RESULT_FILE="$WORKTREE_ROOT/tmp/playwright-it/bootstrap/result.json"
if [ ! -f "$RESULT_FILE" ]; then
  echo "expected result.json to be written" >&2
  exit 1
fi

if ! grep -q '"logicflowEditor":[[:space:]]*"http://127.0.0.1:18084"' "$RESULT_FILE"; then
  echo "expected result.json to record the local logicflow editor micro web" >&2
  exit 1
fi

if ! grep -q '"workflowEditor":[[:space:]]*"http://127.0.0.1:18083"' "$RESULT_FILE"; then
  echo "expected result.json to record the local workflow editor micro web" >&2
  exit 1
fi

if ! grep -q '"apiBaseUrl":[[:space:]]*"http://127.0.0.1:18880"' "$RESULT_FILE"; then
  echo "expected result.json to record the local backend api base url" >&2
  exit 1
fi

if ! grep -q 'E2E_LOGICFLOW_EDITOR_URL=http://127.0.0.1:18084/micro-logicflow-editor/' "$STDOUT_FILE"; then
  echo "expected stdout to export the logicflow editor url" >&2
  exit 1
fi

if ! grep -q 'E2E_WORKFLOW_EDITOR_URL=http://127.0.0.1:18083/micro-workflow-editor/' "$STDOUT_FILE"; then
  echo "expected stdout to export the workflow editor url" >&2
  exit 1
fi

if ! grep -q 'E2E_API_BASE_URL=http://127.0.0.1:18880' "$STDOUT_FILE"; then
  echo "expected stdout to export the local backend api base url" >&2
  exit 1
fi

echo "approval design joint debug contract test passed"
