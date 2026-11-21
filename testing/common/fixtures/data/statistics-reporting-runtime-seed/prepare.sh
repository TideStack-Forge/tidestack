#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[statistics-reporting-runtime-seed] %s\n' "$*" >&2
}

fail() {
  log "$*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令: $1"
}

need jq
need curl

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../" && pwd)
FIXTURE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SPEC_FILE="${FIXTURE_DIR}/${OB_TEST_SRT_SEED_SPEC_FILE:-seed-spec.json}"
SPEC_REPORT_PATH="${SPEC_FILE#"${ROOT_DIR}/"}"
EVIDENCE_ROOT="${OB_TEST_SRT_EVIDENCE_ROOT:-${ROOT_DIR}/tmp/testing-evidence/statistics-reporting-runtime-query-and-bi}"
MODE="check"

case "${1:-}" in
  ""|--check|check)
    MODE="check"
    ;;
  --reset|reset)
    MODE="reset"
    ;;
  --up|up)
    MODE="up"
    ;;
  *)
    fail "未知参数: $1（支持: --check, --reset, --up）"
    ;;
esac

[[ -f "${SPEC_FILE}" ]] || fail "缺少 seed-spec.json: ${SPEC_FILE}"

APP_ROOT="${OB_TEST_APP_ROOT:-}"
[[ -n "${APP_ROOT}" ]] || fail "缺少 OB_TEST_APP_ROOT（应用元数据根目录）"
[[ -d "${APP_ROOT}" ]] || fail "appRoot 不存在: ${APP_ROOT}"

APP_NAME="${OB_TEST_APP_NAME:-$(basename "${APP_ROOT}")}"

ensure_app_exists() {
  local app_list_response
  app_list_response=$(api_json GET "/api/app") || fail "获取应用列表失败"
  
  if printf '%s' "${app_list_response}" | jq -e --arg name "${APP_NAME}" '.data.items[].appInfo | select(.name == $name)' >/dev/null 2>&1; then
    return 0
  fi
  
  local create_payload
  create_payload=$(jq -n --arg name "${APP_NAME}" --arg title "${APP_NAME}" '{name: $name, title: $title, description: "Test app for statistics-reporting runtime seed"}')
  local create_response
  create_response=$(api_json POST "/api/app" "${create_payload}") || fail "创建应用失败"
  
  if printf '%s' "${create_response}" | json_ok; then
    return 0
  fi
  
  if printf '%s' "${create_response}" | jq -e '.msg' | grep -q "应用名已存在"; then
    return 0
  fi
  
  fail "创建应用失败: ${create_response}"
}
BASE_URL="${OB_TEST_BASE_URL:-${OB_TEST_RUNTIME_FRONTEND_BASE_URL:-http://127.0.0.1:80}}"
ADMIN_ACCOUNT="${OB_TEST_ADMIN_ACCOUNT:-admin}"
ADMIN_PASSWORD="${OB_TEST_ADMIN_PASSWORD:-666666}"
BOUNDARY_ROLE_ID="statistics-reporting.boundary"
SCOPED_READER_ROLE_ID="statistics-reporting.read"
SCOPED_READER_ACCOUNT="srt-scoped-reader"
SCOPED_READER_PASSWORD="${OB_TEST_SRT_SCOPED_READER_PASSWORD:-SrtScopedReader@2026!}"
IS_RESTRICTED_SPEC=$(jq -r '((.dataset.permissionDefinition.requiredRoles // []) | length) > 0' "${SPEC_FILE}")

DATASET_MODULE="dataset"
CHART_MODULE="chart"
BUSINESS_MODEL_MODULE="business-model"

