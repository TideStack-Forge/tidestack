# LogicFlow 节点能力矩阵与模块边界总览（Node Capability Matrix）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档的目标不是再讲一遍某个单点机制，而是把前面已经拆开的多份章节，从一个横切面的角度重新压回一张“长期可维护的总表”：

- 当前有哪些标准节点家族。
- 它们分别落在哪个模块边界里。
- 每类节点在 runtime / authoring / contract 上各自具备什么能力。
- 哪些节点现在看起来像某种能力，但其实还没有真正成型。

如果说 [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md) 讲的是“语义分组”，这份文档讲的就是：

- **把语义分组落到模块、基类、端口轮廓、终点角色、外部依赖和成熟度上的一张横向能力表**

## 1. 为什么需要一张横切能力矩阵

前面的章节已经分别解释了：

- 控制流语义
- 端口与数据契约
- 模型装配
- 结果合同与诊断呈现
- NodeDefine / editor contract

但如果缺少一张横切矩阵，后续维护仍然会遇到三个常见问题：

1. 很难快速判断某个节点到底属于哪个模块边界。
2. 很难一眼看出某类节点是“普通变换节点”还是“控制流语义节点”。
3. 很难在新增节点时判断自己是应该落在 core、extra-nodes，还是 data-model family。

所以这章的用途更偏向：

- 新人上手时的“地图”
- 后续设计评审时的“横表”
- 节点扩展前的“边界检查单”

## 2. 先看模块边界：节点能力是按模块分层供给的

当前 LogicFlow 的节点能力不是全堆在一个模块里，而是至少分成三层。

### 2.1 `ouroboros-logicflow-core` 与 `ouroboros-logicflow-builtin-nodes`

core 提供节点抽象、编译器和静态分析；builtin-nodes 通过相同 SPI 提供标准节点实现，包括：

- 基础抽象类
- 标准变换节点
- 当前最小成型的控制流节点
- 成功终点与流程组合节点

它的特点是：

- 语义权威最高
- 必须与编译器、静态分析、端口契约完全对齐
- 任何新控制流原语都优先应该在这里落地

### 2.2 `ouroboros-logicflow-extra-nodes`

这是较轻量的扩展层，当前主要提供：

- 异常终点节点
- 文件格式/资源读取类节点

它的特点是：

- 不重写编译器语义
- 更偏能力接入
- 当前节点数不多，控制流能力也不成体系

### 2.3 `ouroboros-logicflow-data-model`

这是领域能力扩展层，当前主要提供：

- 数据模型句柄节点
- 一组数据库操作节点

它的特点是：

- 语义核心不是“流程调度”，而是“数据模型能力接入”
- 很多节点共享统一的抽象基类
- 更像一个垂直领域节点家族，而不是控制流原语库

## 3. 怎么读这张矩阵

下面的矩阵有几列需要先约定一下含义：

- `家族/节点`
  - 可能是一类节点，也可能是一个具体节点；如果是家族，会明确写出代表成员。
- `模块`
  - 节点能力主要由哪个 Maven 模块提供。
- `运行时基类`
  - 节点在 runtime 里最关键的抽象落点。
- `能力角色`
  - 这个节点最核心的语义职责，而不是 UI 名字。
- `I/O 轮廓`
  - 这里描述的是当前实现与 authoring 合同的组合轮廓，优先强调“最常见/最关键的形状”，不是要替代详细端口规则文档。
- `终点角色`
  - `successful` / `exception` / `none`
- `选择性输出`
  - 指是否具备 selective-output 语义入口，而不是“能不能有多个输出口”。
- `外部依赖`
  - 指是否明显依赖另一个流程、平台能力、数据模型或文件资源。
- `当前备注`
  - 只写最关键的设计事实或当前缺口。

## 4. 节点能力矩阵（当前 baseline）

