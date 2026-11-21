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

touch "${FAKE_STATE_DIR:?}/backend-listening"
printf 'Tomcat started on port(s): 18080 (http)\n'
printf 'Started OuroborosDevelopApplication\n'
exit 0
EOF

cat > "$FAKE_BIN/node" <<'EOF'
#!/bin/bash

set -euo pipefail

touch "${FAKE_STATE_DIR:?}/frontend-listening"
printf 'Project is running at:\n'
printf 'Loopback: http://127.0.0.1:18081/\n'
printf 'ERROR in src/main.ts:1:1\n'
printf 'TS1005: simulated compile failure\n'
exit 0
EOF

cat > "$FAKE_BIN/lsof" <<'EOF'
#!/bin/bash

set -euo pipefail

case "$*" in
  *18080*)
    if [ -f "${FAKE_STATE_DIR:?}/backend-listening" ]; then
      printf 'java 12345 test 0u IPv4 0 0t0 TCP *:18080 (LISTEN)\n'
      exit 0
    fi
    exit 1
    ;;
  *18081*)
    if [ -f "${FAKE_STATE_DIR:?}/frontend-listening" ]; then
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

cat > "$FAKE_BIN/curl" <<'EOF'
#!/bin/bash

set -euo pipefail

args="$*"

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
  *"http://127.0.0.1:18081/"*)
    printf '<html><body>ouroboros-mothership-app</body></html>'
    ;;
  *)
    printf 'unexpected curl invocation: %s\n' "$args" >&2
    exit 99
    ;;
esac
EOF

chmod +x "$FAKE_BIN/mvn" "$FAKE_BIN/java" "$FAKE_BIN/node" "$FAKE_BIN/lsof" "$FAKE_BIN/curl"

set +e
PATH="$FAKE_BIN:$PATH" \
FAKE_STATE_DIR="$STATE_DIR" \
DEV_E2E_WORKTREE_ROOT="$WORKTREE_ROOT" \
DEV_E2E_PRIMARY_REPO_ROOT="$PRIMARY_REPO_ROOT" \
DEV_E2E_TIMEOUT_SECONDS="3" \
DEV_E2E_FRONTEND_SETTLE_SECONDS="1" \
"$SCRIPT_PATH" >"$STDOUT_FILE" 2>"$STDERR_FILE"
exit_code=$?
set -e

if [ "$exit_code" -eq 0 ]; then
  echo "expected helper to fail when frontend compile errors are present" >&2
  cat "$STDOUT_FILE" >&2
  exit 1
fi

RESULT_FILE="$WORKTREE_ROOT/tmp/playwright-it/bootstrap/result.json"
if [ ! -f "$RESULT_FILE" ]; then
  echo "expected result.json to be written on failure" >&2
  exit 1
fi

if ! grep -q '"stage":[[:space:]]*"frontend"' "$RESULT_FILE"; then
  echo "expected frontend failure stage in result.json" >&2
  cat "$RESULT_FILE" >&2
  exit 1
fi

if ! grep -q 'compilation errors' "$RESULT_FILE"; then
  echo "expected compilation error reason in result.json" >&2
  cat "$RESULT_FILE" >&2
  exit 1
fi

echo "frontend error contract test passed"
