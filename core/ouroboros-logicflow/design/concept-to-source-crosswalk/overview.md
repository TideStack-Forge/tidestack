# LogicFlow 概念到源码映射（Concept-to-Source Crosswalk）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 场景走查：[`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md)
>
> 扁平索引：[`docs/logicflow/references.md`](../../../../docs/logicflow/references.md)

这份文档的目标不是再解释一遍设计，而是提供一张“**概念 -> 文档 -> 源码 -> 测试 -> fixture -> 外层消费面**”的速查地图。

如果说：

- [`DESIGN.md`](../../DESIGN.md) 是总览入口
- [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) 是主干样例走查
- [`docs/logicflow/references.md`](../../../../docs/logicflow/references.md) 是扁平参考索引

那么这份文档就是：

- **把“我脑子里有一个概念/问题”直接落到“我现在该看哪几个文件、哪几个测试、哪几个 fixture”上的那座桥**

## 1. 怎么用这张图，而不是把它再读成一篇综述

这份 crosswalk 适合三种典型起点：

1. **我知道概念，但忘了源码入口**
   - 例如：“成功终点互斥到底在哪个类里收口？”
2. **我遇到了症状，但还没判断是哪个层的问题**
   - 例如：“编辑器显示能保存，但后端为什么判 invalid？”
3. **我准备改一个点，想先找回归保护**
   - 例如：“如果我改 `ReturnNode` 结果合同，哪些测试和 fixture 应该先看？”

阅读顺序建议固定成这样：

1. 先从下面的概念表里找到最接近的问题域的一行。
2. 先打开“主设计文档”列，不要一上来就读实现。
3. 再看“权威源码”列，确认真正的语义落点。
4. 最后看“回归测试 / fixture”列，确认当前基线已经锁住了什么。

## 2. 语义内核：先定位 core 里真正“说了算”的部分

