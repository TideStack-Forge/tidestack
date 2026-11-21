# LogicFlow 端口与数据契约（Ports/Data Contracts/Connections）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“端口抽象”“图连接”“类型系统”“成功终点输出契约”几部分的深化说明。它要回答的是一组经常被混在一起的问题：

- LogicFlow 的“数据契约”到底分几层，分别由谁负责。
- 端口名字、端口索引、连线关系里，谁才是运行时真正的权威。
- 为什么节点 metadata 里的端口列表和运行时节点看到的端口列表并不总是 1:1。
- 流程级输入输出类型、节点间类型兼容、成功终点结果契约，是怎么收口到一起的。

如果说 [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) 关注的是“怎么算”，这份文档关注的就是“数据从哪里来、按什么契约流动、在什么边界上被拒绝”。

## 1. 先给结论：LogicFlow 的数据契约是四层叠加，不是一层 metadata

当前 LogicFlow 的数据契约至少分成四层：

1. **流程级契约**
   - 流程整体接收什么输入类型、应该输出什么结果类型。
2. **节点级契约**
   - 节点是什么类型、有哪些输入/输出槽位、它自身是否允许 0 输入或 0 输出。
3. **端口级契约**
   - 每个槽位的数据类型、说明信息，以及输出端口的互斥关系。
4. **连接级契约**
   - 哪个节点的哪个输出槽位，连接到哪个节点的哪个输入槽位。

只有把这四层一起看，才能理解一个流程为什么“可保存但不可执行”“metadata 看起来没问题但运行时端口变多了”“终点节点没有输出端口却仍然合法”。

## 2. 数据模型骨架：流程、节点、端口、连接分别表达什么

### 2.1 流程级：`FlowModelMeta`

流程级元数据入口是：

- [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)

它关心的是整张图的外部契约和骨架：

- `name` / `description`
- `entryNode`
- `exitNode`
- `errorEntryNode`
- `inputDataType`
- `outputDataType`
- `nodes`
- `connections`

从“逻辑流模型元数据格式规范”的角度看，`FlowModelMeta` 里的字段要先分层，再谈是否允许落盘：

- **正式流程模型事实字段**
  - `name` / `description`
  - `entryNode` / `exitNode` / `errorEntryNode`
  - `inputDataType` / `outputDataType`
  - `nodes` / `connections`
- **验证状态相关字段**
  - `validationState`
  - `validationSummary`
  - `validationErrors`
  - `validationDiagnostics`
  - `lastValidatedTime`

这两组字段在 contract 上不是同一层：

- 前一组才构成 LogicFlow 正式持久化模型的核心 contract。
- 后一组只属于建模态展示/诊断读面，用来表达“最近一次权威校验怎么看这张图”。
- 因此，**验证状态相关字段不属于正式持久化模型 contract，规范口径上不应落盘**。
- 当前 develop 读写 API 通过 `includeValidationSnapshot=true` 返回 `{model, validationSnapshot}`，正式 JSON 由 service 剔除诊断字段；`FlowModelMeta` 中仍保留内部兼容字段，不能据此把诊断写回正式模型。

其中最值得注意的是三件事：

1. `entryNode` / `exitNode` / `errorEntryNode` 仍是流程级锚点。
2. `inputDataType` / `outputDataType` 是**流程对外契约**，不是单个节点的局部属性。
3. 节点和连线本身仍以内嵌列表存在于 `FlowModelMeta` 里，而不是拆成独立运行时对象。

### 2.2 节点级：`FlowNodeMeta`

节点级元数据入口是：

- [`FlowNodeMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodeMeta.java)

它表达的是：

- 节点名与描述
- `type`
- `inputPorts`
- `outputPorts`
- `isAsync`
- `isEnabled`
- 节点特有 `extraProps`

设计上它更像“持久化的节点声明”，而不是完整的运行时节点。

### 2.3 端口级：`FlowNodePortMeta`

端口元数据入口是：

- [`FlowNodePortMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java)