| 家族/节点 | 模块 | 运行时基类 | 能力角色 | I/O 轮廓 | 终点角色 | 选择性输出 | 外部依赖 | 当前备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ConstNode` | `logicflow-builtin-nodes` | `AbstractNode<Object>` | 数据源/常量生成 | authoring 为 `0-1 入 / 1 出`，常见用法是“无上游生成值” | `none` | 否 | 无 | 典型 source-like 节点，体现“0 输入合法”的 authoring 需求 |
| `ScriptNode` / `ValidateNode` | `logicflow-builtin-nodes` | `AbstractNode<Object>` / `AbstractSimpleNode<Object>` | 单点计算/校验 | 常见 `1 入 / 1 出` | `none` | 否 | 脚本引擎 / JSON Schema | 是最典型的“普通功能节点” |
| `MappingNode` / `PickNode` / `OmitNode` | `logicflow-builtin-nodes` | `AbstractNode<Object>` | 数据重组/属性变换 | 常见 `1 入 / 1 出` | `none` | 否 | 表达式/映射配置 | 仍属于 payload transform，不参与路径选择 |
| `ConcatNode` / `DistinctNode` / `FilterNode` / `IntersectNode` / `SubtractNode` / `TakeNode` | `logicflow-builtin-nodes` | `AbstractNode<?>` / `AbstractSimpleNode<?>` | 集合操作 | 多数是 `1 入 / 1 出`，少数接收多值比较 | `none` | 否 | 表达式或集合语义 | 是一组集合变换家族，不应误读为控制流节点 |
| `MergeNode` | `logicflow-builtin-nodes` | `AbstractNode<Object>` | Map payload merge | 多入 `Map` / 单出 `Map` | `none` | 否 | 无 | 解决数据合并，不是 control-flow join |
| `SwitchNode` | `logicflow-builtin-nodes` | `AbstractDecisionNode` | 条件分支/XOR 选择 | `1 入 / 多互斥出`，含默认分支 | `none` | 是 | 表达式执行 | 当前唯一稳定成型的内置控制流分支节点 |
| `CallFlowNode` | `logicflow-builtin-nodes` | `AbstractNode<Object>` | 流程组合/执行边界 | 常见 `1 入 / 1 出` | `none` | 否 | 依赖另一份 `FlowModel` | 是组合节点，不是跳转/分支原语 |
| `ReturnNode` | `logicflow-builtin-nodes` | `AbstractTerminalNode` | 成功结果收口 | `1 入 / 0 出` | `successful` | 否 | 无 | 与显式 `exitNode` 一起收口到 `FlowOutcome` |
| `MutexNode` | `logicflow-builtin-nodes` | `AbstractSimpleNode<Object>` | 名义上的“互斥汇集” | 当前实现是 `1 入 / 1 出` 透传 | `none` | 否 | 无 | 当前更像 legacy placeholder；NodeDefine 描述与 runtime 语义不一致 |
| `OuroborosExceptionsNode` | `logicflow-extra-nodes` | `AbstractTerminalNode` | 异常终止 | `1+ 入 / 0 出` | `exception` | 否 | 平台异常类型 | 是异常终点；静态候选包含终点家族，但执行抛错，不产生成功结果 |
| `ReadExcelNode` | `logicflow-extra-nodes` | `AbstractSimpleNode<Object>` | 文件读取/格式解析 | 常见 `1 入 / 1 出` | `none` | 否 | Excel 数据源/文件内容 | 能力来自资源读取，不改变流程调度语义 |
| `DataModelNode` | `logicflow-data-model` | `AbstractNode<Object>` | 数据模型句柄获取 | authoring 为 `0-1 入 / 1 出`，runtime 仍受基类端口补齐影响 | `none` | 否 | `DataModelCenter` | 是领域入口节点，不是控制流节点；authoring 与 runtime 端口形状仍需谨慎理解 |
| `AbstractDataNode` 家族：`search/getOne/count/insert/update/delete/...` | `logicflow-data-model` | `AbstractDataNode<T, P>` | 数据模型 CRUD/查询 | 典型 `1 入 / 1 出` | `none` | 否 | `DataModel` / 插件 | 是统一的领域操作家族，共享数据模型解析与 runtime failure 归一 |
| `NormalizeWhereNode` | `logicflow-data-model` | `AbstractDataNode<Map<String,Object>, Map<String,Object>>` | 查询条件规范化 | `1 入 / 1 出` | `none` | 否 | 数据模型字段/运算符 | 更像 data-model family 的辅助变换节点 |

## 5. 从矩阵里可以直接读出的几个关键结论

### 5.1 当前真正的控制流家族仍然很薄

从矩阵里能直接看到：

- 真正具备路径选择语义的内置节点，当前只有 `SwitchNode`
- 成功终点与异常终点已经分化清楚
- 但 join / recover / cancel 这类控制流原语仍未成型

这再次印证了一个事实：

- 当前 LogicFlow 更成熟的是“编排执行内核 + 标准变换节点”
- 还不是“大而全的控制流节点库”

### 5.2 大多数节点的本质仍然是 payload transform

即使某些节点在 UI 上长得像“流程节点”，它们的 runtime 角色依旧只是：

- 变换输入值
- 产出输出值

而不是：

- 决定路径
- 决定 join policy
- 决定 cancel policy

这也是为什么前面几章一直强调：

- 不要把调度语义偷塞进普通节点属性里

### 5.3 领域节点家族已经存在，但控制流节点家族还没完全存在

`logicflow-data-model` 已经是一个相当清晰的垂直节点家族：

- 统一的抽象基类
- 统一的数据模型解析
- 一组 CRUD/查询节点

反过来，控制流方向还缺：

- join family
- recover family
- terminate/cancel family

这说明如果后续继续扩展节点库，优先级更高的不是再添若干零散 transform node，而是：

- 补齐一组真正成体系的控制流原语

## 6. 当前矩阵里最值得注意的边界与缺口

### 6.1 `MutexNode` 仍然是最明显的目录一致性缺口

它在矩阵里之所以单独列出来，是因为它同时在三层上不一致：

- 名字像 join
- NodeDefine 描述像 race/先到先得
- runtime 却只是单入单出的透传节点

因此现在对它最稳妥的理解仍然是：

- legacy placeholder

而不是：

- 可依赖的控制流能力

### 6.2 `MergeNode` 不应被误记成 join

矩阵特意把它和 `SwitchNode` 分开，就是为了避免常见误解：

- `MergeNode` 是数据合并
- 不是控制流汇合

这一点如果记错，后面画图、写静态分析、做 editor 提示都会跑偏。

### 6.3 `DataModelNode` 体现了“authoring contract != runtime 端口形状”

它是一个很好的提醒案例：

- NodeDefine authoring 侧把它当作可选输入的数据模型入口
- 但 runtime 端口物化仍然受 `AbstractNode` 默认端口补齐影响

这类节点最能说明一个原则：

- 端口最终权威仍在 core runtime，而不是 scaffold 长什么样

## 7. 后续新增节点时，怎么用这张表做边界检查

新增一个节点前，建议先用这张表问自己五个问题：

1. 它应该落在哪个模块边界。
2. 它是 payload transform、control-flow、boundary，还是 domain capability。
3. 它应该继承哪条抽象基类链。
4. 它有没有真的引入新的调度语义，如果有，编译器和静态分析是否已经准备好了。
5. 它在矩阵里会不会和某个现有节点形成职责重叠或命名误导。

如果这五个问题答不清楚，通常说明它还不适合直接进入实现。

## 8. 这张矩阵如何与前面的章节配合使用

建议这样配套阅读：

- 想理解节点语义分组：
  - [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md)
- 想理解新增节点时如何选基类、接 SPI：
  - [`DESIGN-NODE-EXTENSIONS.md`](../node-extensions/overview.md)
- 想理解端口补齐和数据类型为什么会影响矩阵判断：
  - [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
- 想理解 NodeDefine 的分组、终点视觉、editor capability：
  - [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md)
- 想理解模型从 meta 变成 runtime node graph 的装配过程：
  - [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md)
