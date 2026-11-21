# LogicFlow Decision 信号与路径激活边界（Decision / Path Activation Boundaries）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 运行时执行总论：[`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
>
> 输入语义与 Join 边界：[`DESIGN-INPUT-SEMANTICS-AND-JOIN-BOUNDARIES.md`](../input-semantics-and-join-boundaries/overview.md)
>
> selective-output 边界：[`DESIGN-SELECTIVE-OUTPUT-AND-EXCLUSIVE-PORT-BOUNDARIES.md`](../selective-output-and-exclusive-port-boundaries/overview.md)
>
> 结果契约与可见面：[`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md)

这份文档单独拆出来，是为了把当前实现里一条很关键、但也最容易被误读的 runtime 语义收口成单一口径：

- `Decision`
- 路径激活
- branch skip
- same-port reconvergence 之后的“择一有效值”

如果只零散地看 `SwitchNode`、`FunctionalFlowCompiler`、输入语义文档和 selective-output 文档，常见误读会反复出现：

- 把 `Decision` 误当成一种业务数据类型
- 把 `Decision.unsatisfied()` 误读成通用 OR-Join
- 把 skip 误读成真正提前退出 / cancel
- 把 decision-family 节点的 runtime contract 误发散到所有普通多输出节点

这份文档要做的，就是把这些边界一次性钉清楚。

## 1. 先给结论：`Decision` 是 runtime 路径激活信号，不是公开数据合同

当前关于 `Decision` 最重要的事实可以先压缩成七句话：

1. `Decision` 是运行时信号对象，不是 `DataTypeEnum` 里的数据类型。
2. 它当前只表达两种状态：
   - `Decision.satisfied(value)`
   - `Decision.unsatisfied()`
3. 它的职责是表达“这条路径是否命中/激活”，不是表达“流程最终结果是什么”。
4. `Decision` 是内部路径信号，常规路径由下游输入装配消费；公开签名 `Try<Object>` 本身不保证任意显式出口值已去除 `Decision`。
5. 同一输入口多来源时，编译器会等待所有来源完成，再过滤 `unsatisfied` 并择一有效值。
6. 只要某节点任一输入参数里出现 `Decision.unsatisfied()`，该节点就会被 skip，而不是执行真实 `node.execute(...)`。
7. 这种 skip 只是“未激活路径不执行节点逻辑”，不是“终止整张图的剩余并发分支”。

如果只记一句口径，应该记这个版本：

- **`Decision` 是当前 runtime 的路径激活信号，不是端口数据类型、不是流程结果信封、也不是提前退出机制。**

## 2. 为什么这一层需要单独讲

`Decision` 现在跨了三条线：

1. **节点家族线**
   - `AbstractDecisionNode` / `SwitchNode`
2. **输入装配线**
   - 同一输入口多来源时如何等待和择值
3. **执行跳过线**
   - downstream 节点何时真正执行、何时只传播未命中信号

如果这三条线不在同一份文档里收口，就很容易出现两种设计漂移：

1. 在 authoring/editor 侧把 `Decision` 当成某种普通端口值解释
2. 在 runtime 侧把现在的 skip 过度宣传成 OR-Join、短路执行或提前终止

所以这份文档关注的不是“分支节点怎么写”，而是：

- **当前引擎怎样用最小 runtime 信号实现路径激活，以及它明确还没有做什么。**

## 3. `Decision` 当前到底是什么

### 3.1 结构非常小，而且是故意的

定义在：

- [`Decision`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/Decision.java)

当前它只有两个字段：

- `isSatisfied`
- `value`

以及两个工厂方法：

- `Decision.satisfied(value)`
- `Decision.unsatisfied()`

这说明它不是一个复杂的控制流对象，而是一个最小的 runtime wrapper。

### 3.2 它不属于 `DataTypeEnum`

这是一个经常会被混淆、必须单独强调的边界：

- `Decision` 不是 `DataTypeEnum`
- 也不是 `FlowNodePortMeta.dataType` 里应该填的字符串

也就是说：

- 端口数据合同仍然走 [`DESIGN-TYPE-SYSTEM-AND-COMPATIBILITY-BOUNDARIES.md`](../type-system-and-compatibility-boundaries/overview.md) 那条线
- `Decision` 走的是 runtime path-activation 这条线

所以当前正确理解应当是：

- **端口类型合同解决“值应该兼容什么”**
- **`Decision` 解决“这条路径是否被激活”**

### 3.3 它也不是 `FlowOutcome`

同样要避免把两种内部信号混成一个东西：

- `Decision`
  - 节点级/路径级激活信号
- `FlowOutcome`
  - 编译器内部成功结果收口信封

两者都属于内部语义对象，但职责完全不同：

- `Decision` 参与路径传播
- `FlowOutcome` 参与成功终点结果收口

## 4. `Decision` 当前是怎样被生产出来的

### 4.1 `AbstractDecisionNode` 是当前内置 decision-family 模板

定义在：

- [`AbstractDecisionNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java)

