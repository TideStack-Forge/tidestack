#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[approval-workflow-users-seed] %s\n' "$*" >&2
}

fail() {
  log "$*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

need curl
need jq

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../" && pwd)
FIXTURE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
USER_SEED_FILE="${FIXTURE_DIR}/users-and-mappings.json"
ROLE_SEED_FILE="${ROOT_DIR}/testing/common/fixtures/data/approval-workflow-roles-seed/roles-and-permissions.json"

BASE_URL="${OB_TEST_BASE_URL:-${OB_TEST_RUNTIME_FRONTEND_BASE_URL:-http://localhost:80}}"
ADMIN_ACCOUNT="${OB_TEST_ADMIN_ACCOUNT:-admin}"
ADMIN_PASSWORD="${OB_TEST_ADMIN_PASSWORD:-666666}"
AWF_E2E_PASSWORD="${OB_TEST_AWF_PASSWORD:-AwfRegression@2026!}"
IDENTITIES_OUTPUT="${OB_TEST_AWF_IDENTITIES_FILE:-${ROOT_DIR}/tmp/testing-evidence/workflow-approval-flow/approval-workflow-identities.json}"
MODE="reset"

case "${1:-}" in
  ""|--reset|reset|--up|up)
    MODE="reset"
    ;;
  --check|check)
    MODE="check"
    ;;
  *)
    fail "未知参数: $1（支持: --check, --reset）"
    ;;
esac

url_scheme() {
  printf '%s' "${BASE_URL}" | jq -Rr 'try capture("^(?<scheme>[^:]+)://").scheme catch ""'
}

url_host() {
  printf '%s' "${BASE_URL}" | jq -Rr 'try capture("^[^:]+://(?<host>\\[[^]]+\\]|[^/:]+)").host catch ""' | tr -d '[]'
}

is_local_host() {
  local host
  host=$(url_host)
  case "${host}" in
    localhost|127.*|0.0.0.0|::1)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

guard_target() {
  if is_local_host; then
    return 0
  fi

  [[ "${ALLOW_AWF_E2E_MUTATION:-}" == "true" ]] \
    || fail "拒绝在非本地地址执行 approval workflow 用户种子: ${BASE_URL}。如确认是隔离测试环境，请设置 ALLOW_AWF_E2E_MUTATION=true。"

  [[ "$(url_scheme)" == "https" ]] \
    || fail "非本地 approval workflow 用户种子必须使用 HTTPS: ${BASE_URL}"

  [[ -n "${OB_TEST_ADMIN_PASSWORD:-}" && "${ADMIN_PASSWORD}" != "666666" ]] \
    || fail "非本地 approval workflow 用户种子必须显式设置非默认 OB_TEST_ADMIN_PASSWORD"

  [[ -n "${OB_TEST_AWF_PASSWORD:-}" && "${AWF_E2E_PASSWORD}" != "AwfRegression@2026!" ]] \
    || fail "非本地 approval workflow 用户种子必须显式设置非默认 OB_TEST_AWF_PASSWORD"
}

url_encode() {
  jq -rn --arg value "$1" '$value | @uri'
}

guard_target

if [[ -n "${OB_TEST_ADMIN_ACCESS_TOKEN:-}" ]]; then
  token="${OB_TEST_ADMIN_ACCESS_TOKEN}"
else
  login_response=$(
    curl -fsS -X POST "${BASE_URL}/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn --arg account "${ADMIN_ACCOUNT}" --arg password "${ADMIN_PASSWORD}" \
        '{account: $account, password: $password}')"
  )
  token=$(printf '%s' "${login_response}" | jq -er '.data.accessToken.token // .result.accessToken.token // .accessToken.token')
fi
auth_header="Authorization: Bearer ${token}"

api_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [[ -n "${body}" ]]; then
    curl -sS -X "${method}" "${BASE_URL}${path}" \
      -H "${auth_header}" \
      -H 'Content-Type: application/json' \
      -d "${body}"
  else
    curl -sS -X "${method}" "${BASE_URL}${path}" \
      -H "${auth_header}" \
      -H 'Content-Type: application/json'
  fi
}

