# LogicFlow 输入语义与 Join 边界（Input Semantics / Join Boundaries）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 运行时执行：[`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
>
> 静态分析：[`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
>
> 控制流节点目录：[`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md)
>
> 延后能力：[`DESIGN-DEFERRED-CAPABILITIES-AND-NON-GOALS.md`](../deferred-capabilities-and-non-goals/overview.md)

这份文档把“输入语义 / 同口多来源 / Join 边界”抽成一条单独主线，重点不是重复讲端口定义，而是把最容易被误写成“好像已经支持”的几层东西拆开：

- 流程输入如何进入没有有效输入连线的根节点
- 多输入端口节点当前到底是不是严格 AND
- 同一个输入端口接多条上游时，当前到底是什么语义
- `MergeNode` 为什么不是 control-flow join
- 为什么当前不应把 OR / Any / Race 做成任意普通节点的通用属性

如果说 [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) 更偏“执行机制总览”，这份文档更像：

- **当前输入合同与 Join 边界的专题文档**

## 1. 先给结论：当前支持的是“严格输入合同 + 受控 reconvergence”，不是“通用 Join 系统”

当前已落地的最小事实可以压缩成五句话：

1. 外部输入按位置绑定到所在 scope 内所有无有效输入连线的根节点，缺参会在节点执行前拒绝。
2. 对于多个输入端口，当前语义仍然是严格 AND，所有输入端口都必须满足。
3. 对于同一个输入端口的多上游，runtime 会等待全部来源完成后择一，但静态分析要求这些来源必须可证明互斥。
4. `MergeNode` 合并的是 payload，不是 trigger policy，因此它不是 control-flow join。
5. 通用 OR-Join / XOR-Join / Any-Join 目前仍未实现；如果未来需要，应优先做专用 Join 原语，而不是给所有普通节点加调度属性。

如果只记一句口径，应该记这个版本：

- **当前输入层已经有受控 reconvergence 语义，但仍然没有通用 Join 原语。**

## 2. 必须区分的四层概念

### 2.1 流程输入绑定

流程输入绑定回答的是：

- “外部传进来的 `Object... inputs` 如何变成图里的第一个可消费输入”

当前答案是：

- 按位置映射到所在 scope 内所有无有效输入连线的根节点输入端口

### 2.2 多输入端口汇聚

多输入端口汇聚回答的是：

- “一个节点如果有多个输入端口，它何时才会真正执行”

当前答案是：

- 所有输入端口都满足后才执行

这就是当前默认 AND-Join 语义。

### 2.3 同口多来源 reconvergence

同口多来源回答的是：

- “如果同一个输入端口被多条上游连接，它当前到底是怎么取值的”

当前答案不是“谁先到用谁”，而是：

- 等所有来源完成后，按固定顺序择一有效值

但这层 runtime 语义要受静态分析强约束：

- 只有当这些来源可证明互斥时，这种 reconvergence 才合法

### 2.4 Control-flow join primitive

control-flow join primitive 回答的是：

- “系统是否已经提供了明确的 Any-Join / Xor-Join / Error-Join 这类控制流原语”

当前答案仍然是：

- **没有**

所以当前不能把下面这些东西误当成 join primitive：

- `MergeNode`
- 任意普通节点的多输入
- 同口多来源 reconvergence

## 3. 流程输入如何进入图

### 3.1 无有效输入连线的根节点按位置接收流程输入

每次执行 `FlowModel.execute(Object... inputs)` 时，当前 `core` 的稳定合同是：

- main scope 内所有没有有效输入连线的根节点都可以承接外部输入，包括 `entryNode` 之外的根节点。
- 每个根节点的第 `i` 个输入端口接收同一份 `inputs[i]`，不按端口名匹配。
- 零输入端口的根节点不消耗参数；禁用节点的连线不算有效输入连线。

例如 `execute(request, dataModel)` 会让每个根节点的端口 0 接收 `request`，端口 1（如有）接收 `dataModel`。节点仍须属于既有 main / error scope，孤立节点不会因为没有输入连线而自动加入执行。

### 3.2 缺参会在正常执行前 fail-closed

当前 scope 在真正进入节点执行前，会先检查：

- `input.length < max(当前 scope 内所有根节点的实际输入端口数)`

如果不满足，会直接失败，典型报错是：

- “输入参数数量不正确”

这条规则的意义很直接：

- 防止缺参被悄悄扩散成隐式 null、随机异常或更难定位的 downstream failure

### 3.3 部分连线的节点仍须接全，异常根节点接收 errorContext

已有任意有效输入连线的节点，其所有输入端口都必须接全。不会把其中的悬空端口补成流程输入，也不会在上游分支未激活时改取流程输入。

error scope 使用同一条根节点绑定规则，但它的外部输入固定为 `new Object[]{errorContext}`。因此每个异常根节点最多有一个输入端口，且须兼容 `MAP`；它接收标准化异常上下文，不直接接收主流程的业务参数。

## 4. 多输入端口的默认语义：严格 AND

### 4.1 多输入端口不是“有一个到就能跑”

当前只要一个节点有多个输入端口，它的真实语义仍然是：

- 每个输入端口都必须有可消费输入
- 参数装配完成后，节点才会真正执行

这条规则适用于绝大多数普通节点，也适用于 `MergeNode` 这类多输入节点。

### 4.2 `Decision.unsatisfied()` 不会把 AND 变成 OR

当前决策语义和多输入语义是叠加关系，不是替换关系：

- 多输入节点仍然会等待所有输入参数 future 装配完成
- 若某个输入最终是 `Decision.unsatisfied()`，节点会被 skip

