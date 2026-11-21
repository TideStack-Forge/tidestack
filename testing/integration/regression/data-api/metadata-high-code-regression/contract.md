# Metadata And High-Code Data API Regression Contract

## Objective

验证重大重构下的数据 API 回归是否仍在可接受范围内，并把当前已有的 metadata 生成接口回归能力和高代码数据 API 回归能力纳入统一测试体系。这个场景覆盖两类对象：

- 业务模型生成的元数据 API
- 基座模块里的高代码数据 API 种子清单

## Scope

在范围内：

- 元数据接口 inventory 生成
- 元数据 live 执行 rings：
  - `metadata-list`
  - `metadata-detail`
  - `metadata-query-conditions`
  - `metadata-create`
  - `metadata-update`
  - `metadata-delete`
- 高代码 live 执行 GET rings：
  - `high-code-read`
  - `high-code-special-read`
- 高代码 live 执行写 rings：
  - `high-code-write`
  - `high-code-special-write`
- 高代码数据 API 盘点
- 已有回归报告、用例表、证据目录的统一归档入口

不在范围内：

- 高代码接口的全量 live 自动执行
- 非数据 API 相关的纯前端行为
- 已确认属于项目自身问题的 `baseInfo.deviceManage` 相关 API

## Current Bridge Boundary

当前阶段采用“薄桥接”而不是重写回归引擎：

- 场景契约与执行入口转入 `testing/`
- 现有执行核心仍复用 `testing/common/runners/api/data_api_regression/run_regression.py`
- 高代码种子会进入 inventory 和用例表
- metadata `detail` 已作为独立 ring 桥接
- 高代码 GET live 执行已通过 ring 桥接
- 高代码写环只桥接“已执行就绪”的 seed；无样本、无路径参数样本、无清理模板的用例仍保留为 pending

## Truthfulness Rules

这个场景的门禁结论必须先满足“资产真相”：

- 普通 `views.detail` 不能被当成主子表
- 显式 `views.add` 下遗漏的必填字段/关系，不能继续算作 stage-one 可写
- 高代码 seed 要区分“已建档”和“已执行就绪”
- 借用 runtime 如果连未认证 HTTP 探针都不符合预期，必须先判 `runtime-baseline-invalid`

这意味着：

- 覆盖率数字可以因为剔除伪分母而下降
- 这属于体系收敛，不属于质量退化

## Fixtures

- Runtimes:
  - `develop`
  - `mothership`
- Data:
  - `data/app-metadata-root`
- Identities:
  - `identities/admin`
  - `identities/permission-boundary-user`

当前 bridge 默认会从 fixture 输出读取：

- `data/app-metadata-root -> appRoot`
- `identities/admin -> loginPayload`
- `runtimes/develop -> baseUrlEnv`
- `runtimes/mothership -> baseUrlEnv`

其中 `data/app-metadata-root` 的 `appRoot` 并不是仓库内硬编码路径，也不应依赖某次历史 run 留下来的临时目录，而是由以下显式输入解析得到：

- 优先推荐：`--repo app-metadata=/abs/path/to/repo`
- 兼容覆盖：`--app-root`
- 环境变量：`OB_TEST_APP_ROOT` 或 `OB_TEST_REPOS`

如果场景需要多个仓库，统一使用可重复的 `--repo NAME=/abs/path` 传入，由 fixture 自己声明消费哪个 alias。

## Pass Criteria

- Inventory-only 模式可以从新 runner 成功生成报告产物
- Inventory-only 和 execute 模式都会先生成 `fixture-preflight.json`
- 元数据 live 执行 rings 能通过统一 runner 调起旧回归引擎
- 高代码 read/write rings 能通过统一 runner 调起旧回归引擎
- runtime preflight 同时校验类路径完整性和未认证 HTTP 探针
- 场景文档明确说明当前自动化边界和排除项
- 证据与报告路径有统一落点

## Failure Classification

- 依赖/环境问题：
  - app-root 缺失
  - 运行时未启动
  - 登录或 token 获取失败
  - 未认证 HTTP 探针不符合预期
  - HTML / 纯文本 / 连接失败导致的 transport 异常
- 回归问题：
  - 元数据 live 执行返回真实回归
  - 直接适配器兼容链路断裂
- 范围外问题：
  - 排除范围内的项目自有问题
  - 尚未执行就绪的高代码写 seed

## Evidence Requirements

- `tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs` 下的 inventory / cases / summary 报告
- execute 模式下的 `tmp/testing-evidence/metadata-high-code-data-api-regression/<run-id>/results.json`
- 每次 run 的 `fixture-preflight.json`
- 必要时补充 `tmp/testing-evidence/metadata-high-code-data-api-regression/analysis/` 下的 live 失败分析记录
- runtime preflight 若失败，报告中必须包含 HTTP probe 与类路径完整性证据

## Asset Boundary

这个场景长期保留的是：

- `scenario.yaml`
- `contract.md`
- `cases.md`
- `harness.playbook.md`
- `e2e.yaml`
- runner / fixture / standards 中沉淀出的规则

这个场景默认不把以下内容当成长效资产：

- 某次执行生成的 inventory / cases / summary
- 某次 borrowed runtime 的排查结论
- 某次 live 失败分析

这些都应归档到 `tmp/testing-evidence/`，只把抽象后的方法论回填到长期标准。