它的 `doExecute(...)` 当前做了三件非常关键的事：

1. 先为所有输出端口预填 `Decision.unsatisfied()`
2. 再通过 `setPortResult(...)` / `setNamedPortResult(...)` 把命中的输出改成 `Decision.satisfied(value)`
3. 默认声明 `supportsSelectiveOutputMetadata() == true`

这意味着当前 decision-family 节点的默认 contract 是：

- 未命中的输出也必须显式存在
- 但它们以 `Decision.unsatisfied()` 形式存在

### 4.2 `SwitchNode` 是当前 built-in decision-family 的基准节点

当前最稳定的 built-in 决策节点是：

- [`SwitchNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java)

它做的不是“运行时删边”，而是：

- 为每个输出口生产一份 `Decision`
- 再由编译器根据这些 `Decision` 决定 downstream 是否激活

同时它还会发布：

- 共享 `exclusiveGroupId`
- 默认分支标记

所以当前 `SwitchNode` 之所以成立，不是因为它“有多个输出口”，而是因为它同时满足：

- decision-family 输出 contract
- selective-output capability opt-in
- built-in 互斥输出元数据归一

## 5. `Decision` 当前怎样参与输入装配

### 5.1 单来源输入口：直接透传上游结果

当某个输入口只有一条来源时，`buildArgumentsMapper(...)` 当前会直接取：

- 上游节点结果列表中的目标输出槽位

此时如果上游给的是普通值，就直接透传普通值；
如果上游给的是 `Decision`，则由后续节点执行阶段再解释。

### 5.2 同一输入口多来源：先等完，再择一

这是当前最容易被误读成 OR-Join 的地方。

在：

- [`FunctionalFlowCompiler.buildArgumentsMapper(...)`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)

里，同一输入口多来源时的行为是：

1. 为所有来源构造 future
2. `CompletableFuture.allOf(...)` 等待全部完成
3. 按固定顺序遍历这些来源结果
4. 过滤掉 `Decision.unsatisfied()`
5. 对 `Decision.satisfied(v)` 解包成 `v`
6. 找第一个有效值
7. 如果没有有效值，则返回 `Decision.unsatisfied()`

所以当前这条线的正确表述不是：

- “哪个先回来就触发”

而是：

- **全部完成后，按固定顺序择第一个有效值**

### 5.3 为什么这不能被叫作通用 OR-Join

因为它仍然有两个硬边界：

1. 先等待全部完成
   - 不是 short-circuit
2. 必须靠静态分析证明多来源在语义上互斥
   - 否则会引入顺序依赖

这也是为什么：

- [`DESIGN-INPUT-SEMANTICS-AND-JOIN-BOUNDARIES.md`](../input-semantics-and-join-boundaries/overview.md)

一直坚持把当前语义描述为：

- 受控 reconvergence

而不是：

- 通用 OR-Join primitive

## 6. `Decision` 当前怎样驱动路径激活与节点 skip

### 6.1 skip 的触发条件非常直接

在：

- [`FunctionalFlowCompiler.compileNode(...)`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)

里，node executor 当前会先看参数列表：

- 只要任一参数是 `Decision.unsatisfied()`
- 就不调用真实 `node.execute(...)`

而是直接返回：

- 与该节点输出端口数量一致的 `Decision.unsatisfied()` 列表

### 6.2 这条语义的真正含义

这说明当前 runtime 的分支传播机制不是：

- 运行时物理删边
- 或把整条未命中分支从 DAG 上剔除

而是：

- **让“未命中”作为一个显式信号沿下游继续传播**

因此 downstream 节点之所以不执行，不是因为图结构变了，而是因为它收到的输入里包含未满足信号。

### 6.3 这也解释了为什么 skip 不等于提前退出

当前 skip 仍然有几个很明确的成本边界：

1. 参数装配仍然会等待相关上游 future 完成
2. 编译器仍然会进入通用 node executor
3. scope 级结果收口仍然会等待所有节点 future 完成后再统一取 outcome

所以当前不能把 skip 误读成：

- cancel
- terminate
- 提前退出流程
- 分支级短路调度

更准确的说法仍然是：

- **skip 只是“路径未激活时不执行节点业务逻辑”。**

## 7. `Decision` 与 selective-output 的关系

### 7.1 decision-family 节点天然更适配 selective-output contract

当前 selective-output runtime 校验会统计：

- 同一 `exclusiveGroupId` 里“命中”的输出数量

在：

- [`FunctionalFlowCompiler.validateSelectiveOutputContract(...)`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)

里，命中判定规则是：

- `Decision.satisfied(...)` 视为命中
- `Decision.unsatisfied()` 视为未命中
- 非 `Decision` 的普通值默认视为命中

这解释了为什么：

- `AbstractDecisionNode` 默认 opt-in selective-output 是合理的
- 普通节点不应轻易声明自己支持这套 metadata contract

### 7.2 为什么普通节点不能随便借这套语义

如果一个普通多输出节点也声明：

- `supportsSelectiveOutputMetadata() == true`

但它输出的又不是 `Decision` 列表，那么 runtime 会把大多数普通值都视为“已命中”。

这就很容易触发：

- 同组多个输出同时命中

所以当前这条边界必须坚持：

- **不是“有多个输出口的节点”都能安全消费 selective-output metadata**
- **只有真正遵守 decision-family 输出 contract 的节点，才天然适配这套 runtime 规则**

## 8. 当前最容易犯的几类误读

1. **把 `Decision` 当成端口数据类型**
   - 错，它不在 `DataTypeEnum` 里。
2. **把同口多来源的择一行为当成通用 OR-Join**
   - 错，当前会等待全部完成，而且必须依赖静态分析保证互斥。
3. **把 skip 当成提前退出**
   - 错，当前没有 cancel/terminate。
4. **认为 `Decision` 会作为公开执行结果暴露出去**
   - 常规 ReturnNode 路径返回业务值；显式 exitNode 取原始输出槽位，若指向 decision 节点，可能返回 `Decision`。应通过 ReturnNode 明确收口。
5. **认为普通节点只要声明 `exclusiveGroupId` 就自动拥有 decision 语义**
   - 错，真正生效还取决于 capability opt-in 与 runtime 输出 contract。

## 9. 对 authoring / editor 的直接含义

当前这条边界对 authoring 侧至少有四条直接约束：

1. 不要把 `Decision` 暴露成普通端口类型枚举
2. 不要把“分支被跳过”写成“已经支持提前退出”
3. 不要把同口多来源的现有语义包装成通用 OR-Join 产品心智
4. 不要给普通多输出节点开放看起来像 decision-family 的泛化开关

换句话说，当前 editor 最合理的职责仍然是：

- 展示 decision-family 节点的既有语义
- 帮助作者理解哪些分支会被 skip
- 提前提示错误连接和互斥输出问题

而不是：

- 重新定义一套独立于 core 的路径激活解释器

## 10. 当前更接近什么，还不接近什么

当前这套机制更接近：

- DAG 上的显式路径激活信号传播
- decision-family 节点驱动的受控分支跳过
- 静态分析保护下的最小可解释 reconvergence

当前它还不接近：

- 通用 OR-Join 系统
- 分支级 cancel/terminate
- 运行时删边/重写图结构
- 条件层完备静态推理
- 可对任意节点开放的泛化控制流属性系统

## 11. 实际读代码时，推荐的观察顺序

如果你是第一次沿着这条线读代码，推荐顺序如下：

1. [`Decision`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/Decision.java)
2. [`AbstractDecisionNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java)
3. [`SwitchNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java)
4. [`FunctionalFlowCompiler.buildArgumentsMapper(...)`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
5. [`FunctionalFlowCompiler.compileNode(...)`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
6. [`FlowModelExecutionBranchSkippingTest`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelExecutionBranchSkippingTest.java)
7. [`FlowModelSelectiveOutputRuntimeContractTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelSelectiveOutputRuntimeContractTest.java)

配套阅读建议：

- 输入语义：[`DESIGN-INPUT-SEMANTICS-AND-JOIN-BOUNDARIES.md`](../input-semantics-and-join-boundaries/overview.md)
- selective-output：[`DESIGN-SELECTIVE-OUTPUT-AND-EXCLUSIVE-PORT-BOUNDARIES.md`](../selective-output-and-exclusive-port-boundaries/overview.md)
- 运行时执行总览：[`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
- 结果契约：[`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md)
