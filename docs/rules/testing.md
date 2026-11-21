# Testing

Ouroboros 测试体系的文档级入口。定义测试规则、测试方法和运行手册索引。

`testing/` 是 Ouroboros 长期测试资产的统一根目录，用来承载跨模块、跨运行时、跨前后端的测试标准、场景、夹具和 runner 规则。这里放的是长期结构化资产，不是一次性的临时命令集合。

## 阅读顺序

1. 本文件（`docs/rules/testing.md`）：测试规则、方法和索引
2. [testing/test-management-standard.md](../../testing/test-management-standard.md)：测试分类、准入、执行批次、结论与清理
3. [testing/unified-testing-playbook.md](../../testing/unified-testing-playbook.md)：综合联调与测试执行标准
4. [testing/test-asset-management.md](../../testing/test-asset-management.md)：测试资产治理规范
5. 目标场景所在的 `testing/integration/**` 或 `testing/e2e/**`

## 核心原则

1. 先按测试类型分层（integration/e2e），再按目的分类（smoke/regression/feature）
2. 集成场景和 E2E 场景共享同一份契约与用例语义
3. 公共夹具、runner、模板统一收口到 `testing/common/`
4. 夹具必须可组合，依赖通过显式参数传入
5. 原始证据与长期标准分开存放
6. 本地运行时在执行前必须经过完整性预检
7. 长期保留的是构造过程（契约、规则、playbook），不是构造结果（某次 run 的 inventory/summary）
8. Maven 多模块定向测试必须先校验 reactor 边界

