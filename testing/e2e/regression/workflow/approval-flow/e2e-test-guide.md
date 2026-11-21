# 审批流回归 E2E 场景指南

## 定位

本文件只解释 `testing/e2e/regression/workflow/approval-flow/` 这个场景：

- 哪 5 个 Playwright 脚本分别负责什么
- 当前哪些能力已经改成 source-backed 自动化
- 哪些部分仍然受限于凭据或前端未落地的 dialog/schema

它不再把 Maven 集成测试描述成浏览器 E2E 的替代方案。

## 场景入口

- manifest: `testing/e2e/regression/workflow/approval-flow/e2e.yaml`
- runner: `testing/common/runners/playwright/browser_asset_bridge.py`
- truth source:
  - `testing/integration/regression/workflow/approval-flow/scenario.yaml`
  - `testing/integration/regression/workflow/approval-flow/contract.md`
  - `testing/integration/regression/workflow/approval-flow/cases.md`
  - `testing/integration/regression/workflow/approval-flow/harness.playbook.md`

## 五个脚本的职责

### 1. `form-and-flow.spec.ts`

覆盖：

- APF-FORM-001
- APF-FLOW-001
- APF-FLOW-002
- APF-BIND-001
- APF-BUSINESS-CORE-001

策略：

- business-model authoring 走真实 route / schema / label：
  - `#module-business-model`
  - `/dev/{appName}/business-model`
  - `/dev/{appName}/business-model/create`
  - `#create-business-model-form`
  - `#edit-business-model-form`
  - `保存`
  - `保存并提交`
- workflow definition authoring 走真实管理页：
  - `/dev/{appName}/approval-workflow-definition`
  - `/dev/{appName}/approval-workflow-definition/add`
  - `/dev/{appName}/approval-workflow-definition/{id}`
  - `/api/dev/{appName}/approval-workflow-definition*`
- business-model core lane 走生成 wrapper 与证据包：
  - `${modelPath}/add` submit action chain
  - `/api${modelPath}/${saveResult.id}/start-process`
  - `${modelPath}/${id}/approval?instanceId=...&taskId=...`
  - `/api${modelPath}/${id}/process/instance/${instanceId}/task/${taskId}/PASS`
  - `tmp/testing-evidence/workflow-approval-flow/business-model-journey.json`
- 设计时定义通过开发平台 API 保存和提交，测试容器通过 app-container reload 加载开发 workspace；复杂画布数据使用 fixture 驱动正式编辑器保存合同，不使用运行时 definition API 代替设计时。

### 2. `runtime-actions.spec.ts`

覆盖：

- APF-SUBMIT-001
- APF-TASK-001
- APF-APPROVE-001
- APF-REJECT-001
- APF-ROLLBACK-001
- APF-ADVANCED-DIALOGS-001
- APF-TRANSFER-001
- APF-ADDSIGN-001
- APF-ADDSIGN-002
- APF-FREEREJECT-001
- APF-FREEAPPROVE-001

策略：

- 浏览器 route/locator 只使用已确认的 approval-center 页面资产：
  - `/approval-center`
  - `/approval-center/start`
  - `/approval-center/start/init-form?id=...`
  - `#approval-center`
  - `#drawer-detail`
  - `#start-form`
  - `#reject-form`
  - `#rollback-form`
- 基础动作尽量通过真实 drawer/dialog 执行。
- advanced action 的聚合入口证据必须先证明四个 approval-center dialog schema 存在、selector GET 成功且 confirm ajax 指向 canonical task API；旧 direct POST 只能作为底层 task API 合同或测试 setup，不能替代弹窗/selector 证据。

### 3. `topology-and-reset.spec.ts`

覆盖：

- APF-CONDITION-XOR-001
- APF-RUNTIME-REFRESH-001
- APF-OR-JOIN-001
- APF-NODE-TYPES-001
- APF-SUBFLOW-001
- APF-RESET-DESIGN-001

策略：

- 稳定 V2 资源图不再使用 `GatewayNode`：
  - XOR 条件分支用 StartNode 多出边 + 条件表达式验证。
  - OR 分流合流用多出边汇入显式 `JoinNode(joinType=OR)` 验证。
- 节点类型覆盖当前稳定合同：`StartNode`、`ApprovalNode`、`HandleNode`、`CcNode`、`SubflowNode`、`JoinNode`、`EndNode`。
- `HandleNode` 只提交 `complete`，`CcNode` 验证为 pass-through，不伪造待办。
- 子流程必须先部署 child，再部署 parent；parent 的 `SubflowNode.subFlowKey` 指向本轮 child engine key。
- reset 用例只对真实 metadata-layer 记录执行：部署 -> 实例定制 adopted=true -> `reset` -> draft / processDefinitionId 清空 / adopted=false -> 显式 redeploy。
- 测试容器刷新必须通过开发平台页面的热刷新按钮触发，并观察运行时从 `BOOTSTRAPPING` 恢复为可用；reload API 返回本身不代表刷新完成。