DATASET_NAME=$(jq -er '.dataset.name' "${SPEC_FILE}")
CHART_KPI_NAME=$(jq -er '.charts[] | select(.name == "sales-kpi-runtime-chart") | .name' "${SPEC_FILE}")
CHART_TABLE_NAME=$(jq -er '.charts[] | select(.name == "sales-crosstab-runtime-chart") | .name' "${SPEC_FILE}")
CHART_LINE_NAME=$(jq -er '.charts[] | select(.name == "sales-trend-runtime-chart") | .name' "${SPEC_FILE}")
DASHBOARD_NAME=$(jq -er '.dashboard.name' "${SPEC_FILE}")
DASHBOARD_FULL_NAME=$(jq -er '
  .dashboard
  | if ((.namespace // "") | length) == 0
    then .name
    else (.namespace + "." + .name)
    end
' "${SPEC_FILE}")
DASHBOARD_RUNTIME_PATH=$(
  printf '%s' "${DASHBOARD_FULL_NAME}" \
    | sed -E 's/([[:lower:][:digit:]])([[:upper:]])/\1-\2/g; s#\.#/#g' \
    | tr '[:upper:]' '[:lower:]'
)
DASHBOARD_RUNTIME_PATH="/${DASHBOARD_RUNTIME_PATH}"
APP_DEFAULT_DATASET_FILE="${APP_ROOT}/statistics-reporting/datasets/${DATASET_NAME}.dataset.json"
APP_DEFAULT_CHART_KPI_FILE="${APP_ROOT}/statistics-reporting/charts/${CHART_KPI_NAME}.chart.json"
APP_DEFAULT_CHART_TABLE_FILE="${APP_ROOT}/statistics-reporting/charts/${CHART_TABLE_NAME}.chart.json"
APP_DEFAULT_CHART_LINE_FILE="${APP_ROOT}/statistics-reporting/charts/${CHART_LINE_NAME}.chart.json"
APP_DEFAULT_DASHBOARD_FILE="${APP_ROOT}/business-model/${DASHBOARD_NAME}.business.json"

REPORT_FILE="${EVIDENCE_ROOT}/statistics-reporting-runtime-seed-${MODE}.json"

mkdir -p "${EVIDENCE_ROOT}"

url_encode() {
  jq -rn --arg value "$1" '$value | @uri'
}

json_ok() {
  jq -e '((.code? // 0) == 0)' >/dev/null
}

model_exists() {
  jq -e '((.code? // 0) == 0 and (.data? != null))' >/dev/null
}


api_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [[ -n "${body}" ]]; then
    printf '%s' "${body}" \
      | jq -c '.' \
      | curl -sS -X "${method}" "${BASE_URL}${path}" \
          -H "${AUTH_HEADER}" \
          -H 'Content-Type: application/json' \
          --data-binary @-
  else
    curl -sS -X "${method}" "${BASE_URL}${path}" \
      -H "${AUTH_HEADER}" \
      -H 'Content-Type: application/json'
  fi
}

login_token() {
  local account="$1"
  local password="$2"
  local response
  response=$(
    curl -fsS -X POST "${BASE_URL}/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "$(jq -cn --arg account "${account}" --arg password "${password}" \
        '{account: $account, password: $password}')"
  ) || fail "用户登录失败: ${account}"
  printf '%s' "${response}" | jq -er '.data.accessToken.token // .result.accessToken.token // .accessToken.token' \
    || fail "登录响应缺少 access token: ${account} ${response}"
}

ensure_role() {
  local role_id="$1"
  local role_name="$2"
  local existing response
  existing=$(api_json GET "/api/sys/role/$(url_encode "${role_id}")") || fail "获取角色失败: ${role_id}"
  if printf '%s' "${existing}" | model_exists; then
    return 0
  fi
  response=$(api_json POST "/api/sys/role" "$(jq -cn --arg id "${role_id}" --arg name "${role_name}" '{id: $id, name: $name}')")
  printf '%s' "${response}" | json_ok || fail "创建角色失败: ${role_id} ${response}"
}

set_role_authority() {
  local role_id="$1"
  local response
  response=$(api_json PUT "/api/sys/role/$(url_encode "${role_id}")/authority" "$(jq -cn \
    --arg role "${role_id}" \
    --arg dashboardAuthority "${DASHBOARD_FULL_NAME}" \
    --arg dataset "${DATASET_NAME}" \
    --argjson charts "$(jq '[.charts[].name]' "${SPEC_FILE}")" \
    '([{role: $role, authority: $dashboardAuthority, accessControlParams: {}}] +
      (["execute", "export"] | map(. as $operation |
        {role: $role, authority: ("statisticsReporting.runtime.dataset." + ($dataset | ascii_downcase) + "." + $operation), accessControlParams: {}})) +
      ([$charts[] as $chart | ["execute", "export"][] as $operation |
        {role: $role, authority: ("statisticsReporting.runtime.chart." + ($chart | ascii_downcase) + "." + $operation), accessControlParams: {}}]))')")
  printf '%s' "${response}" | json_ok || fail "绑定角色权限失败: ${role_id} ${response}"
}

ensure_user() {
  local account="$1"
  local password="$2"
  local existing response
  existing=$(api_json GET "/api/sys/user/$(url_encode "${account}")") || fail "获取用户失败: ${account}"
  if printf '%s' "${existing}" | model_exists; then
    return 0
  fi
  response=$(api_json POST "/api/sys/user" "$(jq -cn \
    --arg userIdentity "${account}" \
    --arg password "${password}" \
    '{userIdentity: $userIdentity, password: $password, roles: []}')")
  printf '%s' "${response}" | json_ok || fail "创建用户失败: ${account} ${response}"
}

set_user_role() {
  local account="$1"
  local role_id="$2"
  local response
  response=$(api_json PUT "/api/sys/user/$(url_encode "${account}")/role" "$(jq -cn --arg role "${role_id}" '[$role]')")
  printf '%s' "${response}" | json_ok || fail "绑定用户角色失败: ${account} -> ${role_id} ${response}"
}

ensure_restricted_actors() {
  local boundary_account="${OB_TEST_RESTRICTED_ACCOUNT:-}"
  local boundary_password="${OB_TEST_RESTRICTED_PASSWORD:-}"
  [[ -n "${boundary_account}" && -n "${boundary_password}" ]] \
    || fail "restricted seed 需要 OB_TEST_RESTRICTED_ACCOUNT 和 OB_TEST_RESTRICTED_PASSWORD"

  login_token "${boundary_account}" "${boundary_password}" >/dev/null

  ensure_role "${BOUNDARY_ROLE_ID}" "Statistics Reporting Boundary"
  set_role_authority "${BOUNDARY_ROLE_ID}"
  set_user_role "${boundary_account}" "${BOUNDARY_ROLE_ID}"

  ensure_role "${SCOPED_READER_ROLE_ID}" "Statistics Reporting Reader"
  set_role_authority "${SCOPED_READER_ROLE_ID}"
  ensure_user "${SCOPED_READER_ACCOUNT}" "${SCOPED_READER_PASSWORD}"
  set_user_role "${SCOPED_READER_ACCOUNT}" "${SCOPED_READER_ROLE_ID}"
  login_token "${SCOPED_READER_ACCOUNT}" "${SCOPED_READER_PASSWORD}" >/dev/null
}

get_model() {
  local module="$1"
  local full_name="$2"
  api_json GET "/api/dev/${APP_NAME}/${module}/$(url_encode "${full_name}")"
}

save_model() {
  local module="$1"
  local full_name="$2"
  local body="$3"
  local commit_msg="$4"
  local method="$5"

  if [[ "${method}" == "POST" ]]; then
    api_json POST "/api/dev/${APP_NAME}/${module}?autoUnlock=true&commitMsg=$(url_encode "${commit_msg}")" "${body}"
  else
    api_json PUT "/api/dev/${APP_NAME}/${module}/$(url_encode "${full_name}")?commit=true&autoUnlock=true&commitMsg=$(url_encode "${commit_msg}")" "${body}"
  fi
}

delete_model() {
  local module="$1"
  local full_name="$2"
  api_json DELETE "/api/dev/${APP_NAME}/${module}/$(url_encode "${full_name}")?autoUnlock=true&commitMsg=$(url_encode "删除 ${full_name}")"
}

assert_model_exists() {
  local module="$1"
  local full_name="$2"
  local response
  response=$(get_model "${module}" "${full_name}") || fail "获取 ${module}/${full_name} 失败"
  printf '%s' "${response}" | json_ok || fail "${module}/${full_name} 不存在或获取失败: ${response}"
  printf '%s' "${response}" | jq -e --arg name "${full_name}" '.data.name == $name' >/dev/null \
    || fail "${module}/${full_name} 名称不匹配: ${response}"
}

assert_model_missing() {
  local module="$1"
  local full_name="$2"
  local response
  response=$(get_model "${module}" "${full_name}") || fail "获取 ${module}/${full_name} 失败"
  printf '%s' "${response}" | jq -e '((.code? // 0) != 0 or (.data? == null))' >/dev/null \
    || fail "${module}/${full_name} 仍然存在: ${response}"
}

build_seed_sql() {
  jq -r '
    .seedRows
    | map(
        "select "
        + (.orderId | @json) + " as orderId, "
        + (.customerName | @json) + " as customerName, "
        + (.region | @json) + " as region, "
        + (.productCategory | @json) + " as productCategory, "
        + (.orderDate | @json) + " as orderDate, "
        + (.status | @json) + " as status, "
        + (.salesAmount | tostring) + " as salesAmount, "
        + (.grossProfit | tostring) + " as grossProfit, "
        + (.departmentId | @json) + " as departmentId, "
        + (.ownerUserId | @json) + " as ownerUserId"
      )
    | join("\nunion all\n") as $baseSql
    | "select * from (\n" + $baseSql + "\n) seeded_orders\n"
      + "where (${region} is null or region = ${region})\n"
      + "  and (${departmentId} is null or departmentId = ${departmentId})\n"
      + "  and (${ownerUserId} is null or ownerUserId = ${ownerUserId})\n"
      + "  and (${status} is null or status = ${status})"
  ' "${SPEC_FILE}"
}

build_dataset_payload() {
  local seed_sql
  seed_sql=$(build_seed_sql)

  jq -n \
    --arg seedSql "${seed_sql}" \
    --slurpfile spec "${SPEC_FILE}" \
    '
      $spec[0].dataset as $dataset |
      {
        name: $dataset.name,
        title: $dataset.title,
        description: $dataset.description,
        tags: $dataset.tags,
        isEnabled: true,
        queryDefinition: {
          type: "sql",
          datasource: "default",
          sql: $seedSql,
          dialects: []
        },
        parameters: $dataset.parameters,
        schema: {
          fields: [
            $dataset.fields[] |
            {
              name: .name,
              title: .title,
              dataType: .type,
              semanticRole: .semanticRole,
              nullable: true,
              sortable: true,
              filterable: true,
              exportable: .exportable,
              visibleByDefault: true
            }
          ]
        },
        permissionDefinition: $dataset.permissionDefinition,
        executionConstraints: $dataset.executionConstraints
      }
    '
}

build_chart_payload() {
  local chart_name="$1"

  jq -n --arg chartName "${chart_name}" --slurpfile spec "${SPEC_FILE}" '
    ($spec[0].charts[] | select(.name == $chartName)) as $chart |
    {
      type: "chart",
      name: $chart.name,
      datasetName: $chart.datasetName,
      title: $chart.title,
      isEnabled: ($chart.isEnabled // true),
      chartType: $chart.chartType,
      params: $chart.params
    }
  '
}

build_dashboard_payload() {
  jq -n --slurpfile spec "${SPEC_FILE}" '
    $spec[0].dashboard
  '
}

write_app_default_dataset() {
  local payload
  payload=$(build_dataset_payload)
  mkdir -p "$(dirname "${APP_DEFAULT_DATASET_FILE}")"
  printf '%s' "${payload}" | jq '.' > "${APP_DEFAULT_DATASET_FILE}"
}

write_app_default_chart() {
  local chart_name="$1"
  local output_file="$2"
  local payload
  payload=$(build_chart_payload "${chart_name}")
  mkdir -p "$(dirname "${output_file}")"
  printf '%s' "${payload}" | jq '.' > "${output_file}"
}

write_app_default_dashboard() {
  local payload
  payload=$(build_dashboard_payload)
  mkdir -p "$(dirname "${APP_DEFAULT_DASHBOARD_FILE}")"
  printf '%s' "${payload}" | jq '.' > "${APP_DEFAULT_DASHBOARD_FILE}"
}

remove_app_default_files() {
  rm -f "${APP_DEFAULT_DATASET_FILE}"
  rm -f "${APP_DEFAULT_CHART_KPI_FILE}"
  rm -f "${APP_DEFAULT_CHART_TABLE_FILE}"
  rm -f "${APP_DEFAULT_CHART_LINE_FILE}"
  rm -f "${APP_DEFAULT_DASHBOARD_FILE}"
}

ensure_dataset() {
  local payload
  payload=$(build_dataset_payload)
  local response
  local existing
  existing=$(get_model "${DATASET_MODULE}" "${DATASET_NAME}") || fail "获取 ${DATASET_MODULE}/${DATASET_NAME} 失败"
  if printf '%s' "${existing}" | model_exists; then
    response=$(save_model "${DATASET_MODULE}" "${DATASET_NAME}" "${payload}" "创建 statistics reporting runtime dataset" PUT)
    printf '%s' "${response}" | json_ok || fail "更新 Dataset 失败: ${response}"
  else
    response=$(save_model "${DATASET_MODULE}" "${DATASET_NAME}" "${payload}" "创建 statistics reporting runtime dataset" POST)
    printf '%s' "${response}" | json_ok || fail "创建 Dataset 失败: ${response}"
  fi
  assert_model_exists "${DATASET_MODULE}" "${DATASET_NAME}"
}

ensure_chart() {
  local chart_name="$1"
  local payload response
  payload=$(build_chart_payload "${chart_name}")
  local existing
  existing=$(get_model "${CHART_MODULE}" "${chart_name}") || fail "获取 ${CHART_MODULE}/${chart_name} 失败"
  if printf '%s' "${existing}" | model_exists; then
    response=$(save_model "${CHART_MODULE}" "${chart_name}" "${payload}" "更新 statistics reporting runtime chart ${chart_name}" PUT)
    printf '%s' "${response}" | json_ok || fail "更新 Chart 失败: ${response}"
  else
    response=$(save_model "${CHART_MODULE}" "${chart_name}" "${payload}" "创建 statistics reporting runtime chart ${chart_name}" POST)
    printf '%s' "${response}" | json_ok || fail "创建 Chart 失败: ${response}"
  fi
  assert_model_exists "${CHART_MODULE}" "${chart_name}"
}

ensure_dashboard() {
  local payload response
  payload=$(build_dashboard_payload)
  local existing
  existing=$(get_model "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}") || fail "获取 ${BUSINESS_MODEL_MODULE}/${DASHBOARD_NAME} 失败"
  if printf '%s' "${existing}" | model_exists; then
    response=$(save_model "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}" "${payload}" "更新 dashboard business model" PUT)
    printf '%s' "${response}" | json_ok || fail "更新 Dashboard 失败: ${response}"
  else
    response=$(save_model "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}" "${payload}" "创建 dashboard business model" POST)
    printf '%s' "${response}" | json_ok || fail "创建 Dashboard 失败: ${response}"
  fi
  assert_model_exists "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}"
}

build_runtime_probe_request() {
  local region="$1"
  local department_id="$2"

  jq -n \
    --arg region "${region}" \
    --arg departmentId "${department_id}" \
    '{
      runtimeParameters: {
        region: $region,
        departmentId: $departmentId
      },
      executionContext: {
        departmentId: $departmentId
      }
    }'
}

assert_chart_runtime_execution() {
  local chart_name="$1"
  local response

  response=$(api_json POST "/api/runtime/statistics-reporting/charts/${chart_name}/execute" '{}')
  printf '%s' "${response}" | json_ok || fail "Chart ${chart_name} execute 失败: ${response}"

  case "${chart_name}" in
    "sales-kpi-runtime-chart")
      printf '%s' "${response}" | jq -e '
        .data.chartType == "metric-card" and
        (.data.option.title | map(.subtext)) == ["Sales Amount", "Gross Profit"] and
        .data.option.title[0].text == "120000" and
        .data.option.title[1].text == "48000"
      ' >/dev/null || fail "Chart ${chart_name} 指标卡结果不符合 seed 约束: ${response}"
      ;;
    "sales-crosstab-runtime-chart")
      printf '%s' "${response}" | jq -e '
        .data.chartType == "crosstab" and
        (.data.option.graphic[0].style.text | contains("Sales Amount")) and
        (.data.option.graphic[0].style.text | contains("east"))
      ' >/dev/null || fail "Chart ${chart_name} 透视表结果不符合 seed 约束: ${response}"
      ;;
    "sales-trend-runtime-chart")
      printf '%s' "${response}" | jq -e '
        .data.chartType == "line" and
        (.data.option.xAxis.data | length) == 12 and
        (.data.option.series | length) == 4 and
        (.data.option.series[] | select(.name == "east") | .data[0]) == 120000
      ' >/dev/null || fail "Chart ${chart_name} 图表结果不符合 seed 约束: ${response}"
      ;;
    *)
      fail "未知 Chart: ${chart_name}"
      ;;
  esac
}

