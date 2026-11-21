# Low-Code Full App Lifecycle Contract

## Objective

把“从开发平台建应用、建业务模型、启动运行时、打开应用、登录并验证生成 CRUD/查询”的完整低代码链路沉淀成统一测试体系下的正式场景。

## Scope

- 在范围内：
  - develop 平台与 runtime 基座的双运行时拓扑
  - 应用创建、业务模型创建、运行时启动
  - 生成应用入口、登录和基础 CRUD/查询验证
  - 业务模型与运行时生成 UI 的联调观察点
- 不在范围内：
  - 某个具体业务应用的深度业务规则
  - 非主链路的可选设计器能力
  - 所有前端细节的一次性全覆盖自动化

## Fixtures

- Services:
  - 无强制外部依赖
- Runtimes:
  - `runtimes/develop`
  - `runtimes/mothership`
- Frontends:
  - `frontends/develop-shell`
  - `frontends/runtime-shell`
- Data:
  - `data/app-metadata-root`
- Identities:
  - `identities/admin`

## Pass Criteria

- 场景能统一描述 develop 与 mothership 两套运行时及其前端壳
- 用例表能覆盖应用创建、建模、启动、登录、基础 CRUD/查询
- harness 证据能先确认拓扑和夹具状态，再进入浏览器/接口主链路
- 生成式 UI 和业务模型 API 的关系在场景中被明确记录，而不是靠操作者记忆

## Failure Classification

- 依赖/环境问题：
  - develop 或 mothership 未启动
  - app-root 或生成目录不可访问
- 后端问题：
  - 应用创建/模型创建接口失败
  - 运行时启动失败
- 前端壳问题：
  - develop-shell 或 runtime-shell 入口不可打开
  - 代理或菜单装配错误
- 页面交互问题：
  - 应用创建向导失败
  - 生成页面无法新增/编辑/删除/查询
- 权限与上下文问题：
  - 登录成功但菜单、组织或模型上下文缺失

## Evidence Requirements

- `tmp/testing-evidence/lowcode-full-app-lifecycle/**` 下的 fixture harness 报告
- 应用创建、业务模型创建、运行时启动的关键日志/接口证据
- 浏览器页面、网络请求、console 证据
- 生成应用 CRUD/查询验证结果
