# LogicFlow 术语表与关键决策词汇表（Glossary / Decision Lexicon）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 场景走查：[`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md)
>
> 源码地图：[`DESIGN-CONCEPT-TO-SOURCE-CROSSWALK.md`](../concept-to-source-crosswalk/overview.md)

这份文档只做一件事：

- 给 LogicFlow 当前文档体系里最容易漂移的高频术语，建立一套**简短但权威的统一口径**。

如果前面的文档分别回答的是：

- 设计是什么
- 架构怎么分层
- 场景怎么走
- 代码和测试入口在哪里

那么这份术语表回答的是：

- **同一个词到底该怎么理解，哪些理解是错误的，哪些地方以后不该再写飘。**

## 1. 为什么需要单独做这一层

LogicFlow 现在的材料已经不少，真正的风险不再只是“缺内容”，而是“同一个概念被不同文档从不同角度描述后，慢慢漂移成几套口径”。

最典型的漂移风险包括：

- 把 `ReturnNode` 说成“提前退出”
- 把 `validationState` 说成“运行时开关”
- 把 `NodeDefine` 说成“节点运行时真相”
- 把 `MergeNode` 当成 join
- 把 `exclusiveGroupId` 当成普通节点都能随便获得 XOR 语义的开关

所以这份文档的使用原则很简单：

- **以后如果某个高频词在新文档里要被反复引用，先回看这里。**

## 2. 核心术语表

| 术语 | 统一定义 | 不要混淆成什么 | 主文档入口 | 代码落点 |
| --- | --- | --- | --- | --- |
| `FlowModel` | 运行时公开流程合同，对外提供 `execute(Object...): Try<Object>` 等能力 | 不是持久化元数据对象 | [`DESIGN.md`](../../DESIGN.md), [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md) | [`FlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModel.java) |
| `FlowModelMeta` | 持久化流程元数据，描述节点、连线、入口出口、类型、validation snapshot 等 | 不是可执行流程实例 | [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md) | [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java) |
| `DefaultFlowModel` | `FlowModelMeta -> 可执行模型` 的装配与校验管线实现 | 不是单纯 DTO 包装器 | [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md), [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) | [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java) |
| `entryNode` | main scope 的显式入口节点 | 不是“所有无上游节点的统称” | [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) | [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java) |
| `exitNode` | 兼容存量模型的显式成功终点字段；当前仍属于成功终点集合的一部分 | 不是唯一成功终点，也不是未来唯一推荐终点表达 | [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java), [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) |
| `ReturnNode` | 内置成功终点节点；把首个输入值作为成功结果收口 | 不是 terminate/cancel 语义，也不是异常恢复专用节点 | [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md), [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) | [`ReturnNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java) |
| successful terminal | 能把流程收口为成功结果的业务角色；静态候选还包含终点家族，候选不等于执行成功 | 不等于“所有 terminal node” | [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md), [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java) |
| `errorEntryNode` | 同图 error scope 的显式入口；用于接住 main scope 的 runtime failure | 不是 invalid model 的补救入口 | [`DESIGN-ERROR-HANDLING.md`](../error-handling/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) | [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java) |
| main scope / error scope | 同一张图中两条独立装配、独立执行、独立成功终点约束的 scope | 不是同一条流程里随便插一个异常分支 | [`DESIGN-ERROR-HANDLING.md`](../error-handling/overview.md), [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md) | [`LogicFlowUtil.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java) |
| `FlowOutcome` | 编译器内部成功结果信封，用来区分 `EXIT` 与 `RETURN` | 不是公开 API，不应被外层直接消费 | [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md) | [`FlowOutcome.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/FlowOutcome.java) |
| `validationState` | 保存时由后端生成的诊断快照状态，用于列表页/编辑页/建模态展示 | 不是 runtime gate，不自动改写 `isEnabled` | [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`LogicFlowValidationSnapshotSupport.java`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java) |
| `isEnabled` | 选模与覆盖关系的 gate；决定模型是否进入“可被选中”的集合 | 不是可执行性证明 | [`DESIGN-MODEL-LIFECYCLE.md`](../model-lifecycle/overview.md), [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java), [`FlowModelCenter.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelCenter.java) |
| `isValid` | runtime `FlowModel` 装配后的执行态事实，表示当前实例有没有 design-time 错误 | 不等于 `validationState`，也不等于 `isEnabled` | [`DESIGN-MODEL-STATE-AND-RUNTIME-GATING.md`](../model-state-and-runtime-gating/overview.md) | [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java) |
| `NodeDefine` | dev 向 editor 暴露的 authoring contract，描述节点如何被建模、展示、限制和脚手架化 | 不是 runtime 节点真相，也不是语义全集 | [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md) | [`NodeDefine.java`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java) |
| `terminalKind` | authoring / display 层的终点分类字段，当前主要有 `successful` / `exception` / `generic` | 不是单独决定 runtime 成功语义的执行字段 | [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md) | [`NodeDefine.java`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java), [`displayState.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node-define/displayState.mjs) |
| `exclusiveGroupId` | 输出端口级的互斥分组字段 | 不是普通节点的万能 XOR 开关 | [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) | [`FlowNodePortMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java) |
| `defaultBranch` | 同一 `exclusiveGroupId` 中的默认输出标记 | 不是普通“次级输出口”标签；也不能脱离 `exclusiveGroupId` 单独成立 | [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) | [`FlowNodePortMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java) |
| selective-output | 一类受节点能力门控的互斥输出语义 | 不是“任何多输出节点都天然有”的语义 | [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md), [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) | [`AbstractDecisionNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java), [`FunctionalFlowCompiler.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java) |
| `SwitchNode` | 当前唯一稳定成型的内置 XOR 选择节点 | 不是“所有分支节点”的泛称 | [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`SwitchNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java) |
| `MergeNode` | payload merge 节点，用于 map 数据合并 | 不是 control-flow join | [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`MergeNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/MergeNode.java) |
| `MutexNode` | 当前更接近 legacy placeholder 的兼容节点 | 不是成熟 control-flow primitive，不应用它建立新设计前提 | [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) | [`MutexNode.java`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/MutexNode.java) |