### 4. `countersign-history.spec.ts`

覆盖：

- APF-COUNTERSIGN-001
- APF-COUNTERSIGN-002
- APF-COUNTERSIGN-003
- APF-HISTORY-001
- APF-COMPLETE-001
- APF-MULTIUSER-001
- APF-ACTION-VISIBILITY-001
- APF-ACTION-VALIDATION-001
- APF-COMMENT-001
- APF-FORMDATA-001

策略：

- 串行 / 并行 / 比例通过通过 source-backed route + canonical task action API 验证。
- 历史以 `/approval-center/detail` 内的 timeline 为准。
- 不再假设独立 `/approval-center/completed` 或 `/approval-center/history` 页面一定存在。

### 5. `dynamic-form-workflow.spec.ts`

覆盖：

- APF-DYNAMIC-FORM-001

策略：

- 通过运行时审批流管理页的“新建审批流”入口创建定义，这是唯一允许在运行时创建定义的场景。
- 选择已部署动态表单，验证统一字段源同时提供节点字段权限和条件表达式上下文。
- 发起后验证集中绑定记录，以及 `START`、`UPDATE` 表单历史和 `changedData` 查询。

## 执行顺序

所有命令都应在本次执行目标 worktree 根目录运行。先确认 `pwd` 位于目标 worktree，并让 `OB_TEST_*`、app-root 与证据目录指向同一轮环境；不要切换到其他任务 worktree。

### 0. 准备环境变量

基础 URL 变量由 manifest 标记为必需。管理员账号来自 `identities/admin` fixture，不需要额外导出。

```bash
export OB_TEST_BASE_URL='http://localhost:80'
export OB_TEST_APP_BASE_URL='http://localhost:80'
export OB_TEST_FRONTEND_BASE_URL='http://localhost:80'
export OB_TEST_RUNTIME_FRONTEND_BASE_URL='http://localhost:80'

# 可选：不设置时使用固定回归密码 AwfRegression@2026!
export OB_TEST_AWF_PASSWORD='AwfRegression@2026!'

# 推荐：每轮审批流回归使用新的本地后端数据根，避免复用上一轮数据库状态。
export DEV_E2E_BACKEND_DATA_ROOT="$PWD/tmp/playwright-it/backend/data-approval-flow-$(date +%Y%m%d%H%M%S)"
export DEV_E2E_BACKEND_DOCUMENT_ROOT="$PWD/tmp/playwright-it/backend/document-libraries-approval-flow-$(date +%Y%m%d%H%M%S)"
```

`approver1/2/3` 不再需要操作者手动提供密码。`approval-workflow-users-seed/prepare.sh` 会在测试前通过平台 API 重建这些账号、员工绑定和固定密码，并把候选人用的 `userIdentity` 与组织证据用的 `employeeId` 写到：

- `tmp/testing-evidence/workflow-approval-flow/approval-workflow-identities.json`

Playwright 脚本会读取这个映射，把 fixture 里的 `AWF_USER_APPROVER_*` 候选人别名替换成当前数据库可登录的账号身份（即 `employee.user`）。

如果 `@playwright/test` 尚未安装，先运行 manifest 中的安装脚本；依赖是否已安装应以当前 worktree 的实际检查为准；root `package.json` 是本场景的 Playwright 依赖边界。

### 1. 启动并确认本地运行时

```bash
bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh

curl -sS -o /dev/null -w '%{http_code}\n' "$OB_TEST_FRONTEND_BASE_URL/"

npx playwright test testing/e2e/regression/workflow/approval-flow/scripts/ --list
```

判定：

- 前端根路径必须可达；若 `/actuator/health` 在当前 profile 受认证保护，应以登录后的启动探针和页面加载作为 readiness，不能把 401 当作未启动。
- `--list` 必须能列出测试；若报 `Cannot find module '@playwright/test'`，先执行 `approval-flow-install-root-playwright`。
- 若从干净数据库运行，先让 `DEV_E2E_BACKEND_DATA_ROOT` 指向一个新的 `tmp/playwright-it/backend/data-*` 路径，再 bootstrap；随后执行 `approval-flow-seed-users`，它会重建 AWF 用户、员工绑定和候选人账号身份映射。

### 2. 先做 manifest plan

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode plan
```

### 3. 再按 script-id 执行

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-install-root-playwright

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-seed-users

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-form-and-design

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-runtime-actions

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-prepare-design-time-metadata

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-topology-and-reset

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-countersign-and-history

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-dynamic-form-workflow
```

也可以用一个可复制的顺序执行块：