assert_dashboard_runtime_execution() {
  local response

  response=$(api_json GET "/api${DASHBOARD_RUNTIME_PATH}")
  printf '%s' "${response}" | json_ok || fail "Dashboard structure 读取失败: ${response}"
  printf '%s' "${response}" | jq -e --arg fullName "${DASHBOARD_FULL_NAME}" '
    .data.fullName == $fullName and
    (.data.widgets | length) == 3 and
    (.data.widgets | map(.id) | sort) == ["sales-crosstab-runtime-chart-widget", "sales-kpi-runtime-chart-widget", "sales-trend-runtime-chart-widget"] and
    (.data.widgets | map(has("data") | not) | all) and
    (.data.widgets | map(.chartName) | sort) == ["sales-crosstab-runtime-chart", "sales-kpi-runtime-chart", "sales-trend-runtime-chart"]
  ' >/dev/null || fail "Dashboard structure 不符合 seed 约束: ${response}"
}

assert_restricted_runtime_execution() {
  local boundary_account="${OB_TEST_RESTRICTED_ACCOUNT:-}"
  local boundary_password="${OB_TEST_RESTRICTED_PASSWORD:-}"
  [[ -n "${boundary_account}" && -n "${boundary_password}" ]] \
    || fail "restricted check 需要 OB_TEST_RESTRICTED_ACCOUNT 和 OB_TEST_RESTRICTED_PASSWORD"

  local boundary_token boundary_response boundary_chart_response
  boundary_token=$(login_token "${boundary_account}" "${boundary_password}")
  AUTH_HEADER="Authorization: Bearer ${boundary_token}"
  boundary_response=$(api_json GET "/api${DASHBOARD_RUNTIME_PATH}")
  printf '%s' "${boundary_response}" | jq -e '
    ((.code? // 0) == 0) and
    (.data.widgets | length) == 3 and
    (.data.widgets | map(has("data") | not) | all)
  ' >/dev/null || fail "Boundary actor 无法读取 Dashboard structure: ${boundary_response}"

  boundary_chart_response=$(api_json POST "/api/runtime/statistics-reporting/charts/${CHART_KPI_NAME}/execute" '{}')
  printf '%s' "${boundary_chart_response}" | jq -e '
    ((.code? // 0) != 0) and
    (.data == null) and
    ((.msg // .message // "") | contains("dataset access denied"))
  ' >/dev/null || fail "Boundary actor 的 Chart 数据请求未被 Dataset requiredRoles 拒绝: ${boundary_chart_response}"

  local reader_token export_request export_response execute_request execute_response
  reader_token=$(login_token "${SCOPED_READER_ACCOUNT}" "${SCOPED_READER_PASSWORD}")
  AUTH_HEADER="Authorization: Bearer ${reader_token}"

  export_request=$(jq -n '{runtimeParameters: {ownerUserId: "client-supplied-owner"}}')
  export_response=$(api_json POST "/api/runtime/statistics-reporting/charts/${CHART_TABLE_NAME}/export" "${export_request}")
  printf '%s' "${export_response}" | jq -e '
    ((.code? // 0) != 0) and
    (.data == null) and
    ((.msg // .message // "") | contains("export"))
  ' >/dev/null || fail "Scoped reader 未被 exportRoles 拒绝: ${export_response}"

  execute_request=$(jq -n --arg datasetName "${DATASET_NAME}" '{
    datasetName: $datasetName,
    executionContext: {currentUser: "client-supplied-owner"},
    runtimeParameters: {ownerUserId: "client-supplied-owner"}
  }')
  execute_response=$(api_json POST "/api/statistics-reporting/datasets/${DATASET_NAME}/execute" "${execute_request}")
  printf '%s' "${execute_response}" | json_ok || fail "Scoped reader Dataset execute 失败: ${execute_response}"
  printf '%s' "${execute_response}" | jq -e --arg owner "${SCOPED_READER_ACCOUNT}" '
    .data.appliedParameters.ownerUserId == $owner and
    (.data.diagnostics.attributes.permissionBindings | index("ownerUserId<-currentUser")) != null and
    (.data.rows | map(.orderId) | sort) == ["SO-2026-005", "SO-2026-006", "SO-2026-011"]
  ' >/dev/null || fail "Scoped reader identity scope 未覆盖客户端参数: ${execute_response}"
}

reset_dashboard() {
  delete_model "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}" >/dev/null
  assert_model_missing "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}"
  rm -f "${APP_DEFAULT_DASHBOARD_FILE}"
}

reset_charts() {
  delete_model "${CHART_MODULE}" "${CHART_KPI_NAME}" >/dev/null
  delete_model "${CHART_MODULE}" "${CHART_TABLE_NAME}" >/dev/null
  delete_model "${CHART_MODULE}" "${CHART_LINE_NAME}" >/dev/null
  assert_model_missing "${CHART_MODULE}" "${CHART_KPI_NAME}"
  assert_model_missing "${CHART_MODULE}" "${CHART_TABLE_NAME}"
  assert_model_missing "${CHART_MODULE}" "${CHART_LINE_NAME}"
  rm -f "${APP_DEFAULT_CHART_KPI_FILE}"
  rm -f "${APP_DEFAULT_CHART_TABLE_FILE}"
  rm -f "${APP_DEFAULT_CHART_LINE_FILE}"
}

reset_dataset() {
  delete_model "${DATASET_MODULE}" "${DATASET_NAME}" >/dev/null
  assert_model_missing "${DATASET_MODULE}" "${DATASET_NAME}"
  rm -f "${APP_DEFAULT_DATASET_FILE}"
}

token=$(login_token "${ADMIN_ACCOUNT}" "${ADMIN_PASSWORD}")
AUTH_HEADER="Authorization: Bearer ${token}"

ensure_app_exists

if [[ "${MODE}" == "reset" ]]; then
  reset_dashboard
  reset_charts
  reset_dataset

  jq -n \
    --arg mode "reset" \
    --arg appName "${APP_NAME}" \
    --arg appRoot "${APP_ROOT}" \
    --arg datasetName "${DATASET_NAME}" \
    --arg dashboardName "${DASHBOARD_NAME}" \
    '{status: "pass", lifecycle: $mode, appName: $appName, appRoot: $appRoot, datasetName: $datasetName, dashboardName: $dashboardName}' \
    > "${REPORT_FILE}"

  log "reset 完成: ${REPORT_FILE}"
  exit 0
fi

if [[ "${MODE}" == "up" ]]; then
  ensure_dataset
  ensure_chart "${CHART_KPI_NAME}"
  ensure_chart "${CHART_TABLE_NAME}"
  ensure_chart "${CHART_LINE_NAME}"
  ensure_dashboard
  if [[ "${IS_RESTRICTED_SPEC}" == "true" ]]; then
    ensure_restricted_actors
  fi
  write_app_default_dataset
  write_app_default_chart "${CHART_KPI_NAME}" "${APP_DEFAULT_CHART_KPI_FILE}"
  write_app_default_chart "${CHART_TABLE_NAME}" "${APP_DEFAULT_CHART_TABLE_FILE}"
  write_app_default_chart "${CHART_LINE_NAME}" "${APP_DEFAULT_CHART_LINE_FILE}"
  write_app_default_dashboard

  jq -n \
    --arg mode "up" \
    --arg appName "${APP_NAME}" \
    --arg appRoot "${APP_ROOT}" \
    --arg datasetName "${DATASET_NAME}" \
    --arg dashboardName "${DASHBOARD_NAME}" \
    '{status: "pass", lifecycle: $mode, appName: $appName, appRoot: $appRoot, datasetName: $datasetName, dashboardName: $dashboardName}' \
    > "${REPORT_FILE}"

  log "up 完成: ${REPORT_FILE}"
  exit 0
fi

  jq -e '
    .dataset.name == "srt.sales.orders" and
    (.dataset.permissionDefinition.requiredRoles | type == "array" and all(type == "string")) and
    (.dataset.permissionDefinition.exportRoles | type == "array" and all(type == "string")) and
    (.dataset.fields | map(select(.name == "departmentId" and .exportable == false)) | length) == 1 and
    (.dataset.fields | map(select(.name == "ownerUserId" and .exportable == false)) | length) == 1 and
    (.dataset.parameters | map(select(.name == "ownerUserId" and .required == false)) | length) == 1 and
    (.seedRows | length) >= 12 and
    (.seedRows | map(.region) | unique | length) >= 4 and
    (.seedRows | map(.status) | unique | length) >= 3 and
    (.seedRows | map(.departmentId) | unique | length) >= 2 and
    (.charts | length) >= 3 and
    .dashboard.name == "sales-overview-runtime-dashboard" and
    (.dashboard.widgets | length) >= 3 and
    (.dashboard.widgets | map(has("id")) | all) and
    (.dashboard.widgets | map(.widgetName) | unique) == ["chart"] and
    (if ((.dataset.permissionDefinition.requiredRoles // []) | length) > 0
      then .dataset.permissionDefinition.scopeBindings.ownerUserId == "currentUser" and
        (.seedRows | map(select(.ownerUserId == "srt-scoped-reader")) | length) == 3
      else true
    end)
 ' "${SPEC_FILE}" >/dev/null || fail "seed specification 未满足 statistics-reporting runtime 回归约束: ${SPEC_REPORT_PATH}"

assert_model_exists "${DATASET_MODULE}" "${DATASET_NAME}"
assert_model_exists "${CHART_MODULE}" "${CHART_KPI_NAME}"
assert_model_exists "${CHART_MODULE}" "${CHART_TABLE_NAME}"
assert_model_exists "${CHART_MODULE}" "${CHART_LINE_NAME}"
assert_model_exists "${BUSINESS_MODEL_MODULE}" "${DASHBOARD_NAME}"

if [[ "${IS_RESTRICTED_SPEC}" == "true" ]]; then
  assert_restricted_runtime_execution
else
  assert_chart_runtime_execution "sales-kpi-runtime-chart"
  assert_chart_runtime_execution "sales-crosstab-runtime-chart"
  assert_chart_runtime_execution "sales-trend-runtime-chart"
  assert_dashboard_runtime_execution
fi

jq -n \
  --arg mode "check" \
  --arg appName "${APP_NAME}" \
  --arg appRoot "${APP_ROOT}" \
  --arg datasetName "${DATASET_NAME}" \
  --arg dashboardName "${DASHBOARD_NAME}" \
  --arg specFile "${SPEC_REPORT_PATH}" \
  '{
    status: "pass",
    lifecycle: $mode,
    appName: $appName,
    appRoot: $appRoot,
    specFile: $specFile,
    datasetName: $datasetName,
    dashboardName: $dashboardName,
    notes: [
      "Fixture truth-source is structurally complete.",
      "Fixture check and browser E2E both authenticate with OB_TEST_ADMIN_ACCOUNT/OB_TEST_ADMIN_PASSWORD and use the same runtime identity.",
      "Dashboard is persisted as a standard business model under business-model/.",
      "Lifecycle up/reset are implemented through supported dev APIs and workspace paths."
    ]
  }' > "${REPORT_FILE}"

log "check 完成: ${REPORT_FILE}"
