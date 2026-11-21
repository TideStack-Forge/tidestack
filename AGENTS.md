# Ouroboros

Ouroboros（TideStack / 潮汐栈）是采用 Java/Maven 多模块与 React 微前端的高低代码融合应用开发平台。

## 规则与纪律

1. 开始修改前明确目标、范围、验收条件，执行 pwd、git status 和必要的 rev-parse，确认仓库、分支、HEAD、已有变更及当前任务。遵守指定 worktree；本仓库额外工作区默认放在 .worktrees/。保留无关改动与原有暂存状态。
2. 按任务读取适用的根/局部规则、功能、需求、领域定义、设计、契约和已接受决定。关键歧义在依赖操作前澄清；信息已足够时主动推进。
3. 以当前架构为默认依据；写前识别正式代码路径、扩展点、不变量、根因与最小正确修改。优先复用、扩展；确需改变设计先说明现有设计不足与理由。
4. 禁止绕过 Registry/Factory/SPI 等正式机制，禁止隐藏注册表、平行实现、补丁叠补丁和不成比例的抽象。不混入无关整理；只清理本次产生的孤立代码。
5. 按变更风险选择定向、组合与恢复场景验证。失败先调查环境、前提、行为与必要 Git 历史，不降低预期或扩大豁免来换绿。需要时用 Git 历史核对设计取舍和回归起点。
6. 测试验证可观察行为、结果和逻辑分支；禁止用源码文本匹配、源码结构或 AST 断言代替行为验证。公开 Schema/导出产物本身是契约时可验证其结构。
7. 改代码后检查功能介绍、需求、设计、接口和操作说明是否需要同步，必要时一并修改。当前实现、已确认待实现、提案、已启用、验证与发布状态分别描述。
8. 每步以可验证结果推进。交付说明实际变更、运行的检查及范围、失败/跳过/未执行、剩余风险；文件存在、测试存在或已提交均不能冒充验收、部署或发布成功。
9. 合并、切换上下文、回收 worktree 前检查状态；确认必要提交已集成或有可恢复去向，另行保存需保留的忽略文件。清理仅限本任务拥有的进程和资源。
10. 修改前核对路径位于指定 .worktrees/ 工作区，每次 Git/文件操作使用明确 workdir。搜索文本用 rg；支持的语言做语法级定位/重构优先 ast-grep（sg）。
11. docs/ 及其全部子目录只保留反映最新情况的长生命周期文档。设计与能力现状分别维护在 docs/design/ 和 docs/features/。`.codestable/`、执行计划、实施日志、评审记录、单次验收及发版操作记录不得进入版本库，长期结论更新唯一正文，过程记录归关联任务，临时证据归 tmp/testing-evidence/。具体边界见[文档治理](docs/rules/documentation.md)。

## 红线

- 禁止空 catch、忽略异常、未实现的 TODO、循环依赖和跨层旁路；依赖遵循 libs → core/security → ability → app/dev → platform，精确例外见架构约束。
- 跨模块业务依赖核心契约，正式装配入口选择实现；Java 与 Maven 生产依赖同时遵守[接口与实现依赖隔离](docs/architecture-constraints.md#15-接口与实现依赖隔离-p1-16)。
- SPI实现和自动配置按 [META-INF注册机制](docs/design/cross-cutting/extension-mechanisms.md)注册。
- 业务异常使用 BusinessException、ValidationException、InternalException、PlatformException；data/logicflow内核保留自己的异常，对外在边界映射。
- 禁止 @SuppressWarnings("unchecked")；动态配置走 ouroboros-configuration，系统级配置走 Spring；数据操作走 ouroboros-data QueryDSL，已登记的底层JDBC例外按编码规则。
- 新功能包含归属明确的行为单元测试，覆盖率门槛70%；禁止纯覆盖率补数、重复无效测试和无场景归属的一次性测试。

## 按任务读取

| 主题 | 入口与读取时机 |
| --- | --- |
| 项目概况、布局、技术栈 | [README](README.md)、[技术栈](README.md#技术栈)；首次进入或调整依赖/环境前 |
| 当前功能 | [功能地图](docs/features/README.md)；定位实际能力与代码 |
| 编码 | [coding](docs/rules/coding.md)；实现或修改代码前 |
| 测试 | [testing](docs/rules/testing.md)；建设、选择和执行验证前 |
| 版本管理 | [version-control](docs/rules/version-control.md)；创建分支、提交、合并和发版前 |
| CI/CD | [ci-cd](docs/rules/ci-cd.md)；安排交付或改流水线前 |
| 并行协作 | [parallel-work](docs/rules/parallel-work.md)；并行任务、交接和资源回收前 |
| 文档治理 | [documentation](docs/rules/documentation.md)；新增/移动/更新知识前 |
| 需求与验收 | [requirements](docs/requirements/README.md)；确认行为、范围和标准 |
| 架构与设计 | [ARCHITECTURE](ARCHITECTURE.md)、[design](docs/design/README.md)；跨模块或机制修改前 |
| 领域定义 | [GLOSSARY](GLOSSARY.md)；确定命名与理解相近概念 |
| 接口契约 | [api](docs/api/README.md)；修改HTTP、源类型或消费者前 |
| 操作与接手 | [开发指南](docs/development-guide.md)、[运维手册](docs/runbooks/README.md)、[反馈循环](docs/runbooks/agent-feedback-loop.md)、[会话恢复](docs/runbooks/agent-session-recovery.md) |
| 风险/债务 | [risk-tracker](docs/risk-tracker.md)、[tech-debt-tracker](docs/tech-debt-tracker.md)；判断例外、未决事项和整改 |
| 前端差异 | [frontends/web/AGENTS](frontends/web/AGENTS.md)、[TESTING](frontends/web/TESTING.md)；进入前端子树时 |
| 未知主题 | [docs/README](docs/README.md) |

## 反馈工具

日志、测试、关键旅程、健康、指标、恢复分别使用 scripts/agent-feedback 下的 query-logs.sh、run-tests.sh、run-key-journey-e2e.sh、check-health.sh、query-metrics.sh、resume-session.sh；先读取对应 --help 或操作正文，确认参数、环境和产物位置。
