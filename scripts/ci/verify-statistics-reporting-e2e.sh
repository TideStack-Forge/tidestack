#!/usr/bin/env bash
set -euo pipefail

scenario="statistics-reporting-runtime-query-and-bi"
e2e_manifest="testing/e2e/regression/statistics-reporting/runtime-query-and-bi/e2e.yaml"
evidence_dir="tmp/testing-evidence/statistics-reporting-runtime-query-and-bi"
run_exit_file="$evidence_dir/run.exit"

run_step() {
  local script_id="$1"
  echo "--- running script: ${script_id} ---"
  bash scripts/agent-feedback/run-key-journey-e2e.sh \
    --scenario "$scenario" \
    --mode run \
    --script-id "$script_id"
}

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Required environment variable $name is not set"
    echo "Statistics-reporting E2E gate requires: OB_TEST_BASE_URL, OB_TEST_APP_BASE_URL, OB_TEST_FRONTEND_BASE_URL, OB_TEST_RUNTIME_FRONTEND_BASE_URL, OB_TEST_APP_ROOT"
    echo "RBAC coverage additionally requires: OB_TEST_RESTRICTED_ACCOUNT, OB_TEST_RESTRICTED_PASSWORD"
    exit 1
  fi
}

require_env "OB_TEST_BASE_URL"
require_env "OB_TEST_APP_BASE_URL"
require_env "OB_TEST_FRONTEND_BASE_URL"
require_env "OB_TEST_RUNTIME_FRONTEND_BASE_URL"
require_env "OB_TEST_APP_ROOT"

if [ "${OB_TEST_REQUIRE_RESTRICTED_CREDENTIALS:-false}" = "true" ]; then
  require_env "OB_TEST_RESTRICTED_ACCOUNT"
  require_env "OB_TEST_RESTRICTED_PASSWORD"
fi

if [ ! -f "$e2e_manifest" ]; then
  echo "E2E manifest not found: $e2e_manifest"
  exit 1
fi

mkdir -p "$evidence_dir"
rm -f "$run_exit_file"

echo "=== Statistics-reporting E2E gate: plan mode ==="
bash scripts/agent-feedback/run-key-journey-e2e.sh \
  --scenario "$scenario" \
  --mode plan \
  2>&1 | tee "$evidence_dir/plan.log"

plan_exit=${PIPESTATUS[0]}
if [ "$plan_exit" -ne 0 ]; then
  echo "Plan mode failed with exit code $plan_exit"
  exit "$plan_exit"
fi

echo ""
echo "=== Statistics-reporting E2E gate: run mode ==="

has_restricted_credentials="false"
if [ -n "${OB_TEST_RESTRICTED_ACCOUNT:-}" ] && [ -n "${OB_TEST_RESTRICTED_PASSWORD:-}" ]; then
  has_restricted_credentials="true"
  echo "Restricted-user credentials detected — RBAC denial paths will be exercised"
else
  echo "WARNING: Restricted-user credentials not set — RBAC denial tests will be skipped"
  echo "For full coverage, set OB_TEST_RESTRICTED_ACCOUNT and OB_TEST_RESTRICTED_PASSWORD"
fi

set +e
{
  run_exit=0

  record_step() {
    local script_id="$1"
    local step_exit=0
    run_step "$script_id"
    step_exit=$?
    if [ "$step_exit" -ne 0 ] && [ "$run_exit" -eq 0 ]; then
      run_exit="$step_exit"
    fi
  }

  record_step "statistics-reporting-install-root-playwright"
  record_step "statistics-reporting-fixture-reset"
  record_step "statistics-reporting-fixture-up"
  record_step "statistics-reporting-fixture-check"

  record_step "statistics-reporting-runtime-query"
  record_step "statistics-reporting-runtime-report"
  record_step "statistics-reporting-runtime-dashboard"

  if [ "$has_restricted_credentials" = "true" ]; then
    record_step "statistics-reporting-restricted-fixture-up"
    record_step "statistics-reporting-restricted-fixture-check"
    record_step "statistics-reporting-runtime-rbac"
  else
    echo "--- skipping restricted fixture and RBAC steps ---"
  fi

  printf '%s\n' "$run_exit" > "$run_exit_file"
} 2>&1 | tee "$evidence_dir/run.log"

pipeline_exit=${PIPESTATUS[0]}
set -e

if [ "$pipeline_exit" -ne 0 ]; then
  run_exit="$pipeline_exit"
elif [ -f "$run_exit_file" ]; then
  run_exit=$(cat "$run_exit_file")
else
  echo "Run mode did not record an exit code"
  run_exit=1
fi
rm -f "$run_exit_file"

echo ""
if [ "$run_exit" -eq 0 ]; then
  echo "Statistics-reporting E2E gate PASSED"
else
  echo "Statistics-reporting E2E gate FAILED with exit code $run_exit"
fi

exit "$run_exit"