因此现在的系统不是：

- “任一分支到了就触发”

而是：

- “先把输入层收齐，再由 `Decision` 语义决定节点是否真正执行”

### 4.3 Skip 不是零成本，也不是抢跑

当前 skip 仍有两个明确边界：

- 参数装配阶段仍会等待相关 future 完成
- 只是在 nodeExecutor 阶段不进入真实 `node.execute(...)`

所以不能把当前 skip 误读成：

- 高性能短路调度器

它更像：

- 以确定性和可解释性优先的路径激活实现

## 5. 同一个输入端口的多上游：当前是“受控 reconvergence”

### 5.1 Runtime 语义：等待全部完成后择一

当同一个输入端口被多条上游连接时，当前 runtime 做法是：

1. 先等待这些来源的 future 全部完成
2. 按固定顺序寻找第一个有效值
3. 过滤掉 `Decision.unsatisfied()`
4. 找不到有效值则返回 `Decision.unsatisfied()`

这条语义最容易被误写成两种错误说法：

- “这就是 Any-Join”
- “谁先到就用谁”

这两种说法当前都不准确。

### 5.2 它为什么只能是受控语义，而不能放任使用

如果没有静态分析约束，同口多来源就会把结果绑定到：

- 连线顺序
- 隐式先后关系
- 不可解释的 reconvergence 规则

这会直接破坏 LogicFlow 一直坚持的设计原则：

- 能在设计期拒绝的坏图，不要推到运行时靠偶然行为决定结果

所以当前的合理表述必须是：

- **runtime 提供了受控 reconvergence 行为**
- **design-time 决定这种 reconvergence 是否被允许**

### 5.3 静态分析对它的最低要求：必须可证明互斥

当前对同口多来源的核心约束是：

- 这些来源必须能在语义上被证明互斥

否则就属于：

- `InputPortConflict`

这条规则的本质不是“限制建模自由”，而是：

- 防止把顺序依赖偷偷塞进 declarative graph

## 6. 竞态条件：为什么“不同输入端口也可能不合法”

当前另一个经常被忽略的边界是：

- 即使不是同一个输入端口，只要同组互斥输出分别连到同一个下游节点的不同输入端口，也可能构成 racing condition

原因是：

- 下游节点会等待多个输入端口都满足
- 但互斥组保证这些输入不可能同时满足

结果就是：

- 这个节点在图结构上看起来被连接了
- 运行时却永远不可能真正激活

所以当前静态分析会把它直接拒绝。

## 7. `MergeNode` 为什么不是 Join

### 7.1 它解决的是 payload merge，不是 trigger policy

`MergeNode` 当前的真实语义非常明确：

- 输入必须是 `Map`
- 所有输入都要等齐
- 多个输入值会 merge / deepMerge 成一个结果

它解决的是：

- “几份对象数据如何拼成一份”

而不是：

- “哪一路先到就继续”
- “几个分支在这里形成控制流汇合”

### 7.2 为什么这个误解危险

一旦把 `MergeNode` 误记成 join，会连带把很多地方都带偏：

- 建模者会拿它去表达并不存在的调度语义
- 静态分析会被要求替一个 payload node 背 control-flow 责任
- editor 也容易继续用错误的视觉或提示强化这种误解

所以这条口径需要继续明确写死：

- **`MergeNode` 是数据节点，不是控制流原语。**

## 8. 为什么当前不适合把 OR / Any / Race 做成任意节点的通用属性

当前如果给所有普通节点都加“任一触发 / OR / Any / Race”这类通用属性，会立刻带来一组系统性问题：

- 节点执行次数不再稳定
- 重入与副作用边界变复杂
- 异步调度与先后顺序依赖变多
- 死锁、永不激活、重复触发的分析难度大幅上升
- editor / dev / core 需要同时理解新的调度元语义

这与当前模块一直坚持的低学习成本原则是冲突的。

所以更稳妥的路线仍然是：

- **普通节点保持“消费输入、产出结果”的简单心智**
- **复杂 Join 语义交给专用控制流原语**

## 9. 如果未来真有需求，应该怎么推进

当前最合理的 deferred 路线仍然是：

- `XorJoinNode`
- `AnyJoinNode`
- `ErrorJoinNode`

也就是说，未来若要增强 Join 表达力，优先做：

- 专用 join family

而不是：

- 给所有节点追加可配置调度属性

这样至少能把复杂性收束在：

- 少数明确的控制流节点
- 更可控的静态分析与 authoring contract

## 10. 改动这条语义时，至少要联动回查哪些地方

如果后续要继续改输入语义、同口多来源或 Join 这条线，建议固定回查下面几层：

1. 总口径
   - [`DESIGN.md`](../../DESIGN.md)
   - 本文档
2. 运行时
   - [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
   - [`FunctionalFlowCompiler`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
3. 静态分析
   - [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
   - [`LogicFlowUtil`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)
4. 节点边界
   - [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md)
   - [`DESIGN-NODE-CAPABILITY-MATRIX.md`](../node-capability-matrix/overview.md)
5. 延后能力
   - [`DESIGN-DEFERRED-CAPABILITIES-AND-NON-GOALS.md`](../deferred-capabilities-and-non-goals/overview.md)
6. 回归与 fixture
   - [`DESIGN-CONCEPT-TO-SOURCE-CROSSWALK.md`](../concept-to-source-crosswalk/overview.md)

如果某次改动只改了编译器行为，却没有同步检查静态分析、节点目录和 deferred 边界，这条语义很快又会重新变模糊。
