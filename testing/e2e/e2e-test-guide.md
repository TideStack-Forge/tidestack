# E2E 测试执行指南

本指南说明如何在 Ouroboros 中运行**真实浏览器 E2E**，以及如何把它与后端集成测试、环境健康检查和快速调试区分开。

## 先明确三件事

1. **浏览器 E2E 的主入口是 Playwright + manifest runner**，不是 Maven。
2. **Java 集成测试是辅助证据**，能证明后端契约和动作语义，但不能代替真实页面与微前端装配验证。
3. **健康检查必须看 `/actuator/health`**，不要再用登录 API 冒充 readiness。

## 运行架构

### 浏览器 E2E（主入口）

- runner: `testing/common/runners/playwright/browser_asset_bridge.py`
- scenario manifest: `testing/e2e/**/e2e.yaml`
- contract truth source: `testing/integration/**/{scenario.yaml,contract.md,cases.md,harness.playbook.md}`

### 后端集成测试（辅助验证）

- 适合验证 API、Flowable 动作映射、payload 语义、版本历史等
- 不适合验证：
  - 前端 route 是否真实存在
  - 微前端壳是否正确装配
  - 浏览器按钮/表单/selector 是否真实可用

## 环境准备

### 推荐 bootstrap

优先使用：

```bash
bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh
```

这个脚本对应的 contract 资产：

- `platform/ouroboros-develop/e2e/contracts/runtime-management.local-bootstrap.contract.md`

### 必需环境变量

- `OB_TEST_BASE_URL`
- `OB_TEST_APP_BASE_URL`
- `OB_TEST_FRONTEND_BASE_URL`
- `OB_TEST_RUNTIME_FRONTEND_BASE_URL`

本地默认值通常可以这样设置：

```bash
export OB_TEST_BASE_URL='http://localhost:80'
export OB_TEST_APP_BASE_URL='http://localhost:80'
export OB_TEST_FRONTEND_BASE_URL='http://localhost:80'
export OB_TEST_RUNTIME_FRONTEND_BASE_URL='http://localhost:80'
```

审批流场景不需要人工提供 approver 密码；它通过 `approval-workflow-users-seed` fixture 生命周期重建 `awf.approver1/2/3`，默认固定测试密码为 `AwfRegression@2026!`，并把实际 employeeId 映射写入 `tmp/testing-evidence/workflow-approval-flow/approval-workflow-identities.json`。

### 健康检查

```bash
curl http://localhost:80/actuator/health
```

判定规则：

- `503` → LifecycleGuardFilter 仍在拦截，请继续等待或排查启动失败
- `200` → 生命周期完成，可进入浏览器自动化
- 外部访问不到 `/actuator/health` → 优先检查 nginx 是否暴露 `/actuator/` 反代

不要使用：

```bash
curl http://localhost:80/api/auth/login
```

作为健康检查。登录 API 最多只能作为附加诊断信号。

## 浏览器 E2E 执行方式

### develop 本机联调补充

当你不是走完整容器链路，而是直接在本机联调 develop 壳与后端时，优先确认以下前置条件：

#### 前端壳与后端端口

- 浏览器 bootstrap 默认前端壳为 `http://127.0.0.1:18081`（`DEV_E2E_FRONTEND_PORT` 可覆盖）
- 同一 bootstrap 默认后端为 `http://127.0.0.1:18080`（`DEV_E2E_BACKEND_PORT` 可覆盖）
- 进入浏览器自动化前，先确认前端 dev server 的 `/api/` 代理是否确实指向当前后端端口

#### 后端本机启动

如果是本机直接拉起 develop 后端，不走 Docker 容器，优先复用仓库 bootstrap，由它重建 classpath 并传入端口、数据根和配置覆盖：

```bash
bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh
```

启动后先验证：

```bash
curl http://127.0.0.1:18080/actuator/health
```

只有 `/actuator/health` 返回正常，才进入页面级联调。

#### 浏览器认证态注入

develop 壳读取的不是 raw JWT，而是结构化 token 存储。手工联调或浏览器脚本注入时：

- `localStorage.Ouroboros_token` 必须是 **JSON 字符串**
- JSON 中至少应包含 `accessToken` / `refreshToken`
- 直接写入单个 JWT 字符串时，页面通常会表现为已打开但接口持续未认证

#### 详情页 / 弹窗类页面验证

对依赖列表行上下文的详情页或弹窗，不要只靠猜测裸 URL 直达作为唯一验证方式。

推荐顺序：

1. 先通过列表或真实入口打开页面
2. 再查看底层 API 是否返回预期状态字段
3. 最后校验 UI 按钮、文案和跳转行为

### 先跑 plan

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest <path-to-e2e.yaml> \
  --mode plan