```bash
set -euo pipefail

python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode plan

for script_id in \
  approval-flow-install-root-playwright \
  approval-flow-seed-users \
  approval-flow-form-and-design \
  approval-flow-runtime-actions \
  approval-flow-prepare-design-time-metadata \
  approval-flow-topology-and-reset \
  approval-flow-countersign-and-history \
  approval-flow-dynamic-form-workflow
do
  python3 testing/common/runners/playwright/browser_asset_bridge.py \
    --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
    --mode run \
    --script-id "$script_id"
done
```

## 凭据约定

### 已确认默认管理员

来自 `testing/common/fixtures/identities/admin/fixture.yaml`：

- account: `admin`
- password: `666666`

如果隔离测试容器已经通过平台初始化流程改写了管理员密码，但当前开发平台浏览器仍有有效登录态，可临时设置 `OB_TEST_ADMIN_ACCESS_TOKEN` 复用该 Bearer token。该变量只用于测试夹具、设计时资产脚本和 Playwright 管理员登录；未设置时仍使用上述账号密码登录，不要把 token 写入仓库或报告。

### approver 浏览器凭据

当前仓库的 approval workflow user seed 会创建这些登录账号：

- `awf.approver1`
- `awf.approver2`
- `awf.approver3`

默认密码：

- `AwfRegression@2026!`

如需覆盖单次运行的原始密码，可设置：

- `OB_TEST_AWF_PASSWORD`

不要手动猜测密码，也不要把 approver 凭据当作外部 blocker。测试前置准备会通过后端 `UserServiceImpl` 路径创建用户，让密码进入平台编码存储。

## 当前已知限制

### 1. advanced action dialog 页面已纳入聚合入口证据

approval-center schema 引用了：

- `/approval-center/transfer-form`
- `/approval-center/add-sign-form`
- `/approval-center/free-reject-form`
- `/approval-center/free-approval-form`

当前仓库已有对应 ui-model/ui-schema 页面资产。Batch 4 后相关 case 必须记录：

- 普通审批人身份
- dialog path 与 form id
- selector GET URL / status / optionCount
- confirm action 的 schema 来源

- 同源 task API direct POST 仍可作为底层 action 合同或 setup 使用，但不能作为高级动作弹窗打开、selector 请求或 confirm 来源的替代证据。
## deploy 步骤与脚本内部 setup 的关系

场景 contract 里，`APF-DEPLOY-001` 仍然是独立的 harness 语义步骤。

为了让 runtime / countersign 脚本在本地和 CI 中保持隔离性，当前脚本实现会：

- 为每个测试在开发平台生成、保存并提交唯一 definition
- 从开发平台页面点击热刷新测试容器，并确认运行时加载出 `source=metadata-layer` 的同名模型
- 调用真实 deploy API 显式产生引擎版本
- 只把这一步当成**测试内部 setup**，而不是把它记作对 APF-DEPLOY-001 的单独浏览器覆盖

这意味着：

- contract/cases 里关于 deploy 的业务语义仍然成立
- runtime 脚本内部的 deploy 只是为了避免多个 case 共享同一个可变流程定义状态
- 普通流程不允许通过运行时 definition API 临时创建；运行时创建只用于动态表单审批流入口用例
- 真正的 deploy 业务验证仍以 harness / API evidence 为准

### 2. standalone completed/history route 未确认

当前确认存在：

- `/approval-center`
- `/approval-center/detail`
- `/approval-center/start`

未确认存在：

- `/approval-center/completed`
- 独立 `/approval-center/history`

所以已完成/历史类 case 现在用 task disappearance、detail timeline、instance state 证明，不再伪造 route。

### 3. `@playwright/test` 依赖未安装

若运行：

```bash
npx playwright test testing/e2e/regression/workflow/approval-flow/scripts/ --list
```

报：

- `Cannot find module '@playwright/test'`

则这是工作区依赖问题，不是 manifest/cases/scripts 路径错误。
本场景已在仓库根提供 `package.json` / `package-lock.json`，执行 `approval-flow-install-root-playwright` 或 `npm ci --no-audit --no-fund` 后再运行。

## 诊断优先级

1. 登录后的启动探针和页面加载
2. `/actuator/health`（当前 profile 未受保护时）
3. bootstrap 输出
4. Docker / Spring Boot / nginx 日志
5. 浏览器 console / pageerror
6. selector 或 contract drift

## 遇到什么情况应该直接记 blocker

- `approval-flow-seed-users` 未执行，导致 `approval-workflow-identities.json` 没有当前数据库的候选人账号身份映射
- advanced dialog ui-schema 缺失
- standalone completed/history route 在源码中找不到
- Playwright 依赖缺失，导致 `--list` 或 `run` 无法加载配置