每个端口当前承载的最小合同包括：

- `name`
- `description`
- `dataType`
- `exclusiveGroupId`
- `defaultBranch`

这里有一个关键事实：

- `exclusiveGroupId` 与 `defaultBranch` 只定义了**端口层的关系元数据**。
- 它们不是“一写就自动获得分支语义”的万能开关。

真正的运行时意义，还取决于节点是否显式 opt-in selective-output 语义。

### 2.4 连接级：`FlowConnectionMeta`

连接元数据入口是：

- [`FlowConnectionMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowConnectionMeta.java)

它表达的不是“两个节点大概连起来”，而是非常具体的：

- `sourceNode`
- `sourceOutputPort`
- `targetNode`
- `targetInputPort`

这说明 LogicFlow 的图连接从一开始就是：

- **节点名 + 端口索引**

的结构，而不是：

- 端口名绑定
- 模糊边
- 运行时自动猜连线含义

## 3. 位置优先：运行时真正以“端口索引”作为权威，而不是端口名

这是 LogicFlow 非常核心的一条设计取向。

### 3.1 端口名主要是可读性与 authoring 信息

端口名当然重要，但它主要服务于：

- editor 显示
- 建模可读性
- 决策节点的语义说明

真正的运行时连线绑定，并不按端口名查找，而是按：

- `sourceOutputPort`
- `targetInputPort`

来完成。

### 3.2 为什么这是更稳定的设计

位置优先的好处是：

- 运行时编译映射更直接，不需要做 name resolution
- 节点端口可以允许无名、空名或由规则自动生成
- 同一节点类型可以在 authoring 侧动态生成端口，但 runtime 仍能用统一索引语义接住

代价也很明确：

- 端口顺序就成了合同的一部分
- authoring 工具必须谨慎处理端口重排
- 端口 metadata 看起来“相似”不代表运行时位置可互换

因此，LogicFlow 当前更像：

- **位置驱动的端口图**

而不是：

- **名称驱动的消息通道**

## 4. 运行时端口不是 metadata 的简单镜像

这是最容易误解的一点，也是现在文档必须单独强调的一点。

### 4.1 端口数量的最终权威在 `AbstractNode`

运行时端口由：

- [`AbstractNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)

动态 materialize。

输入端口数量取决于：

- metadata 中声明的输入端口数
- 现有入边使用到的最大输入端口索引 `+ 1`
- `minInputPortCount()`
- 再受 `maxInputPortCount()` 上限约束

输出端口数量同理，取决于：

- metadata 中声明的输出端口数
- 现有出边使用到的最大输出端口索引 `+ 1`
- `minOutputPortCount()`
- 再受 `maxOutputPortCount()` 上限约束

### 4.2 这意味着什么

这意味着以下几件事都是真实现象，而不是异常：

1. metadata 里没写那么多端口，但运行时端口变多了。
2. 节点类型要求最少 1 个输入/输出时，即使 metadata 为空，也会被补出来。
3. 连线如果引用了更高的端口索引，运行时会尝试补齐该索引之前的槽位。
4. 但如果补齐后仍然超过节点允许的 maxPortCount，模型会被判 invalid。

### 4.3 自动补出来的端口长什么样

运行时补齐的默认端口来自：

- [`DefaultFlowNodeInPort`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowNodeInPort.java)
- [`DefaultFlowNodeOutPort`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowNodeOutPort.java)

默认行为是：

- 输入端口名类似 `input1`、`input2`
- 输出端口名类似 `output1`、`output2`
- 数据类型默认是 `any`

所以“补齐端口”并不是只补一个空壳，而是补一份最小可运行合同。

### 4.4 0 输入 / 0 输出都是可以合法存在的

当前设计明确允许：

- 0 输入节点
  - 例如常量节点、数据源节点
