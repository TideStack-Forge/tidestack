# ouroboros-logicflow

`ouroboros-logicflow` 是平台的节点式逻辑编排与执行模块。它以 `ouroboros-logicflow-core` 为语义权威，向外延伸到 runtime、web-adapter、dev、editor 与跨栈测试资产。

## 文档入口

- [`DESIGN.md`](./DESIGN.md)
  - 模块当前架构快照；先讲设计理念与原则，再讲架构、机制、扩展点和限制。
- [`design/reading-paths-and-module-boundaries/overview.md`](./design/reading-paths-and-module-boundaries/overview.md)
  - 不同角色的阅读路径、模块边界和联动检查面。

## 目录约定

- `README.md`
  - 模块级入口，说明应该从哪里开始看。
- `DESIGN.md`
  - 模块当前设计快照，保持自顶向下和面向当前状态。
- `design/<topic>/overview.md`
  - 活跃专题设计文档；每个专题目录只有一个 canonical 入口。
- 历史评审与单次证据归关联任务或 Git 固定提交，不承担当前设计入口。
- 历史 `DesignOverview.md`
  - 入口已经退役；当前设计入口以 `DESIGN.md` 和 `design/<topic>/overview.md` 为准。

## 推荐阅读

如果你是第一次接触这个模块，建议顺序是：

1. [`DESIGN.md`](./DESIGN.md)
2. [`design/reading-paths-and-module-boundaries/overview.md`](./design/reading-paths-and-module-boundaries/overview.md)
3. [`design/core-abstractions-and-patterns/overview.md`](./design/core-abstractions-and-patterns/overview.md)
4. [`design/model-assembly-pipeline/overview.md`](./design/model-assembly-pipeline/overview.md)
5. [`design/runtime-execution/overview.md`](./design/runtime-execution/overview.md)

如果你要做 authoring、editor 或跨栈联调，继续看：

- [`design/authoring-contracts/overview.md`](./design/authoring-contracts/overview.md)
- [`design/node-defines-and-editor-capabilities/overview.md`](./design/node-defines-and-editor-capabilities/overview.md)
- [`design/editor-embedding-and-lifecycle-boundaries/overview.md`](./design/editor-embedding-and-lifecycle-boundaries/overview.md)
- [`design/testing-and-verification/overview.md`](./design/testing-and-verification/overview.md)
- [`design/online-debugging/overview.md`](./design/online-debugging/overview.md)：在线调试设计与当前实现，已完成本地发布候选验证、尚未生产部署；涵盖容器选择、采集控制、可替换仓储与默认缓存实现、执行回放及稳定性边界。

## 运行时管理

[模型生命周期](design/model-lifecycle/overview.md) 说明独立刷新与完成态发布合同。[运行时管理手册](../../docs/runbooks/logicflow-runtime-manage.md) 说明持久覆盖、权限、撤销和实例核对。`ouroboros-logicflow-editor-assets` 唯一持有版本化编辑器资产，供 dev 与管理模块共同装配。

## 验证入口

- `core`
  - `mvn test --file core/ouroboros-logicflow/ouroboros-logicflow-core/pom.xml`
- `dev`
  - `mvn test -pl :ouroboros-logicflow-dev -am`
- `editor unit`
  - `cd frontends/web/ouroboros-logicflow-editor && npm run test:unit`
- `editor integration`
  - `cd frontends/web/ouroboros-logicflow-editor && npm run test:integration`
- `cross-stack diagnostics scenario`
  - `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action up --mode run`