| 概念 / 问题 | 主设计文档 | 权威源码 | 关键回归测试 | 关键 fixture / 样本 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 公开执行合同与模型装配入口 | [`DESIGN.md`](../../DESIGN.md), [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md) | [`FlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModel.java), [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java), [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java) | [`FlowModelExecutionSmokeTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionSmokeTest.java) | [`result/linear-exit-result.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/result/linear-exit-result.json) | 如果你要回答“这张图何时变成可执行模型”，从这里开始。 |
| authoring 端口声明、端口补齐与 runtime 端口物化 | [`DESIGN-AUTHORING-PORTS-AND-RUNTIME-PORT-MATERIALIZATION-BOUNDARIES.md`](../authoring-ports-and-runtime-port-materialization-boundaries/overview.md), [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md), [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md) | [`AbstractNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java), [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java), [`FlowNodeMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodeMeta.java), [`NodeDefine.java`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java) | [`FlowModelConnectionValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelConnectionValidationTest.java) | [`validation/invalid/invalid-connection-target-port-out-of-range.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/invalid-connection-target-port-out-of-range.json), [`validation/invalid/invalid-connection-should-not-cascade-input-conflict.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/invalid-connection-should-not-cascade-input-conflict.json) | 这一行回答“authoring 端看见的端口、持久化里声明的端口、runtime 真正执行的端口为什么可能不是同一份列表”。 |
| 最小类型系统、流程输入输出合同与类型兼容 | [`DESIGN-TYPE-SYSTEM-AND-COMPATIBILITY-BOUNDARIES.md`](../type-system-and-compatibility-boundaries/overview.md), [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md), [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md) | [`DataTypeEnum.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DataTypeEnum.java), [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java), [`FlowNodePortMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) | [`FlowModelTypeCompatibilityValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelTypeCompatibilityValidationTest.java), [`FlowModelConnectionValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelConnectionValidationTest.java) | [`validation/invalid/type-mismatch-basic.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/type-mismatch-basic.json), [`validation/invalid/return-only-type-mismatch.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/return-only-type-mismatch.json), [`validation/invalid/error-entry-type-mismatch.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/error-entry-type-mismatch.json), [`validation/valid/disabled-exit-type-mismatch-ignored.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/valid/disabled-exit-type-mismatch-ignored.json) | 这一行回答“当前类型系统到底多强、哪些兼容检查已经落地、以及为什么未知类型字符串并不会自动变成严格错误”。 |
| `Decision` 信号、路径激活与 skip 边界 | [`DESIGN-DECISION-SIGNALS-AND-PATH-ACTIVATION-BOUNDARIES.md`](../decision-signals-and-path-activation-boundaries/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-INPUT-SEMANTICS-AND-JOIN-BOUNDARIES.md`](../input-semantics-and-join-boundaries/overview.md), [`DESIGN-SELECTIVE-OUTPUT-AND-EXCLUSIVE-PORT-BOUNDARIES.md`](../selective-output-and-exclusive-port-boundaries/overview.md) | [`Decision.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/Decision.java), [`AbstractDecisionNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java), [`SwitchNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java) | [`FlowModelExecutionBranchSkippingTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionBranchSkippingTest.java), [`FlowModelSelectiveOutputRuntimeContractTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelSelectiveOutputRuntimeContractTest.java) | [`execution/branch/switch-default-skips-unselected-branch.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/branch/switch-default-skips-unselected-branch.json), [`execution/runtime/broken-selective-node-violates-exclusive-group.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/runtime/broken-selective-node-violates-exclusive-group.json) | 这一行回答“当前路径为什么靠 `Decision` 激活、skip 到底意味着什么、以及为什么这还不是 OR-Join 或提前退出”。 |
| 成功终点集合、`exitNode` vs `ReturnNode`、提前退出边界 | [`DESIGN-SUCCESSFUL-TERMINALS-AND-EARLY-EXIT-BOUNDARIES.md`](../successful-terminals-and-early-exit-boundaries/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md), [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) | [`ReturnNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java), [`FlowOutcome.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/FlowOutcome.java) | [`FlowModelReturnExecutionTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelReturnExecutionTest.java), [`FlowModelExecutionResultSelectionTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionResultSelectionTest.java), [`FunctionalFlowCompilerOutcomeGuardTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompilerOutcomeGuardTest.java) | [`result/return-terminal-result.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/result/return-terminal-result.json), [`validation/valid/return-only-linear.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/valid/return-only-linear.json) | 如果你要改结果合同、成功终点集合或“是否已经支持提前退出”这类边界口径，这一行是第一站。 |
| 输入语义、同口多来源与 Join 边界 | [`DESIGN-INPUT-SEMANTICS-AND-JOIN-BOUNDARIES.md`](../input-semantics-and-join-boundaries/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md), [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java), [`MergeNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/MergeNode.java) | [`FlowModelExecutionInputContractTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionInputContractTest.java), [`FlowModelInputConnectivityValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelInputConnectivityValidationTest.java), [`FlowModelInputConflictValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelInputConflictValidationTest.java), [`FlowModelRacingConditionValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelRacingConditionValidationTest.java) | [`execution/input/linear-input-count-required.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/input/linear-input-count-required.json), [`validation/invalid/unconnected-input.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/unconnected-input.json), [`validation/valid/input-conflict-metadata-exclusive.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/valid/input-conflict-metadata-exclusive.json), [`validation/invalid/input-conflict-non-mutex.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/input-conflict-non-mutex.json) | 如果问题是“输入为什么没被喂到节点 / 为什么 reconvergence 不合法 / 为什么 `MergeNode` 不是 Join”，优先看这里。 |
| 成功终点的可达性与互斥证明 | [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md), [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) | [`FlowModelGraphReachabilityValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelGraphReachabilityValidationTest.java) | [`validation/valid/exclusive-return-vs-exit.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/valid/exclusive-return-vs-exit.json), [`validation/invalid/unreachable-return-terminal.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/unreachable-return-terminal.json), [`validation/invalid/unreachable-exit-terminal.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/unreachable-exit-terminal.json), [`validation/invalid/dual-success-ambiguous.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/dual-success-ambiguous.json) | 如果问题是“为什么这个图看起来有终点却仍然 invalid”，优先看这里。 |
| selective-output、`SwitchNode`、互斥输出端口 | [`DESIGN-SELECTIVE-OUTPUT-AND-EXCLUSIVE-PORT-BOUNDARIES.md`](../selective-output-and-exclusive-port-boundaries/overview.md), [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) | [`AbstractDecisionNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java), [`SwitchNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java), [`FlowNodePortMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) | [`FlowModelExecutionBranchSkippingTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionBranchSkippingTest.java), [`FlowModelSelectiveOutputRuntimeContractTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelSelectiveOutputRuntimeContractTest.java), [`FlowModelOutputPortMetadataValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelOutputPortMetadataValidationTest.java) | [`branch/switch-default-skips-unselected-branch.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/branch/switch-default-skips-unselected-branch.json), [`validation/valid/output-metadata-exclusive-dual-success.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/valid/output-metadata-exclusive-dual-success.json), [`execution/runtime/broken-selective-node-violates-exclusive-group.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/runtime/broken-selective-node-violates-exclusive-group.json) | 这一行回答“为什么有些多输出是 XOR，有些只是普通多输出，以及这层语义为什么必须由节点 capability 显式 opt-in 才成立”。 |
| 错误流、`errorEntryNode`、恢复成功与 fail-closed | [`DESIGN-ERROR-FLOW-RECOVERY-AND-FAIL-CLOSED-BOUNDARIES.md`](../error-flow-recovery-and-fail-closed-boundaries/overview.md), [`DESIGN-ERROR-HANDLING.md`](../error-handling/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) | [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) | [`FlowModelErrorFlowExecutionTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelErrorFlowExecutionTest.java), [`FlowModelErrorScopeValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelErrorScopeValidationTest.java), [`FlowModelTypeCompatibilityValidationTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelTypeCompatibilityValidationTest.java) | [`execution/error-flow/main-failure-recovers.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/error-flow/main-failure-recovers.json), [`execution/error-flow/main-failure-no-recovery-terminal.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/error-flow/main-failure-no-recovery-terminal.json), [`validation/invalid/error-entry-main-scope-missing-success-terminal.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/validation/invalid/error-entry-main-scope-missing-success-terminal.json) | 如果问题是“error flow 为什么没接住 / 为什么不该接住”，优先看这里。 |