- 0 输出节点
  - 例如 `ReturnNode` 这类成功终点
  - 或纯消费类终点

因此：

- “没有输出端口”不等于非法
- “没有输入端口”也不等于非法

是否合法，要看该节点类型本身的 min/max 端口合同，以及静态分析对整张图的收口判断。

## 5. 连接不是 runtime 查表，而是在模型初始化时一次性绑定

### 5.1 `DefaultFlowConnection` 是带缓存引用的运行时连接

运行时连接实现位于：

- [`DefaultFlowConnection`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowConnection.java)

它除了持有 metadata 外，还会缓存：

- `sourceNode`
- `targetNode`

这些引用不是在 getter 里每次现查，而是在：

- [`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)

初始化时一次性绑定。

### 5.2 初始化顺序为什么重要

`DefaultFlowModel.initialize(...)` 的相关顺序是：

1. 先构造所有 `DefaultFlowConnection`
2. 再构造所有 `FlowNode`
3. 再把每条连接的 `sourceNode/targetNode` 绑定到节点实例
4. 然后校验连线端口是否越界

这个顺序保证了两件事：

- 节点在构造时就能看到整张图的连线集合
- 连接在后续执行与静态分析中又能直接拿到节点实例

这比在运行时反复通过名字去查节点列表更稳定，也更便于静态分析复用。

## 6. 类型系统：它是“最小但够用”的契约系统

### 6.1 `DataTypeEnum` 不是完整类型推断，而是兼容性基线

当前类型系统入口是：

- [`DataTypeEnum`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DataTypeEnum.java)

它支持的核心事实很简单：

- `ANY` 与任何类型兼容
- 数值类型之间互相兼容
- 其他类型通常要求完全一致

它的定位是：

- **运行时/静态分析用的契约兼容检查**

而不是：

- 完整的数据结构推断系统

### 6.2 未知类型字符串会退回 `ANY`

`DataTypeEnum.fromValue(...)` 对未知字符串会回退到：

- `DataTypeEnum.ANY`

这是一种偏兼容性的设计：

- 它降低了存量模型或弱 authoring 场景因为陌生类型字符串而直接崩掉的概率

但它也意味着：

- 类型声明拼写错误可能被“变宽”成 `ANY`
- 真正严格的类型治理不能只依赖这层枚举

## 7. 流程级输入输出合同是怎么落到节点图上的

真正把流程级类型合同落地的是：

- [`LogicFlowUtil.validateInputTypeCompatibility`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)

当前它主要做四组检查。

### 7.1 流程输入类型 vs main scope 根节点输入端口

main scope 中每个启用且没有有效输入连线的根节点，都按位置接收流程输入：

- `FlowModelMeta.inputDataType`
- 必须兼容这些根节点所有输入端口的 `dataType`

这说明：

- 流程输入类型是“外部调用者视角”的约束
- 根节点（不限于显式 entry）是它进入图内部的类型边界。

### 7.2 error scope 根节点输入合同

error scope 中每个没有有效输入连线的根节点都有额外限制：

- 最多只能有 1 个输入端口
- 且该端口类型必须兼容 `MAP`

原因并不复杂：

- 当前 error scope 接收到的是标准化 `errorContext`
- `errorContext` 本质上就是一个 `Map`

因此 error scope 当前的数据入口契约非常明确，也很克制。

### 7.3 成功终点输出合同

成功终点的输出类型要兼容：

- `FlowModelMeta.outputDataType`

但这里有一个非常重要的细分：

- 对 `ReturnNode`
  - 校验的是它第 0 个**输入端口**的类型
- 对显式 `exitNode`
  - 校验的是它第 0 个**输出端口**的类型

也就是说，当前成功终点结果契约已经收口为：

- `ReturnNode` 通过“返回输入”表达成功结果
- `exitNode` 通过“节点输出”表达成功结果

二者对外都归并到流程的 `outputDataType`。

### 7.4 节点间连线的两端类型兼容

对每个启用节点的每个输入端口：

- 会找到所有连向这个输入端口的活跃、合法连线
- 逐一检查上游输出端口类型是否兼容当前输入端口类型

这一步是图内部最基础的“边契约检查”。

如果这一步失败，说明问题不是流程级 contract，而是局部图结构本身已经不自洽。

## 8. 输出端口元数据也是数据契约的一部分，但不能脱离节点能力单独理解

### 8.1 `exclusiveGroupId` / `defaultBranch` 是端口层关系字段

这两个字段定义在：

- [`FlowNodePortMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java)

