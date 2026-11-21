#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh"
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
  "$WORKTREE_ROOT/workspace/dev/test-app/business-model" \
  "$WORKTREE_ROOT/workspace/dev/test-app/ui-schema" \
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
    if [ -z "$output_file" ]; then
      echo "missing mdep.outputFile" >&2
      exit 98
    fi
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
invocation_file="${FAKE_STATE_DIR:?}/java-invocations"
invocations=0
if [ -f "$invocation_file" ]; then
  invocations="$(cat "$invocation_file")"
fi
invocations=$((invocations + 1))
printf '%s\n' "$invocations" > "$invocation_file"
if [ "$invocations" -eq 1 ]; then
  printf 'Tomcat started on port 18080 (http)\n'
else
  printf 'Tomcat started on port(s): 18080 (http)\n'
fi
printf 'Started OuroborosDevelopApplication\n'
exit 0
EOF

cat > "$FAKE_BIN/node" <<'EOF'
#!/bin/bash

set -euo pipefail

printf '%s\n' "$*" > "${FAKE_STATE_DIR:?}/node.log"
touch "${FAKE_STATE_DIR:?}/frontend-listening"
printf 'Project is running at:\n'
printf 'Loopback: http://127.0.0.1:18081/\n'
exit 0
EOF

cat > "$FAKE_BIN/lsof" <<'EOF'
#!/bin/bash

set -euo pipefail

case "$*" in
  *18080*)
    if [ -f "${FAKE_STATE_DIR:?}/backend-listening" ]; then
      if [[ "$*" == *"-t"* ]]; then
        printf '12345\n'
        exit 0
      fi
      printf 'java 12345 test 0u IPv4 0 0t0 TCP *:18080 (LISTEN)\n'
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
    counter_file="${FAKE_STATE_DIR:?}/login-attempts"
    attempts=0
    if [ -f "$counter_file" ]; then
      attempts="$(cat "$counter_file")"
    fi
    attempts=$((attempts + 1))
    printf '%s' "$attempts" > "$counter_file"
    if [ "$attempts" -eq 1 ]; then
      printf 'simulated 503\n' >&2
      exit 22
    fi
    printf '{"code":0,"data":{"accessToken":{"token":"fake-token"}}}'
    ;;
  *"/api/app/container/images?backendType=DOCKER"*)
    if [[ "$args" != *"Authorization: Bearer fake-token"* ]]; then
      printf 'expected Authorization header with login token, got: %s\n' "$args" >&2
      exit 22
    fi
    printf '{"code":0,"data":{"items":[{"value":"fake-image:latest","label":"fake-image:latest"}]}}'
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

chmod +x "$FAKE_BIN/mvn" "$FAKE_BIN/java" "$FAKE_BIN/node" "$FAKE_BIN/lsof" "$FAKE_BIN/ps" "$FAKE_BIN/curl"

set +e
PATH="$FAKE_BIN:$PATH" \
FAKE_STATE_DIR="$STATE_DIR" \
DEV_E2E_WORKTREE_ROOT="$WORKTREE_ROOT" \
DEV_E2E_PRIMARY_REPO_ROOT="$PRIMARY_REPO_ROOT" \
DEV_E2E_TIMEOUT_SECONDS="3" \
FRONTEND_EXTRA_ARGS="--logicflow_editor http://127.0.0.1:19088" \
OB_TEST_APP_ROOT="$WORKTREE_ROOT/workspace/dev/test-app" \
"$SCRIPT_PATH" >"$STDOUT_FILE" 2>"$STDERR_FILE"
exit_code=$?
set -e

if [ "$exit_code" -ne 0 ]; then
  echo "expected helper to exit 0, got $exit_code" >&2
  cat "$STDERR_FILE" >&2
  exit 1
fi

if [ ! -f "$STATE_DIR/mvn.log" ]; then
  echo "expected install command to run" >&2
  exit 1
fi

if ! grep -q -- "-pl :ouroboros-develop -am -DskipTests -Djacoco.skip=true install" "$STATE_DIR/mvn.log"; then
  echo "expected install command to use safe fallback reactor install" >&2
  exit 1
fi

if ! grep -q -- "dependency:build-classpath" "$STATE_DIR/mvn.log"; then
  echo "expected script to refresh runtime.classpath" >&2
  exit 1
fi

if grep -q -- "-am -DincludeScope=runtime dependency:build-classpath" "$STATE_DIR/mvn.log"; then
  echo "expected dependency:build-classpath refresh to avoid reactor mode" >&2
  exit 1
fi

if ! grep -q -- "/containers/ouroboros-test-app-playwrightit-admin?force=1&v=1" "$STATE_DIR/curl.log"; then
  echo "expected script to pre-clean the deterministic smoke runtime container" >&2
  exit 1
fi