## 3. 开发态与运行时集成：别把 state、selection、diagnostic 混成一层

| 概念 / 问题 | 主设计文档 | 权威源码 | 关键回归测试 | 关键 fixture / 样本 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `validationState`、validation snapshot、可导航 diagnostics | [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md), [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`LogicFlowValidationSnapshotSupport.java`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java), [`FlowModelValidationDiagnostic.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelValidationDiagnostic.java) | [`LogicFlowValidationSnapshotSupportTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupportTest.java) | [`reviews/2026-03-18-report-current-baseline.md`](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-18-report-current-baseline.md), [`testing/integration/feature/lowcode/logicflow-validation-diagnostics/contract.md`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/contract.md) | 如果问题是“后端为什么说 invalid、前端该怎么导航到节点”，从这里开始。 |
| enabled / invalid 模型的覆盖策略与选模行为 | [`DESIGN-MODEL-LIFECYCLE.md`](../model-lifecycle/overview.md), [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`LogicModelDevelopServiceImpl.java`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImpl.java), [`DefaultLogicFlowProvider.java`](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/DefaultLogicFlowProvider.java), [`FlowModelCenter.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelCenter.java) | [`LogicModelDevelopServiceImplValidationTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImplValidationTest.java), [`FlowModelCenterPrioritySelectionTest.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelCenterPrioritySelectionTest.java) | [`reviews/2026-03-18-report-current-baseline.md`](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-18-report-current-baseline.md) | 这一行回答“为什么 invalid 但 enabled 的高优先级模型仍会覆盖低优先级模型”。 |
| Web adapter 的执行边界、事务、错误映射 | [`DESIGN-ADAPTER-AND-TRANSACTION-BOUNDARIES.md`](../adapter-and-transaction-boundaries/overview.md), [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md) | [`HandlerFunctionAdapter.java`](../../ouroboros-logicflow-web-adapter/src/main/java/com/ouroboros/logicflow/adapter/HandlerFunctionAdapter.java) | adapter 的映射边界由 [`HandlerFunctionAdapterTest.java`](../../ouroboros-logicflow-web-adapter/src/test/java/com/ouroboros/logicflow/adapter/HandlerFunctionAdapterTest.java) 覆盖；选模/gate 位于 runtime 的 `DefaultLogicFlowExecutionFacade`，并有模块测试。修改分析语义时还需回看 [`FlowModelRuntimeAnalyzerCompatibilityTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelRuntimeAnalyzerCompatibilityTest.java) | [`testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml) | 如果问题是“为什么同一个 invalid model 在编辑页、运行时、接口调用里表现不完全一样”，这里是关键接缝。 |
| runtime analyzer 与最终结果可观测面 | [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md) | [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java), [`FlowRuntimeAnalyzer.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/diagnostic/FlowRuntimeAnalyzer.java) | [`FlowModelRuntimeAnalyzerCompatibilityTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelRuntimeAnalyzerCompatibilityTest.java) | [`execution/result/return-terminal-result.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/result/return-terminal-result.json), [`execution/error-flow/main-failure-recovers.json`](../../ouroboros-logicflow-core/src/test/resources/fixtures/execution/error-flow/main-failure-recovers.json) | 如果你要回答“分析器能看到什么、故意看不到什么”，看这一行。 |

## 4. NodeDefine 与编辑器：作者态合同到底落在哪