它们表达的是：

- 某些输出端口属于同一互斥组
- 其中一个端口可以被标记为默认分支

### 8.2 元数据本身先要合法

这层最小合法性由：

- [`AbstractNode.validateOutputPortMetadata`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)

负责兜底：

- `defaultBranch` 必须带 `exclusiveGroupId`
- 同一组最多一个 `defaultBranch`

### 8.3 真正的语义生效还要看节点是否 opt-in

即使端口 metadata 合法，也不代表节点就一定拥有“互斥多出口”的运行时含义。

真正的 selective-output 语义还取决于：

- 节点是否声明 `supportsSelectiveOutputMetadata() == true`

这也是为什么当前这套语义应主要理解为：

- 决策节点的端口合同

而不是：

- 任意普通节点都能随便附加的分支属性

## 9. authoring 看到的端口结构，不等于 runtime 最终端口结构

这条边界现在尤其需要单独写清楚。

authoring 侧主要消费的是：

- `NodeDefine.scaffold`
- `NodeDefine.portRules`
- 前端临时的端口编辑状态

但 runtime 最终看到的端口，仍然由 `core` 里的：

- 节点 min/max 端口规则
- 连线实际引用到的端口索引
- `AbstractNode` 补齐逻辑

共同决定。

因此合理的分工应该是：

- authoring 侧尽量把初始端口结构和编辑约束做对
- core 侧保留最终运行时端口物化与兜底校验的权威

不能反过来依赖 editor 假设：

- “metadata 里只有这些端口，所以 runtime 一定也只有这些端口”

## 10. 后续演进时，这层契约最容易被破坏的地方

如果后面继续演进 LogicFlow，最容易破坏这层契约的地方主要有四类。

### 10.1 把端口名误当作运行时绑定 key

一旦有人开始按端口名做强绑定，而不是按索引理解连线，就会和现有连接模型产生冲突。

### 10.2 忽略端口补齐，默认 metadata 就是最终端口

这会直接导致：

- 端口越界判断错误
- editor/dev 与 core 对端口数量理解不一致
- 某些旧模型在保存或执行时出现“前端看着没问题，后端说越界”

### 10.3 把 `ANY` 当成“严格类型”

`ANY` 的本意是兼容兜底，不是严格设计。若把大量本应明确的类型都退化为 `ANY`，流程 contract 会快速失去约束力。

### 10.4 把 selective-output 当作普通节点的万能扩展开关

如果不先定义“未命中输出如何表达”，就让普通节点随意 opt-in selective-output，运行时很容易把所有普通值都当成“命中”，反而制造新的冲突。

## 11. 实际阅读代码时，推荐的观察顺序

如果你是第一次从这层进入代码，建议按这个顺序看：

1. [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)
2. [`FlowNodeMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodeMeta.java)
3. [`FlowNodePortMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java)
4. [`FlowConnectionMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowConnectionMeta.java)
5. [`AbstractNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)
6. [`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)
7. [`LogicFlowUtil`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)

配套阅读建议：

- 静态分析如何消费这些契约：[`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
- 运行时如何把这些端口映射成参数与结果：[`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
- 节点扩展时如何遵守这层端口合同：[`DESIGN-NODE-EXTENSIONS.md`](../node-extensions/overview.md)
- dev/editor 如何消费 authoring-facing 端口合同：[`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md)