api_empty_ok() {
  local method="$1"
  local path="$2"
  curl -fsS -X "${method}" "${BASE_URL}${path}" \
    -H "${auth_header}" \
    -H 'Content-Type: application/json' >/dev/null
}

assert_success() {
  local response="$1"
  local context="$2"
  if [[ -z "${response}" ]]; then
    return 0
  fi
  is_success_response "${response}" \
    || fail "${context} 失败: ${response}"
}

is_success_response() {
  local response="$1"
  printf '%s' "${response}" | jq -e '((.code? // 0) == 0) and ((.responseStatus? // 0) == 0)' >/dev/null
}

check_existing_identity() {
  local account="$1"
  local login_check
  login_check=$(
    curl -fsS -X POST "${BASE_URL}/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn --arg account "${account}" --arg password "${AWF_E2E_PASSWORD}" '{account: $account, password: $password}')"
  )
  printf '%s' "${login_check}" | jq -e '.data.accessToken.token // .result.accessToken.token // .accessToken.token' >/dev/null \
    || fail "用户登录预检失败: ${account} ${login_check}"
}

items_filter='.data.items // .data // .result.items // .result // .items // []'

ensure_role() {
  local role_id="$1"
  local role_name="$2"
  local existing
  existing=$(api_json GET "/api/sys/role?page=1&perPage=500")
  if printf '%s' "${existing}" | jq -e --arg id "${role_id}" "${items_filter} | .[]? | select(.id == \$id)" >/dev/null; then
    return 0
  fi

  log "创建角色: ${role_id}"
  local response
  response=$(api_json POST "/api/sys/role" "$(jq -cn --arg id "${role_id}" --arg name "${role_name}" '{id: $id, name: $name}')")
  assert_success "${response}" "创建角色 ${role_id}"
}

ensure_role_authorities() {
  local role_id="$1"
  local response
  response=$(api_json PUT "/api/sys/role/${role_id}/authority" "$(jq -cn \
    --arg roleId "${role_id}" \
    '["approvalCenter", "approvalCenter.start"] | map({role: $roleId, authority: ., accessControlParams: {}})')")
  assert_success "${response}" "绑定角色权限 ${role_id}"
}

ensure_company() {
  local name="$1"
  local response id
  response=$(api_json GET "/api/organization/companies")
  id=$(printf '%s' "${response}" | jq -er --arg name "${name}" "${items_filter} | .[]? | select(.name == \$name) | .id" 2>/dev/null || true)
  if [[ -n "${id}" ]]; then
    printf '%s' "${id}"
    return 0
  fi
  response=$(api_json POST "/api/organization/companies" "$(jq -cn --arg name "${name}" '{name: $name, description: "approval workflow deterministic e2e company"}')")
  assert_success "${response}" "创建公司 ${name}"
  printf '%s' "${response}" | jq -er '.data.id // .result.id'
}

ensure_department() {
  local company_id="$1"
  local name="$2"
  local response id
  response=$(api_json GET "/api/organization/departments?companyId=${company_id}")
  id=$(printf '%s' "${response}" | jq -er --arg name "${name}" "${items_filter} | .[]? | select(.name == \$name) | .id" 2>/dev/null || true)
  if [[ -n "${id}" ]]; then
    printf '%s' "${id}"
    return 0
  fi
  response=$(api_json POST "/api/organization/departments" "$(jq -cn --arg name "${name}" --arg parentId "${company_id}" '{name: $name, parentId: $parentId, description: "approval workflow deterministic e2e department", withDefaultPosition: false}')")
  assert_success "${response}" "创建部门 ${name}"
  printf '%s' "${response}" | jq -er '.data.id // .result.id'
}

ensure_position() {
  local department_id="$1"
  local name="$2"
  local response id
  response=$(api_json GET "/api/organization/positions?departmentId=${department_id}")
  id=$(printf '%s' "${response}" | jq -er --arg name "${name}" "${items_filter} | .[]? | select(.name == \$name) | .id" 2>/dev/null || true)
  if [[ -n "${id}" ]]; then
    printf '%s' "${id}"
    return 0
  fi
  response=$(api_json POST "/api/organization/positions" "$(jq -cn --arg name "${name}" --arg departmentId "${department_id}" '{name: $name, departmentId: $departmentId, description: "approval workflow deterministic e2e position"}')")
  assert_success "${response}" "创建岗位 ${name}"
  printf '%s' "${response}" | jq -er '.data.id // .result.id'
}