| 概念 / 问题 | 主设计文档 | 权威源码 | 关键回归测试 | 关键 fixture / 样本 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `NodeDefine`、`terminalKind`、`editorCapabilities` | [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md), [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md) | [`NodeDefine.java`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java), [`LogicflowNodeDefineController.java`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/controller/LogicflowNodeDefineController.java) | [`LogicflowNodeDefineControllerReturnNodeTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/controller/LogicflowNodeDefineControllerReturnNodeTest.java) | [`frontends/web/ouroboros-logicflow-editor/tests/nodeDefineFetch.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/nodeDefineFetch.test.mjs) | 如果问题是“这个节点在编辑器里为什么被识别成成功终点/异常终点”，优先看这里。 |
| editor 作为嵌入式部件的生命周期与宿主桥接面 | [`DESIGN-EDITOR-EMBEDDING-AND-LIFECYCLE-BOUNDARIES.md`](../editor-embedding-and-lifecycle-boundaries/overview.md), [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md) | [`index.jsx`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/index.jsx), [`useLogicFlowEditorState.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/state/useLogicFlowEditorState.js), [`logicFlowCanvasController.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/canvas/logicFlowCanvasController.mjs), [`canvas/index.jsx`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/components/canvas/index.jsx), [`propertiesPanel/index.jsx`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/components/propertiesPanel/index.jsx) | [`canvasLifecycle.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/canvasLifecycle.test.mjs), [`propertyPanelLifecycle.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/propertyPanelLifecycle.test.mjs), [`canvasController.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/canvasController.test.mjs), [`editorOrchestrationBridge.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/editorOrchestrationBridge.test.mjs) | [`tests/integration/logicflow/harness/createLogicflowEditorHarness.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/integration/logicflow/harness/createLogicflowEditorHarness.mjs) | 如果问题是“宿主到底能依赖 editor 暴露什么、哪些 runtime 资源由谁销毁”，优先看这里。 |
| 终点视觉与三态展示（successful / exception / generic） | [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md) | [`displayState.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node-define/displayState.mjs) | [`nodeDefineDisplayState.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/nodeDefineDisplayState.test.mjs), [`x6TerminalKindRendering.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/x6TerminalKindRendering.test.mjs) | editor 侧 mock node defines 与 `ReturnNode`/异常终点 contract | 这一行是“为什么 badge 画成 RETURN / ERROR / TERMINAL”的直接落点。 |
| selective-output 在 editor 的消费与兼容归一 | [`DESIGN-SELECTIVE-OUTPUT-AND-EXCLUSIVE-PORT-BOUNDARIES.md`](../selective-output-and-exclusive-port-boundaries/overview.md), [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md), [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md) | [`outputPortSemantics.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortSemantics.mjs), [`outputPortNormalization.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortNormalization.mjs), [`validate.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/validate.js) | [`outputPortSemantics.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/outputPortSemantics.test.mjs), [`outputPortNormalization.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/outputPortNormalization.test.mjs) | `switch` 与 built-in selective nodes 的 editor mock contract | 如果问题是“为什么 editor 能认这个互斥端口、为什么它只是兼容归一而不是语义源头、以及什么时候应该把判断交回 core”，看这里。 |
| 无效连线 transpile warning 与画布同步 | [`DESIGN-TESTING-AND-VERIFICATION.md`](../testing-and-verification/overview.md), [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md) | [`LogicFlowPlugin.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/LogicFlowPlugin.js), [`canvas/index.jsx`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/components/canvas/index.jsx) | [`transpileWarnings.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/transpileWarnings.test.mjs), [`editorTranspileWarningsBridge.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/editorTranspileWarningsBridge.test.mjs) | integration harness 中的 mock platform / mock node defines | 这条线是“前端 warning 不阻塞保存，但要低噪声提示”的直接实现入口。 |
| 编辑器保存阻塞策略 | [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md), [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`saveBlockingValidation.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/editorValidation/saveBlockingValidation.mjs), [`registerComponent.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/registerComponent.js) | [`saveBlockingValidation.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/saveBlockingValidation.test.mjs) | 当前规则本身就是最小实现：只有“模型名为空”会阻塞保存 | 如果症状是“为什么前端不拦这个校验错误”，这一行能最快定位到策略落点。 |
| validation diagnostics 到节点导航 | [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md), [`DESIGN-TESTING-AND-VERIFICATION.md`](../testing-and-verification/overview.md) | [`validationDiagnosticNavigation.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/orchestration/validationDiagnosticNavigation.mjs), [`editorSelectionBridge.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/orchestration/editorSelectionBridge.mjs) | [`validationDiagnosticNavigation.test.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/tests/validationDiagnosticNavigation.test.mjs) | `validationState = INVALID` 的 dev 页面 contract 与 editor selection bridge | 如果问题是“点击后端诊断为什么能/不能定位到节点”，从这里查。 |