```

### AMIS / CXD 下拉动作验证

develop 管理面里的 AMIS / CXD 下拉菜单不要假设“看到文案就等于点击目标唯一稳定”。像 `助手详情` 这类动作，真实可点击节点可能会随渲染层级、包裹标签和事件绑定位置变化。

排查或补强 E2E 时，按下面顺序尝试，避免把问题误判成后端或权限异常：

1. 先点下拉项的列表容器节点。
2. 如果无效，再尝试内部 anchor-like 子节点。
3. 再尝试仅包裹文案的 text wrapper。
4. 鼠标点击不稳定时，补一次键盘激活，例如聚焦后回车。
5. 仍不稳定时，最后再用 DOM 级 `click()` / `evaluate()` 作为兜底验证。

对 helper `助手详情` 这一类入口，当前更稳的优先级应调整为：

1. 先确保 dropdown 已实际展开。
2. 优先定位菜单项内部的 `<a>` 并调用该 `a.click()`。
3. 只有在内部 `<a>` 不存在或不可点时，再退回 `li` 容器或文案 wrapper。

这条经验来自当前代码库的真实页面验证：对于 `helper-dropDown`，菜单项文本存在并不代表外围 `li` 是最稳定的 action target，真正稳定的触发点通常是内部 `<a>`。

这不是通用浏览器测试技巧，而是当前代码库中 AMIS / CXD 下拉动作的专项经验：不要把 selector 固定死在单一节点上；验证 `助手详情`、菜单动作弹窗、同类 dropdown action 时，应显式准备多个可信 DOM target。

### 再按 script-id 跑

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest <path-to-e2e.yaml> \
  --mode run \
  --script-id <script-id>
```

示例（审批流回归）：

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode plan

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
  --script-id approval-flow-countersign-and-history
```

## 后端集成测试怎么用

后端集成测试适合：

- 验证 `SubmitTaskVO` / advanced action payload 语义
- 验证 definition deploy/version/timeline/diagram API
- 验证 Flowable service 层行为

例如：

```bash
mvn -Pjdk21 -pl :ouroboros-approval-workflow-web -am test \
  -Dtest=ApprovalWorkflowTaskControllerIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

或：

```bash
mvn -Pjdk21 -pl :ouroboros-approval-workflow-web -am test \
  -Dtest=ApprovalWorkflowDefinitionControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

> 这些测试是支撑浏览器回归的“契约证据”，不是 E2E 的替代品。

## 微前端快速调试

使用 `devMicroEntryMap` 注入本地微前端：

```javascript
sessionStorage.setItem('devMicroEntryMap', JSON.stringify({
  '/approval-workflow-designer/': 'http://localhost:3001'
}))
```

关键点：

- key 是 shell 中的**原始 entry**，通常是相对路径
- value 是本地开发服务器地址
- 刷新页面后生效，无需重启容器

相关源码：

- `frontends/web/main-app/src/dev-tools/index.ts`
- `frontends/web/main-app/src/common/micro/utils.js`

## 后端快速调试

需要快速验证后端修复时，可以替换 jar 后重启容器。

常见落点：

- `/app/base/service/third-party-lib`
- `/app/base/service/ouroboros-lib`
- `/app/metadata/extend`

示例：

```bash
docker cp core/ouroboros-approval-workflow/ouroboros-approval-workflow-web/target/ouroboros-approval-workflow-web-<version>.jar \
  ouroboros-develop:/app/metadata/extend/

docker restart ouroboros-develop
```

## 常见问题分类

### 1. 环境未就绪

- `/actuator/health` 仍是 `503`
- bootstrap 脚本未成功
- 容器没有全部起来

### 2. nginx / 路由问题

- 外部访问 `/actuator/health` 失败
- `/api/` 或页面路由返回 404

### 3. 前端壳 / 微前端问题

- 页面空白
- console / pageerror 报错
- `devMicroEntryMap` key 写错

### 4. 后端异常

- deploy/start/task action/timeline API 报错
- Flowable 部署或任务执行失败

### 5. 测试契约漂移

- selector 来自旧脚本、仓库中并不存在
- 假设了未确认的 route
- 把业务模型写成“表单设计器”
- 把 deploy 写成 develop runtime-management 动作

## Playwright 依赖预检

如果本地执行：

```bash
npx playwright test --list
```

报错：

- `Cannot find module '@playwright/test'`

则说明当前工作区 Playwright 依赖未安装完整，这是本地依赖问题，不是 manifest 或 contract 结构错误。审批流场景已在仓库根声明 `@playwright/test` 依赖，可执行：

```bash
python3 testing/common/runners/playwright/browser_asset_bridge.py \
  --e2e-manifest testing/e2e/regression/workflow/approval-flow/e2e.yaml \
  --mode run \
  --script-id approval-flow-install-root-playwright
```

或直接运行：

```bash
npm ci --no-audit --no-fund
npx playwright test --list
```

## 审批流回归场景入口

审批流场景的更具体说明见：

- `testing/e2e/regression/workflow/approval-flow/e2e-test-guide.md`
