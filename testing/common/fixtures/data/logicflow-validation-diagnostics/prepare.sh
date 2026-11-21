#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[logicflow-validation-fixture] %s\n' "$*"
}

fail() {
  log "$*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../" && pwd)
FIXTURE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

need curl
need jq
need node

SMOKE_PUBLIC_SHELL_URL="${SMOKE_PUBLIC_SHELL_URL:-http://127.0.0.1:18081}"
SMOKE_LOGIN_USERNAME="${SMOKE_LOGIN_USERNAME:-admin}"
SMOKE_LOGIN_PASSWORD="${SMOKE_LOGIN_PASSWORD:-666666}"
SMOKE_APP_NAME="${SMOKE_APP_NAME:-logicflowsmoke}"
SMOKE_APP_TITLE="${SMOKE_APP_TITLE:-逻辑流烟测}"
SMOKE_LOGICFLOW_MODEL_NAME="${SMOKE_LOGICFLOW_MODEL_NAME:-smokevalidationfixture}"
SMOKE_EXPECTED_NODE_NAME="${SMOKE_EXPECTED_NODE_NAME:-caller}"

login_response=$(
  curl -fsS -X POST "${SMOKE_PUBLIC_SHELL_URL}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(jq -cn --arg username "${SMOKE_LOGIN_USERNAME}" --arg password "${SMOKE_LOGIN_PASSWORD}" \
      '{username: $username, password: $password}')"
)

token=$(printf '%s' "${login_response}" | jq -er '.data.accessToken.token')
auth_header="Authorization: Bearer ${token}"

apps_response=$(curl -fsS "${SMOKE_PUBLIC_SHELL_URL}/api/app/" -H "${auth_header}")
if ! printf '%s' "${apps_response}" | jq -e --arg name "${SMOKE_APP_NAME}" \
  '.data.items[]? | select(.appInfo.name == $name)' >/dev/null; then
  log "创建 smoke app: ${SMOKE_APP_NAME}"
  create_app_response=$(
    curl -fsS -X POST "${SMOKE_PUBLIC_SHELL_URL}/api/app" \
      -H "${auth_header}" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn \
        --arg name "${SMOKE_APP_NAME}" \
        --arg title "${SMOKE_APP_TITLE}" \
        --arg description 'logicflow smoke fixture app' \
        '{name: $name, title: $title, description: $description}')"
  )
  printf '%s' "${create_app_response}" | jq -e '.code == 0' >/dev/null \
    || fail "创建 smoke app 失败: ${create_app_response}"
fi

models_response=$(curl -fsS "${SMOKE_PUBLIC_SHELL_URL}/api/dev/${SMOKE_APP_NAME}/logic-flow" -H "${auth_header}")
if ! printf '%s' "${models_response}" | jq -e --arg name "${SMOKE_LOGICFLOW_MODEL_NAME}" \
  '.data[]? | select((.model.name // .name) == $name)' >/dev/null; then
  log "创建 smoke logicflow model: ${SMOKE_LOGICFLOW_MODEL_NAME}"
  create_model_response=$(
    curl -fsS -X POST "${SMOKE_PUBLIC_SHELL_URL}/api/dev/${SMOKE_APP_NAME}/logic-flow" \
      -H "${auth_header}" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn \
        --arg name "${SMOKE_LOGICFLOW_MODEL_NAME}" \
        --arg description 'logicflow validation diagnostics fixture' \
        '{name: $name, description: $description}')"
  )
  printf '%s' "${create_model_response}" | jq -e '.code == 0' >/dev/null \
    || fail "创建 smoke logicflow model 失败: ${create_model_response}"
fi

fixture_payload=$(
  node "${FIXTURE_DIR}/fixturePayload.mjs" "${SMOKE_LOGICFLOW_MODEL_NAME}" "${SMOKE_EXPECTED_NODE_NAME}"
)

update_response=$(
  curl -fsS -X PUT "${SMOKE_PUBLIC_SHELL_URL}/api/dev/${SMOKE_APP_NAME}/logic-flow/${SMOKE_LOGICFLOW_MODEL_NAME}?includeValidationSnapshot=true" \
    -H "${auth_header}" \
    -H 'Content-Type: application/json' \
    -d "${fixture_payload}"
)

printf '%s' "${update_response}" | jq -e '.code == 0' >/dev/null \
  || fail "更新 smoke logicflow model 失败: ${update_response}"
printf '%s' "${update_response}" | jq -e '
  (.data.validationSnapshot.lastValidatedTime // "") != "" and
  (
    ((.data.validationSnapshot.validationDiagnostics // []) | length) > 0 or
    ((.data.validationSnapshot.validationErrors // []) | length) > 0 or
    ((.data.validationSnapshot.validationSummary // "") | length) > 0
  )
' >/dev/null || fail "fixture 未返回 validationSnapshot 诊断信封: ${update_response}"
printf '%s' "${update_response}" | jq -e --arg nodeName "${SMOKE_EXPECTED_NODE_NAME}" \
  '.data.validationSnapshot.validationDiagnostics[]? | select((.nodeNames // []) | index($nodeName))' >/dev/null \
  || fail "fixture 缺少目标节点诊断信息: ${update_response}"

log "fixture 准备完成: ${SMOKE_APP_NAME}/${SMOKE_LOGICFLOW_MODEL_NAME}"