## 5. 测试与夹具：验证资产到底该去哪找

| 需求 / 问题 | 先看哪层 | 入口文件 | 说明 |
| --- | --- | --- | --- |
| core 图语义回归 | core unit / fixture | [`FlowModelAssertions.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/support/FlowModelAssertions.java), [`FlowModelFixtures.java`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/support/FlowModelFixtures.java) | 大多数核心设计规则的第一证据都在这里。 |
| dev 校验快照与保存语义 | dev unit | [`LogicFlowValidationSnapshotSupportTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupportTest.java), [`LogicModelDevelopServiceImplValidationTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImplValidationTest.java) | 如果改了 diagnostics / validationState / save 行为，优先补这一层。 |
| editor 纯逻辑与契约消费 | editor unit | [`tests/`](../../../../frontends/web/ouroboros-logicflow-editor/tests/) | 这里覆盖 node define、transpile warning、保存阻塞、terminal display 等前端消费面。 |
| editor 精简集成链路 | editor integration | [`tests/integration/logicflow/`](../../../../frontends/web/ouroboros-logicflow-editor/tests/integration/logicflow/) | 这里守的是“前端真的能在薄平台里消费这些 contract”，不是替代 core 语义测试。 |
| 跨栈 validation diagnostics 场景 | cross-stack harness | [`scenario.yaml`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml), [`contract.md`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/contract.md), [`harness.playbook.md`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/harness.playbook.md) | 如果要证明从后端诊断到前端展示再到导航链路整体没断，最终看这一层。 |
| 共享夹具供应 | testing/common fixtures | [`fixture.yaml`](../../../../testing/common/fixtures/frontends/logicflow-editor-static/fixture.yaml), [`fixture.yaml`](../../../../testing/common/fixtures/data/logicflow-validation-diagnostics/fixture.yaml) | 这两份 fixture 是跨栈场景的能力供应，不是场景私货。 |

## 6. 任务导向速查：准备改代码时先看哪几组文件

### 6.1 我要改“合法图”的判断规则

先看：

- [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
- [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)
- [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)
- 对应 core fixture + validation test

不要先改：

- editor 校验
- dev validation snapshot 映射

因为这些层都应该消费 core 结论，而不是反向定义 core。

### 6.2 我要改终点语义或结果合同

先看：

- [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
- [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md)
- [`ReturnNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java)
- [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
- [`FlowModelReturnExecutionTest.java`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelReturnExecutionTest.java)

再看：

- dev 的 `NodeDefine` contract
- editor 的 terminal display

### 6.3 我要改 editor 的终点显示或保存策略

先看：

- [`NodeDefine.java`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java)
- [`displayState.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node-define/displayState.mjs)
- [`saveBlockingValidation.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/editorValidation/saveBlockingValidation.mjs)
- [`LogicflowNodeDefineControllerReturnNodeTest.java`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/controller/LogicflowNodeDefineControllerReturnNodeTest.java)
- 对应 editor unit / integration tests

不要先改：

- core 节点实现

除非你已经确认是 authoring contract 本身缺失，而不是前端消费错了。

### 6.4 我要查“后端 invalid，但前端还能保存”是不是 bug

先看：

- [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md)
- [`LogicFlowValidationSnapshotSupport.java`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java)
- [`saveBlockingValidation.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/editorValidation/saveBlockingValidation.mjs)
- dev 页面相关 UI schema

当前基线的答案通常是：

- 这往往不是 bug，而是设计选择
- 前端除“模型名称为空”外主要给 warning，不阻塞保存
- runtime / adapter 再根据公开执行合同 fail-closed

## 7. 这份 crosswalk 和 `references.md` 的边界

这两份文档应该长期共存，但职责不同：

- [`docs/logicflow/references.md`](../../../../docs/logicflow/references.md)
  - 偏“扁平稳定索引”
  - 适合你已经知道文件名、只想快速点开
- **这份 crosswalk**
  - 偏“概念导航”
  - 适合你先有一个问题，再反推源码和证据链

如果未来 LogicFlow 继续扩展，新增高价值内容时更推荐的做法是：

1. 先补对应专题设计文档或 canonical scenario。
2. 再回到这份 crosswalk，把新概念挂到已有分组里。
3. 只有在它已经成为稳定入口文件时，才补进 `references.md`。