delete_existing_employee_binding() {
  local account="$1"
  local employees employee_ids
  employees=$(api_json GET "/api/organization/employees?page=1&perPage=500")
  employee_ids=$(printf '%s' "${employees}" | jq -r --arg account "${account}" "${items_filter} | .[]? | select(.userId == \$account) | .id")
  while IFS= read -r employee_id; do
    [[ -z "${employee_id}" ]] && continue
    log "删除旧员工绑定: ${employee_id} (${account})"
    api_empty_ok DELETE "/api/organization/employees/${employee_id}"
  done <<< "${employee_ids}"
}

create_employee_user() {
  local account="$1"
  local display_name="$2"
  local email="$3"
  local department_id="$4"
  local position_id="$5"
  local role_id="$6"
  local response employee_id

  delete_existing_employee_binding "${account}"

  local encoded_account existing_user user_payload
  encoded_account=$(url_encode "${account}")
  existing_user=$(api_json GET "/api/sys/user/${encoded_account}")
  if is_success_response "${existing_user}"; then
    user_payload=$(jq -cn --arg account "${account}" '{mode: "link", userId: $account}')
  else
    user_payload=$(jq -cn --arg account "${account}" --arg password "${AWF_E2E_PASSWORD}" '{mode: "create", username: $account, password: $password}')
  fi

  response=$(api_json POST "/api/organization/employees" "$(jq -cn \
    --arg name "${display_name}" \
    --arg email "${email}" \
    --arg departmentId "${department_id}" \
    --arg positionId "${position_id}" \
    --argjson user "${user_payload}" \
    '{name: $name, gender: "男", email: $email, departmentId: $departmentId, positionId: $positionId, startTime: "2026-01-01", user: $user}')")
  assert_success "${response}" "创建员工用户 ${account}"
  employee_id=$(printf '%s' "${response}" | jq -er '.data // .result')

  response=$(api_json PUT "/api/sys/user/$(url_encode "${account}")/role" "$(jq -cn --arg roleId "${role_id}" '[$roleId]')")
  assert_success "${response}" "绑定用户角色 ${account} -> ${role_id}"

  local login_check
  login_check=$(
    curl -fsS -X POST "${BASE_URL}/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn --arg account "${account}" --arg password "${AWF_E2E_PASSWORD}" '{account: $account, password: $password}')"
  )
  printf '%s' "${login_check}" | jq -e '.data.accessToken.token // .result.accessToken.token // .accessToken.token' >/dev/null \
    || fail "用户登录预检失败: ${account} ${login_check}"

  printf '%s' "${employee_id}"
}

if [[ "${MODE}" == "check" ]]; then
  [[ -f "${IDENTITIES_OUTPUT}" ]] || fail "缺少确定性审批流用户映射文件: ${IDENTITIES_OUTPUT}"
  while IFS=$'\t' read -r account; do
    log "检查确定性审批流用户: ${account}"
    check_existing_identity "${account}"
  done < <(jq -r '.users[].account' "${USER_SEED_FILE}")
  log "确定性审批流用户检查完成: ${IDENTITIES_OUTPUT}"
  exit 0
fi

while IFS=$'\t' read -r role_id role_name; do
  ensure_role "${role_id}" "${role_name}"
  ensure_role_authorities "${role_id}"
done < <(jq -r '.roles[] | [.id, .name] | @tsv' "${ROLE_SEED_FILE}")

company_id=$(ensure_company "AWF E2E Company")
department_id=$(ensure_department "${company_id}" "AWF E2E Approval Department")
position_id=$(ensure_position "${department_id}" "AWF E2E Approver")

submitter_employee_id=""
approver1_employee_id=""
approver2_employee_id=""
approver3_employee_id=""
auditor_employee_id=""
flow_admin_employee_id=""