架构门禁检查整个 Maven reactor，而不只检查 `-pl` 选中的目标。先从仓库根目录执行 `mvn -DskipTests test-compile`，再执行 `mvn -pl testing/architecture-tests -am test -Dsurefire.failIfNoSpecifiedTests=false`；两阶段使用相同的 profile。缺少编译产物时门禁必须报告扫描不完整。模块范围、循环依赖及精确基线规则见[架构约束检查指南](../architecture-constraint-guide.md#模块扫描与循环依赖判定)。

## 测试用例卫生规则

新增测试必须先证明“测什么行为结果、属于哪个模块或场景、覆盖哪个逻辑分支”，再进入代码实现。

行为验证默认验证行为结果，不通过检查代码结构或源码模式来证明行为：

- 可验证的结果包括返回值、API 响应、持久化数据、发出的事件或消息、权限判定、状态迁移后的可观察状态、用户能看到或操作到的 UI 输出
- 禁止把代码结构或源码模式当作结果，例如断言是否存在某个方法、类、字段或 helper，读取源码后匹配字符串/正则/AST，或检查内部实现是否符合某种写法
- 如果公开契约本身就是结构化产物，例如 schema、OpenAPI、导出的元数据、序列化后的流程定义，可以通过公开 API、命令或导出结果断言这个公开输出的结构，但仍不得读取源码或断言内部实现模式

组件级测试按模块和组件组织：

- 后端组件测试放在对应 Maven 模块 `src/test/` 下，package 优先镜像生产 package；前端组件测试放在对应前端 package 的 `tests/` 或既有测试目录下
- 测试文件名和用例名必须指向被测组件的公开行为结果、领域规则或可观察副作用，不能只指向源码文件存在性
- 同一组件的用例应覆盖适用的逻辑分支：`normal`、`invalid/error`、`empty/boundary`、`state/transition`、`permission`、`dependency/recovery`；不适用的分支可标记 `N/A`

系统集成和 E2E 测试按场景组织：

- 先在 `testing/integration/**/<scenario>/` 建立或复用正式场景，再补 `scenario.yaml`、`contract.md`、`cases.md`
- 每个 case 必须有稳定 case ID、目标行为结果、所属分支、前置条件、通过标准和证据要求
- E2E manifest 和测试实现必须回指对应场景与 case ID，不得脱离场景契约另起一套语义

禁止新增以下测试：

- 读取源码后用字符串、正则、私有函数名、内部文件名、marker 文本等证明“代码写了某内容”的测试
- 断言代码结构或源码模式、但不验证行为结果的测试，例如某方法是否存在、某 class/helper 是否存在、源码是否匹配某个模式、内部实现是否按某种写法组织
- 只为覆盖率补数的测试，例如只调用 getter/setter/constructor/enum、只断言“不抛异常”、只验证 mock 调用但不验证输出、状态或副作用
- 没有模块、组件、场景或 case ID 归属的零散测试文件
- 与既有 case 语义重复、但未说明差异分支或风险边界的测试

## 覆盖率与报告边界

新功能需有归属明确的行为单元测试，覆盖率门槛为 70%。根 [POM](../../pom.xml) 的 JaCoCo 规则使用 BUNDLE 级 LINE COVEREDRATIO 0.70；这与“每个新方法自动达到 70%”不是同一统计口径。定向测试核对目标模块的继承、skip/profile 与实际报告，不能据插件声明推定本次已生成或通过覆盖率检查。构建脚本可以跳过 JaCoCo，单独的 test 命令也不等于执行 verify 阶段的 check。

后端测试结果按实际 Surefire/Failsafe 配置保存在模块 `target/`；JaCoCo 的标准报告位置为 `target/site/jacoco/`，仅在相应目标实际执行且有数据时形成。前端按包配置核对 Vitest/Playwright 结果。报告保留当前提交、命令、实际场景和跳过项，行为通过与覆盖率通过分别说明。

## 测试类型与位置

| 测试类型 | 位置 | 用途 |
|---------|------|------|
| 单元测试 | 各模块 `src/test/` | 模块内组件测试 |
| 集成测试 | `testing/integration/` | 跨模块、跨运行时、跨前后端联调 |
| E2E 测试 | `testing/e2e/` | 全自动端到端测试 |
| 前端测试 | `frontends/web/main-app/tests/`、各微前端模块 `tests/`、`frontends/web/TESTING.md` | 前端单元测试与 E2E 测试 |

### 集成测试分类

- `testing/integration/smoke/`：验证主链路可用，覆盖窄但必须稳定
- `testing/integration/regression/`：重大重构、合并前回归、兼容性验证
- `testing/integration/feature/`：新功能联调、口头指定用例、探索场景

## 测试资产目录说明

- `testing/unified-testing-playbook.md`：综合联调与测试执行标准
- `testing/test-management-standard.md`：测试分类、准入、执行批次、结论与清理规范
- `testing/test-asset-management.md`：测试资产治理规范
- `testing/integration/`：集成测试场景，按 `smoke / regression / feature` 分类
- `testing/e2e/`：全自动 E2E 入口，按 `smoke / regression` 分类
- `testing/common/`：公共夹具、runner、模板等共享资产
- `tmp/testing-evidence/`：一次性运行证据默认输出目录
- `tmp/`：本地运行时、scratch 目录、临时证据和调试副本的统一落点，不纳入版本库

## 什么不应该放在 testing/ 目录

以下内容原则上仍应留在各自模块附近：

- 单元测试
- 模块内组件测试
- 与具体前端工程强耦合的 Playwright 测试实现
- 与具体后端模块强耦合的 JUnit/集成测试实现

`testing/` 关注的是"跨层联调、场景回归、夹具编排、报告归档"。

## 正式场景最小资产

每个 `testing/integration/**/<scenario>/` 目录至少包含：

- `scenario.yaml`
- `contract.md`
- `cases.md`

如果场景需要 AI agent / 人工辅助执行，则增加：

- `harness.playbook.md`

如果场景具备稳定自动化条件，则在 `testing/e2e/**` 下增加对应 manifest。

## 测试完成度报告规范

每个测试场景必须明确报告以下维度：

### 1. 执行状态分类

- `runnable`：可自动执行或通过 harness 执行
- `skipped`：本轮跳过（需说明原因）
- `gated`：前置条件未满足（需说明缺失项）
- `manual`：需人工介入（需说明介入点）
- `not-covered`：未覆盖（需说明原因）

### 2. 断言级别

- `strict`：完整断言，包括数据正确性、边界条件、异常处理
- `basic`：基础断言，验证主流程通过
- `smoke`：冒烟级别，仅验证服务可达和基本响应
- `none`：无断言，仅记录执行

### 3. 结果分类

- `pass`：通过（需有证据支持）
- `fail`：失败（需有足够证据支持的真实产品问题）
- `blocked`：阻塞（依赖未就绪、服务未通）
- `excluded`：排除（需记录原因，默认视为临时状态）
- `precondition-gap`：前提缺口（账号、组织、公司、权限等上下文未补齐）
- `runtime-baseline-invalid`：运行时基线失真（不适合作为 merge gate）

## 运行时与 E2E 夹具规则

### 运行时路径对齐

- 测试使用的 `app-root` 必须与目标运行时的实际路径一致
- 禁止使用历史临时目录或假设路径
- 仓库地址必须通过显式参数传入：`--repo NAME=/abs/path`

### 完整性预检

执行前使用目标 fixture 已声明的 `--check` 入口；统一编排通过 `fixture_harness.py --scenario <scenario.yaml> --action check --mode plan` 核对 fixture 输入与生命周期计划，满足前提后再 `--mode run`。并非所有 runner 都有 `--check` 参数，浏览器 bridge 的 `--mode plan` 只做浏览器资产预检，不代替运行时健康和数据检查：

- data fixture：验证仓库绑定或 app-root 路径真实存在且可访问
- runtime fixture：验证 base URL 可达，运行时服务正常响应
- identity fixture：验证凭据有效，认证上下文完整

如果预检失败，不应继续执行，避免将环境问题误判为业务代码缺陷。

### 运行时快照定义顺序

当运行时需要快照定义时，执行顺序必须是：

1. `seed`：初始化数据和配置
2. `restart`：重启运行时使配置生效
3. `check`：验证运行时状态和数据完整性

## Case ID 与 Manifest 对齐

每个测试场景必须保持以下文件的 case ID 一致：

- `cases.md`：用例表
- `e2e.yaml` 或对应 manifest：E2E 配置
- 测试实现代码（spec 文件）

Case ID 规则：
- 必须在场景内保持稳定和唯一
- 必须在 `cases.md` 中明确定义
- 必须在 E2E manifest 和测试实现中镜像使用
- 格式由场景自定义（如 `SRT-QUERY-001`、`ldap-login-smoke-001`）

## 运行手册索引

### 核心测试文档

- [testing/test-management-standard.md](../../testing/test-management-standard.md)：测试管理规范
- [testing/unified-testing-playbook.md](../../testing/unified-testing-playbook.md)：综合执行标准
- [testing/test-asset-management.md](../../testing/test-asset-management.md)：资产治理规范

### 代表场景运行手册

#### Smoke 场景

- [LDAP 登录联调](../../testing/integration/smoke/auth/ldap-login/harness.playbook.md)
- [低代码全链路](../../testing/integration/smoke/lowcode/full-app-lifecycle/harness.playbook.md)

#### Regression 场景

- [Metadata 与高代码数据 API 回归](../../testing/integration/regression/data-api/metadata-high-code-regression/harness.playbook.md)
- [审批流回归](../../testing/integration/regression/workflow/approval-flow/harness.playbook.md)
- [统计报表运行时查询与 BI](../../testing/integration/regression/statistics-reporting/runtime-query-and-bi/harness.playbook.md)

#### Feature 场景

- [口头指定用例](../../testing/integration/feature/oral-cases/harness.playbook.md)
- [逻辑流验证诊断](../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/harness.playbook.md)
- [业务 Action/Event](../../testing/integration/feature/lowcode/business-model-actions-events/contract.md)：契约、用例与正式 runner
- [动态外部认证与 OIDC](../../testing/integration/feature/auth/sso-dynamic-config-oidc/contract.md)：独立实例、身份与证据前提

#### E2E 场景

- [统计报表运行时查询与 BI E2E](../../testing/e2e/regression/statistics-reporting/runtime-query-and-bi/runbook.md)
- [E2E 测试指南](../../testing/e2e/e2e-test-guide.md)

## 历史证据与现行流程

历史经验文章已归关联任务附件及固定 Git 提交，不再维护 articles 索引。测试分类、夹具与运行步骤以当前 testing 正文为准，历史成功不替代当前基线验证。

## 存量资产的接入边界

统一目录已经承载场景、fixture、runner 和 manifest。模块内实现仍可通过正式桥接接入，例如：

- `testing/common/runners/api/data_api_regression`
- `platform/.../e2e`
- `frontends/.../tests/playwright`

桥接是现有执行边界，不表示旧入口已完成迁移；清理时须先验证行为与 case ID 的等价覆盖。

## 证据与清理

### 证据存放

- 一次性运行证据：`tmp/testing-evidence/`
- 本地运行时目录：`tmp/`
- 调试 scratch 目录：`tmp/`
- 临时 app-root 副本：`tmp/`

`tmp/` 下的内容默认视为不入版本库的本地状态。需要交付和长期追溯的证据在清理前保存到任务或 CI 制品，并注明访问条件与保留期。

### 清理规则

- 每次测试跑完后，及时清理无用后台进程
- 只保留最新且必要的进程
- 如果要保留进程，必须在报告或交接说明里写明：`PID / 端口 / 用途 / 预计保留时长`

### 证据要求

每一轮执行都必须先有证据，再有结论：

- 证据落到 `tmp/testing-evidence/**`
- 报告引用场景 ID 和 case ID
- 报告明确记录排除项、前提缺口和阻断项
- 关键失败要能回溯到原始日志、响应、trace 或截图

## 前端测试规范

前端测试规范见 [frontends/web/TESTING.md](../../frontends/web/TESTING.md)。

## Agent 反馈循环

测试执行相关的 Agent 反馈循环入口：

- 测试执行：`bash scripts/agent-feedback/run-tests.sh --help`
- 关键旅程 E2E：`bash scripts/agent-feedback/run-key-journey-e2e.sh --help`
- 日志查询：`bash scripts/agent-feedback/query-logs.sh --help`
- 健康检查：`bash scripts/agent-feedback/check-health.sh`

## 相关文档

- [AGENTS.md](../../AGENTS.md)：AI Agent 导航入口
- [docs/development-guide.md](../development-guide.md)：开发环境配置
- [docs/runbooks/README.md](../runbooks/README.md)：运维手册
- [docs/runbooks/troubleshooting-guide.md](../runbooks/troubleshooting-guide.md)：故障排查指南

HTTP API 的源定义、生成一致性与消费者兼容要求见[契约生命周期](../api/contract-lifecycle.md)；当前自动化缺口由 API-001 跟踪。发布阻断与跳过开关由[CI/CD](ci-cd.md)维护。