## 3. 关键决策词汇表

下面这些不是单纯名词，而是当前分支已经明确收敛过的“短句级决策”。以后写文档、写注释、做评审时，优先复用这些说法。

1. **`exitNode` 是兼容字段，不是唯一成功终点来源。**
2. **`ReturnNode` 是成功终点，不是 terminate/cancel。**
3. **successful terminal 是语义集合，不是某一个字段或某一种节点类名。**
4. **`errorEntryNode` 处理 runtime failure，不处理 invalid model。**
5. **`FlowOutcome` 是内部信封，不是公开执行合同。**
6. **公开执行合同仍然是 `FlowModel.execute(): Try<Object>`。**
7. **`validationState` 是后端诊断快照，不是 runtime gate。**
8. **`isEnabled` 决定覆盖关系，不保证可执行。**
9. **`isValid` 是执行态事实，不是持久化状态字段。**
10. **`NodeDefine` 是 authoring contract，不是 runtime truth。**
11. **`terminalKind` 解决展示/建模分类，不单独定义执行语义。**
12. **`exclusiveGroupId + defaultBranch` 是最小互斥输出契约，不是通用约束系统。**
13. **selective-output 的语义前提是节点显式 opt-in。**
14. **`SwitchNode` 是当前稳定的 built-in XOR 分支节点。**
15. **`MergeNode` 只做数据合并，不做控制流汇合。**
16. **`MutexNode` 仍是 legacy placeholder，不是可依赖的成熟控制流原语。**
17. **editor 可以 warning，但不能创造 core 不支持的语义。**
18. **前端保存策略宽松，不等于后端会放行执行。**

## 4. 常见误写与推荐改写

| 容易写偏的说法 | 推荐改写 |
| --- | --- |
| “`ReturnNode` 会提前退出流程” | “`ReturnNode` 会把流程收口为成功结果；真正的 cancel/terminate 尚未实现。” |
| “模型 `validationState=INVALID`，所以 runtime 不会选它” | “`validationState=INVALID` 只代表后端诊断态；runtime 选模仍先看 `isEnabled`。” |
| “`NodeDefine` 就是节点定义本身” | “`NodeDefine` 是节点的 authoring / display contract，而不是 runtime 语义全集。” |
| “给输出端口加 `exclusiveGroupId` 就能获得 XOR” | “只有支持 selective-output 的节点，配合 `exclusiveGroupId` 才形成互斥语义。” |
| “`MergeNode` 把分支汇合” | “`MergeNode` 合并的是 payload，不是 control-flow join。” |
| “editor 校验通过，所以后端模型有效” | “editor 主要给 warning；模型有效性仍以后端构造与校验为准。” |

## 5. 这份术语表应该怎么维护

后续如果 LogicFlow 再演进，我建议用这套顺序维持术语稳定：

1. 先在专题设计文档里把新能力讲清楚。
2. 如果引入了新的高频词，或改变了旧词的正式含义，优先更新这份术语表。
3. 再更新场景走查和源码地图。
4. 最后再考虑是否需要把旧词降级为兼容术语或历史术语。

这能尽量避免出现一种常见坏味道：

- 实现已经改了
- 场景文档已经跟上了
- 但总览和旧文档里大家还在用上一轮的词汇体系

## 6. 什么时候优先看这份文档，什么时候不用

优先看这份文档的情况：

- 你发现两个文档看起来都对，但同一个词像是指了不同东西。
- 你在 review 里怀疑有人把概念说混了。
- 你准备给新文档或代码注释定名。

不必优先看这份文档的情况：

- 你已经非常明确要追某个具体类或某条具体执行链。

那时更直接的入口通常是：

- [`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md)
- [`DESIGN-CONCEPT-TO-SOURCE-CROSSWALK.md`](../concept-to-source-crosswalk/overview.md)