while IFS=$'\t' read -r seed_id account display_name email role_id; do
  log "准备确定性审批流用户: ${account}"
  if ! created_employee_id=$(create_employee_user "${account}" "${display_name}" "${email}" "${department_id}" "${position_id}" "${role_id}"); then
    fail "准备确定性审批流用户失败: ${account}; ${created_employee_id}"
  fi
  case "${seed_id}" in
    AWF_USER_SUBMITTER)
      submitter_employee_id="${created_employee_id}"
      ;;
    AWF_USER_APPROVER_1)
      approver1_employee_id="${created_employee_id}"
      ;;
    AWF_USER_APPROVER_2)
      approver2_employee_id="${created_employee_id}"
      ;;
    AWF_USER_APPROVER_3)
      approver3_employee_id="${created_employee_id}"
      ;;
    AWF_USER_AUDITOR)
      auditor_employee_id="${created_employee_id}"
      ;;
    AWF_USER_FLOW_ADMIN)
      flow_admin_employee_id="${created_employee_id}"
      ;;
    *)
      fail "未知审批流用户种子 ID: ${seed_id}"
      ;;
  esac
done < <(jq -r '
  .userRoleMappings as $userRoleMappings |
  .users[] as $user |
  ($user.id) as $userId |
  ($userRoleMappings[] | select(.userId == $userId) | .roleId) as $roleId |
  [$user.id, $user.account, $user.displayName, $user.email, $roleId] | @tsv
' "${USER_SEED_FILE}")

[[ -n "${submitter_employee_id}" ]] || fail "缺少提交人 employeeId"
[[ -n "${approver1_employee_id}" ]] || fail "缺少一级审批人 employeeId"
[[ -n "${approver2_employee_id}" ]] || fail "缺少二级审批人 employeeId"
[[ -n "${approver3_employee_id}" ]] || fail "缺少并行审批人 employeeId"
[[ -n "${auditor_employee_id}" ]] || fail "缺少审计人 employeeId"
[[ -n "${flow_admin_employee_id}" ]] || fail "缺少流程管理员 employeeId"

mkdir -p "$(dirname "${IDENTITIES_OUTPUT}")"
jq -n \
  --arg password "${AWF_E2E_PASSWORD}" \
  --arg submitterAccount "awf.submitter" \
  --arg submitterEmployee "${submitter_employee_id}" \
  --arg approver1Account "awf.approver1" \
  --arg approver1Employee "${approver1_employee_id}" \
  --arg approver2Account "awf.approver2" \
  --arg approver2Employee "${approver2_employee_id}" \
  --arg approver3Account "awf.approver3" \
  --arg approver3Employee "${approver3_employee_id}" \
  --arg auditorAccount "awf.auditor" \
  --arg auditorEmployee "${auditor_employee_id}" \
  --arg flowAdminAccount "awf.flowadmin" \
  --arg flowAdminEmployee "${flow_admin_employee_id}" \
  '{
    schemaVersion: 1,
    generatedBy: "approval-workflow-users-seed/prepare.sh",
    password: $password,
    users: {
      submitter: {account: $submitterAccount, password: $password, employeeId: $submitterEmployee, userIdentity: $submitterAccount},
      approver1: {account: $approver1Account, password: $password, employeeId: $approver1Employee, userIdentity: $approver1Account},
      approver2: {account: $approver2Account, password: $password, employeeId: $approver2Employee, userIdentity: $approver2Account},
      approver3: {account: $approver3Account, password: $password, employeeId: $approver3Employee, userIdentity: $approver3Account},
      auditor: {account: $auditorAccount, password: $password, employeeId: $auditorEmployee, userIdentity: $auditorAccount},
      flowAdmin: {account: $flowAdminAccount, password: $password, employeeId: $flowAdminEmployee, userIdentity: $flowAdminAccount}
    },
    aliases: {
      AWF_USER_SUBMITTER: $submitterAccount,
      AWF_USER_APPROVER_1: $approver1Account,
      AWF_USER_APPROVER_2: $approver2Account,
      AWF_USER_APPROVER_3: $approver3Account,
      AWF_USER_AUDITOR: $auditorAccount,
      AWF_USER_FLOW_ADMIN: $flowAdminAccount
    }
  }' > "${IDENTITIES_OUTPUT}"

log "确定性审批流用户准备完成: ${IDENTITIES_OUTPUT}"