if ! grep -q -- "$WORKTREE_ROOT/platform/ouroboros-develop/target/classes:/tmp/fake/dependencies/ouroboros-dev-runtime-core.jar" "$WORKTREE_ROOT/tmp/playwright-it/backend/runtime.classpath"; then
  echo "expected runtime.classpath to be refreshed with target/classes and generated dependencies" >&2
  exit 1
fi

if ! grep -q -- "com.ouroboros.develop.OuroborosDevelopApplication" "$STATE_DIR/java.log"; then
  echo "expected backend launch command to target OuroborosDevelopApplication" >&2
  exit 1
fi

if ! grep -q -- "--server.port=18080" "$STATE_DIR/java.log"; then
  echo "expected backend launch command to contain the backend port" >&2
  exit 1
fi

if ! grep -q -- "--ouroboros.metadata.path=$WORKTREE_ROOT/workspace/dev/test-app" "$STATE_DIR/java.log"; then
  echo "expected backend launch command to align metadata path with OB_TEST_APP_ROOT" >&2
  exit 1
fi

if ! grep -q -- "scripts/webpack-serve.js --api http://127.0.0.1:18080 --debug_console http://127.0.0.1:88 --logicflow_editor http://127.0.0.1:18080 --workflow_editor http://127.0.0.1:18080 --other http://127.0.0.1:18080 --host 127.0.0.1 --port 18081" "$STATE_DIR/node.log"; then
  echo "expected frontend launch command to contain the canonical serve arguments" >&2
  exit 1
fi

if ! grep -q -- "--logicflow_editor http://127.0.0.1:19088" "$STATE_DIR/node.log"; then
  echo "expected frontend launch command to honor legacy FRONTEND_EXTRA_ARGS compatibility" >&2
  exit 1
fi

RESULT_FILE="$WORKTREE_ROOT/tmp/playwright-it/bootstrap/result.json"
if [ ! -f "$RESULT_FILE" ]; then
  echo "expected result.json to be written" >&2
  exit 1
fi

if ! grep -q '"ok":[[:space:]]*true' "$RESULT_FILE"; then
  echo "expected ok=true in result.json" >&2
  exit 1
fi

if ! grep -q '"debugConsole":[[:space:]]*"http://127.0.0.1:88"' "$RESULT_FILE"; then
  echo "expected debug-console-header micro frontend to target 127.0.0.1:88" >&2
  cat "$RESULT_FILE" >&2
  exit 1
fi

if ! grep -q 'E2E_BASE_URL=http://127.0.0.1:18081' "$STDOUT_FILE"; then
  echo "expected stdout to export E2E_BASE_URL" >&2
  exit 1
fi

if ! grep -q 'E2E_API_BASE_URL=http://127.0.0.1:18080' "$STDOUT_FILE"; then
  echo "expected stdout to export E2E_API_BASE_URL" >&2
  exit 1
fi

NO_REUSE_STDOUT_FILE="$TMP_DIR/no-reuse-stdout.txt"
NO_REUSE_STDERR_FILE="$TMP_DIR/no-reuse-stderr.txt"
rm -f "$STATE_DIR/node.log"
rm -f "$WORKTREE_ROOT/tmp/playwright-it/bootstrap/result.json"
rm -f "$STATE_DIR/backend-listening"
set +e
PATH="$FAKE_BIN:$PATH" \
FAKE_STATE_DIR="$STATE_DIR" \
DEV_E2E_WORKTREE_ROOT="$WORKTREE_ROOT" \
DEV_E2E_PRIMARY_REPO_ROOT="$PRIMARY_REPO_ROOT" \
DEV_E2E_TIMEOUT_SECONDS="3" \
DEV_E2E_REUSE_EXISTING_FRONTEND="false" \
DEV_E2E_INSTALL_REACTOR="false" \
DEV_E2E_REFRESH_CLASSPATH="false" \
"$SCRIPT_PATH" >"$NO_REUSE_STDOUT_FILE" 2>"$NO_REUSE_STDERR_FILE"
no_reuse_exit_code=$?
set -e

if [ "$no_reuse_exit_code" -eq 0 ]; then
  echo "expected helper to fail when frontend reuse is disabled and 18081 is occupied" >&2
  exit 1
fi

if [ -f "$STATE_DIR/node.log" ]; then
  echo "expected helper not to launch frontend when 18081 is already occupied and reuse is disabled" >&2
  exit 1
fi

NO_REUSE_RESULT_FILE="$WORKTREE_ROOT/tmp/playwright-it/bootstrap/result.json"
if ! grep -q '"stage":[[:space:]]*"frontend"' "$NO_REUSE_RESULT_FILE"; then
  echo "expected no-reuse result to fail at frontend stage" >&2
  cat "$NO_REUSE_RESULT_FILE" >&2
  exit 1
fi

if ! grep -q 'frontend port 18081 already occupied before launch' "$NO_REUSE_RESULT_FILE"; then
  echo "expected no-reuse result to report occupied frontend port" >&2
  cat "$NO_REUSE_RESULT_FILE" >&2
  exit 1
fi

echo "contract test passed"
