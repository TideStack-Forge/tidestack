# LogicFlow 输出互斥与 Selective-Output 边界（Selective Output / Exclusive Port Boundaries）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 端口与数据契约：[`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
>
> 运行时执行：[`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
>
> 静态分析：[`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
>
> NodeDefine 与 editor contract：[`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md)

这份文档把“输出互斥 / selective-output / editor 端口元数据消费边界”抽成一条单独主线，重点不是再讲一遍端口字段，而是把最容易被误写成“好像只要给端口加点 metadata 就万事大吉”的几层东西拆开：

- `exclusiveGroupId + defaultBranch` 当前到底表达什么
- 为什么只有显式 opt-in 的节点才能把这套 metadata 变成语义
- `SwitchNode` 的兼容回填为什么是 built-in 例外，而不是通用推断规则
- editor 为什么已经切到 metadata-first，但仍然保留少量 legacy fallback
- 为什么现在不适合把这条线扩张成复杂约束图

如果说 [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) 更偏“端口与连接总论”，这份文档更像：

- **最小互斥输出合同与跨层消费边界的专题文档**

## 1. 先给结论：当前支持的是“最小互斥输出合同”，不是“通用端口约束系统”

当前已落地的最小事实可以压缩成五句话：

1. `FlowNodePortMeta` 当前新增的互斥输出合同只有 `exclusiveGroupId + defaultBranch`。
2. 这套 metadata 本身不是万能开关，只有支持 selective-output 的节点才会让它进入静态分析和运行时语义。
3. `SwitchNode` 作为 built-in decision node，会为 legacy 分支结构自动回填共享互斥组和默认分支标记。
4. editor 当前已经改成 metadata-first 消费这套合同，但仍保留少量 legacy fallback 作为兼容安全网。
5. 这条线的当前目标是“低学习成本的最小互斥表达”，还不是更大的 bundle/priority/constraint-graph 体系。

如果只记一句口径，应该记这个版本：

- **`exclusiveGroupId + defaultBranch` 是当前最小互斥输出合同，不是通用约束语言。**

## 2. 必须区分的四层概念

### 2.1 端口关系 metadata

端口关系 metadata 回答的是：

- “某个输出端口在 authoring / 持久化层声明了什么关系字段”

当前最小字段只有：

- `exclusiveGroupId`
- `defaultBranch`

### 2.2 Selective-output capability

selective-output capability 回答的是：

- “这个节点是否声明自己支持用输出端口 metadata 参与语义推理”

当前权威入口是：

- `FlowNode.supportsSelectiveOutputMetadata()`

### 2.3 Built-in 兼容归一

built-in 兼容归一回答的是：

- “存量内置节点如果老模型里没有显式 metadata，谁来做最小兼容回填”

当前最典型的是：

- `SwitchNode`
- editor 侧的 `switch` 归一化 helper

### 2.4 Editor consumption boundary

editor consumption boundary 回答的是：

- “前端应该怎样消费这套合同，而不重新发明一套并列语义”

当前原则已经很明确：

- metadata-first
- legacy-fallback only as safety net

## 3. 当前最小合同到底是什么

### 3.1 `exclusiveGroupId`

`exclusiveGroupId` 当前表达的是：

- 同一节点内，一组输出端口属于同一互斥输出组

它不表达：

- 跨节点互斥
- 复杂优先级
- 穷尽/覆盖/条件证明

### 3.2 `defaultBranch`

`defaultBranch` 当前表达的是：

- 在同一个 `exclusiveGroupId` 下，哪个端口是默认分支

它不能脱离 `exclusiveGroupId` 独立存在。

当前 core 也已经把两条最基本的结构错误固定成节点设计错误：

- `defaultBranch` 端口必须声明 `exclusiveGroupId`
- 同一 `exclusiveGroupId` 下最多只能有一个 `defaultBranch`

### 3.3 为什么这就是当前“最小合同”

这一轮没有继续引入：

- `bundleId`
- `exclusiveWithAll`
- `priority`

原因不是“永远不需要”，而是：

- 它们都会显著提高建模、编辑器和静态分析复杂度
- 不符合当前一直坚持的低学习成本优先原则

所以这一轮的正确理解必须是：

- **先让最小互斥输出合同自洽**
- **再考虑是否需要更复杂的端口关系系统**

## 4. 为什么 metadata 本身不是万能 XOR 开关

### 4.1 只有 opt-in 节点才会让它变成语义

当前一个最容易误写错的地方是：

- 不是任何节点只要 output port 上带了 `exclusiveGroupId`，就自动获得 XOR 语义

当前真正的门控点是：

- `FlowNode.supportsSelectiveOutputMetadata()`

如果节点没有显式 opt-in，这些字段最多只是：

- 存量 JSON 里的附加字段

而不会自动进入：

- 成功终点互斥推理
- 输入冲突推理
- 竞态条件推理
- selective-output 运行时互斥校验

### 4.2 为什么必须要这个门控

如果没有这个门控，风险会很直接：

- 普通节点会因为一些看似 harmless 的 metadata 被误读成 decision node
- editor 可能画得“像互斥输出”，但 core 实际并不应承认
- 静态分析会被迫替普通 payload node 背控制流责任

所以当前这条边界必须继续坚持：

- **metadata 只是语义载体**
- **节点 capability 决定它是否真正参与语义收口**

## 5. `SwitchNode` 为什么是内置基准节点

### 5.1 当前 built-in selective-output 语义的核心宿主仍然是 `SwitchNode`

`SwitchNode` 当前之所以是这条线的基准节点，不是因为名字像分支，而是因为它同时具备：

- decision family runtime 语义
- selective-output capability opt-in
- built-in `exclusiveGroupId = "switch-branches"` 兼容回填
- 默认分支显式标记

因此当前文档和实现的正确口径应该是：

- `SwitchNode` 是 built-in XOR 分支节点的基准实现

### 5.2 兼容回填为什么只应是 built-in 例外

当前 `SwitchNode` 会为老模型自动回填共享互斥组和默认分支，这是一种明确的 built-in compatibility backfill。

它不应被扩写成：

- “editor 或 runtime 可以对任意未知节点类型做类似的语义猜测”

否则很快就会重新滑回：

- 节点类型硬编码
- 前端偷偷创造 core 不支持的语义

## 6. Core 当前如何消费这套合同

### 6.1 构造期：节点级 metadata 合法性检查

当前在节点构造完成后，core 会先检查这套 output metadata 自己是否自洽：

- `defaultBranch` 不能脱离 `exclusiveGroupId`
- 同组不能有多个默认分支

这一步属于：

- node design validation

### 6.2 静态分析：只信任显式 opt-in selective-output 节点

当前 `LogicFlowUtil` 已经把几类图级推理收敛到同一条原则：

- 只在“同节点 + 同 `exclusiveGroupId` + 节点显式 opt-in”时承认互斥输出语义

这会影响：

- 成功终点互斥证明
- 输入端口冲突
- racing condition

### 6.3 运行时：同组输出不能同时命中

当前 `FunctionalFlowCompiler` 在运行时还保留了一层防御性契约：

- 对每个 `exclusiveGroupId`
- 若同组内命中了多个输出
- 直接抛运行时错误并 fail-closed

这条校验锁住的是：

- 静态分析漏检时，runtime 也不能默默接受一个自相矛盾的 selective node

## 7. Editor 当前如何消费这套合同

### 7.1 当前已经切到 metadata-first

editor 当前这条线最关键的变化，不是“多了几个 helper”，而是消费原则已经换了：

- 优先相信 output port metadata
- 只有 metadata 不足时才回退到少量 legacy capability

对应关键入口包括：

- `outputPortSemantics.mjs`
- `validate.js`
- `outputPortNormalization.mjs`

### 7.2 当前 legacy fallback 的定位

当前 editor 还保留 legacy fallback，但它的定位必须说清楚：

- 只服务于 built-in / 存量兼容
- 不作为新语义的首选来源

最典型的兼容点是：

- `switch` 输出端口归一化

而下面这种做法已经被明确收回：

- 对 `processStatusRouter` 这类 core 不认识的类型做前端假归一化

### 7.3 为什么这条消费边界很重要

如果 editor 不守住这条边界，最容易重演过去的问题：

- 前端能画、能连、还能看起来像有互斥分支
- 但 core 根本不承认这层语义

所以现在正确的跨层口径必须是：

- **core 定义 selective-output 语义**
- **editor 消费并展示 selective-output 语义**
- **editor 不再发明 selective-output 语义**

## 8. 为什么现在不适合直接做复杂约束图

当前如果直接把这条线升级成更复杂的 constraint graph，会立刻碰到三类现实成本：

1. `FlowNodePortMeta` 还很薄
2. editor 端口属性编辑器还很薄
3. core/runtime/validator 还没有抽象出更通用的端口关系层

这意味着现在硬上复杂系统，带来的更可能是：

- 学习成本和实现成本同时暴涨
- 文档口径重新散掉
- 兼容逻辑比真正语义还复杂

所以当前 `Hybrid-Lite` 式路线仍然是合理的：

- 先把最小字段与最小消费边界打牢

## 9. 这条线仍然缺什么

当前这条线虽然已经落地了第一阶段，但还明显没有覆盖到：

- 条件层静态分析
- 条件重叠与覆盖缺口
- 更大的 bundle/priority 语义
- 更多节点类型的 metadata 迁移
- editor 对更广泛历史脏数据的统一治理

这些都更适合建立在：

- 最小互斥输出合同已经稳定
- core / editor consumption 边界已经稳定

之后再逐步推进。

## 10. 改动这条语义时，至少要联动回查哪些地方

如果后续要继续改 selective-output、互斥输出或 editor 消费边界这条线，建议固定回查下面几层：

1. 总口径
   - [`DESIGN.md`](../../DESIGN.md)
   - 本文档
2. 端口与静态分析
   - [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
   - [`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
   - [`LogicFlowUtil`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)
3. 运行时
   - [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
   - [`FunctionalFlowCompiler`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
4. 节点与 built-in 兼容
   - [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md)
   - [`SwitchNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java)
5. Authoring / editor contract
   - [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md)
   - [`outputPortSemantics.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortSemantics.mjs)
   - [`outputPortNormalization.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortNormalization.mjs)
   - [`validate.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/validate.js)
6. 回归与 fixture
   - [`DESIGN-CONCEPT-TO-SOURCE-CROSSWALK.md`](../concept-to-source-crosswalk/overview.md)

只要这几层里有一层没跟上，这条线就很容易重新退回“看起来像是语义，实际上只是兼容特判”的状态。
