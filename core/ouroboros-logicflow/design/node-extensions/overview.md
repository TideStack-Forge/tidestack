# LogicFlow 节点扩展指南（NodeBuilder/SPI/端口语义）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“节点体系与扩展点”的深化说明，目标是把“如何正确新增一个节点类型”讲清楚，并把最容易踩坑的端口语义、互斥契约、工具链配套一次性写明。

## 1. 新增节点的最小交付件（不要只交付一半）

一个可用的新节点类型，至少需要同时具备：

1. **执行侧能力（core runtime 能创建并执行）**
   - 提供 `NodeBuilder`，能从 `FlowNodeMeta` 构造出 `FlowNode`
   - 通过 SPI 注册后能被 `NodeFactory` 找到；内置节点也由独立 builtin-nodes 模块的 SPI 描述符提供
2. **建模/编辑侧能力（editor 能展示、能创建 scaffold）**
   - 提供 `NodeDefine` JSON，源码放入 `src/main/metadata/*.flow-node.json`，构建后进入 `META-INF/ouroboros/metadata/`
   - 至少包含 type/label/group/order/scaffold/portRules

否则会出现典型断裂：

- “前端能拖出来，但后端 NodeTypeNotFound”
- 或“后端能执行，但前端无法建模/缺少属性面板/端口规则”

## 2. 执行侧：NodeBuilder 与 NodeFactory

### 2.1 NodeBuilder 的契约

`NodeBuilder` 负责两件事：

- `support(meta)`：判断某 `FlowNodeMeta` 是否属于这个节点类型
- `build(FlowModel, FlowNodeMeta, NodeReferenceCatalog)`：显式接收 owner、节点元数据和引用目录，构造节点实例

代码：

- [`NodeBuilder`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeBuilder.java)

实现建议：

- `support()` 只做 type 匹配（例如 `"validate".equalsIgnoreCase(meta.getType())`），避免把复杂校验塞进 support。
- 构造期的结构性问题（缺字段、配置非法）用 `NodeDesignException` 加到节点 errors 中，而不是直接抛 RuntimeException（除非它是不可恢复的构造失败）。

### 2.2 NodeFactory 的查找与后置校验

`NodeFactory` 从 `META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder` 加载 builders；内置节点和外部扩展共用此机制，没有 core 硬编码的内置列表。

`createNode(owner, nodeMeta, references)` 收集所有 `support(nodeMeta)` 为 true 的 builder：零个时抛 `NodeTypeNotFoundException`，多于一个时抛 `NodeDesignException`；只有唯一匹配时才调用其 `build(owner, nodeMeta, references)`。排序不能用于覆盖内置实现，新增节点应避免类型冲突。

匹配后会 build 节点，并对 `AbstractNode` 执行一次输出端口元数据校验：

- defaultBranch 必须声明 exclusiveGroupId
- 同组最多一个 defaultBranch

代码：

- [`NodeFactory`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeFactory.java)
- [`AbstractNode.validateOutputPortMetadata`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)

这意味着：

- 端口元数据的“合法性底线”是由 core 强制的，不应依赖 editor 自行约束。

### 2.3 SPI 注册（插件式接入）

若节点属于扩展模块（推荐），通过 SPI 注册：

- 文件：`src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder`
- 内容：每行一个 builder 的全限定类名

参考：

- [`ouroboros-logicflow-data-model` SPI](../../ouroboros-logicflow-data-model/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder)
- [`ouroboros-logicflow-extra-nodes` SPI](../../ouroboros-logicflow-extra-nodes/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder)

注意：

- 必须保证“一行一个类名”，否则 SPI loader 可能把两行粘在一起导致某些 builder 永远加载不到。

## 3. 选对基类：简单节点、决策节点、终点节点

### 3.1 AbstractSimpleNode：单输入单输出

适用：

- 大多数“对输入做一次变换”的节点
- 例：`ValidateNode`

特点：

- 默认要求至少一个输入（否则会抛 `FlowRuntimeException("节点没有输入值")`）
- 默认只有一个输出端口（由 `AbstractNode` 默认值决定）

代码：

- [`AbstractSimpleNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractSimpleNode.java)
- [`ValidateNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ValidateNode.java)

### 3.2 AbstractDecisionNode：分支/选择（Decision 输出）

适用：

- 输出端口之间互斥的分支节点
- 需要表达“命中/未命中”并传播跳过语义

特点：

- 输出类型是 `Decision` 列表：命中输出端口用 `Decision.satisfied(value)`，未命中用 `Decision.unsatisfied()`
- 默认 `supportsSelectiveOutputMetadata()` 返回 true
  - 会触发运行时对 `exclusiveGroupId` 的互斥校验（详见 4.4）

代码：

- [`AbstractDecisionNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java)
- [`Decision`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/Decision.java)
- 参考实现：[`SwitchNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/SwitchNode.java)

### 3.3 AbstractTerminalNode：终点（成功终点候选）

适用：

- 作为流程的成功收口点（例如 `ReturnNode`）

关键点：

- 终点节点可以合法拥有 **0 个输出端口**（这不是错误，属于设计允许范围）。
- 但它必须是“成功终点候选”才能通过静态分析收口，否则会被当成 invalid leaf（以 core 校验为准）。

代码：

- [`AbstractTerminalNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractTerminalNode.java)
- [`ReturnNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java)

## 4. 端口与连线：最容易踩坑的地方

### 4.1 元数据端口 vs 运行时端口（补齐规则）

`FlowNodeMeta` 中的 `inputPorts/outputPorts` 只是持久化元数据；运行时端口数量会被 `AbstractNode` 补齐：

- 会考虑：
  - meta 里声明的端口数量
  - 连线中出现的最大端口 index（`maxIncomingPortIndex/maxOutgoingPortIndex`）
  - 节点的 `min*PortCount/max*PortCount`

因此：

- “没有在 meta 里声明端口”不等于“运行时没有该端口”。
- 但 **maxPortCount** 仍是硬边界，越界连线会被 `DefaultFlowModel` 拒绝。

代码：

- [`AbstractNode.getInputPorts/getOutputPorts`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)
- [`DefaultFlowModel` 端口越界校验](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)

### 4.2 允许 0 输入的节点：minInputPortCount

如果节点是“数据源”或“常量生成器”，可以允许 0 输入：

- 重写 `minInputPortCount()` 返回 0
- 并在执行时忽略 inputs

参考：

- [`ConstNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ConstNode.java)
- [`DataModelNode`](../../ouroboros-logicflow-data-model/src/main/java/com/ouroboros/logicflow/nodes/DataModelNode.java)：从配置解析模型引用，允许没有输入端口，也兼容已有的显式输入连线。

### 4.3 允许 0 输出的节点：终点/纯消费节点

如果节点是终点或纯消费节点，可以允许 0 输出：

- 重写 `minOutputPortCount()` / `maxOutputPortCount()` 返回 0

参考：

- [`ReturnNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java)

### 4.4 selective-output 与 Decision：不要错误 opt-in

当前的 selective-output 互斥契约依赖一个事实：

- 节点的输出列表中，`Decision.unsatisfied()` 表示“该端口未命中”
- 运行时互斥校验会统计同组 `exclusiveGroupId` 下“命中”的输出数量，>1 则失败

实现上：

- `AbstractDecisionNode` 默认 opt-in `supportsSelectiveOutputMetadata() == true`
- 普通节点默认不 opt-in

代码：

- [`FlowNode.supportsSelectiveOutputMetadata`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNode.java)
- [`AbstractDecisionNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java)
- 运行时互斥校验：[`FunctionalFlowCompiler.validateSelectiveOutputContract`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)

重要提示：

- **不要**在普通节点上随意把 `supportsSelectiveOutputMetadata()` 改成 true。
- 因为对非 `Decision` 输出，运行时会把它视为“已命中”，可能导致同组输出永远冲突。
- 如果你确实要让非决策节点拥有“选择性输出”，需要先明确契约（输出如何表达未命中）并配套静态/运行时校验，再做演进。

## 5. 建模侧：NodeDefine JSON（editor 能建、能画、能脚手架）

NodeDefine 由 dev 模块聚合输出（从 classpath 的资源目录读取），因此新节点必须提供配套 JSON：

- 目录：`src/main/metadata/`
- 文件名：通常与节点类同名并使用 `.flow-node.json` 后缀（非强制，但建议一致）

参考：

- [`ReturnNode.flow-node.json`](../../ouroboros-logicflow-authoring/src/main/metadata/ReturnNode.flow-node.json)
- `logicflow-authoring`、`logicflow-data-model`、`logicflow-extra-nodes` 均可提供该目录下的 NodeDefine 文件

NodeDefine 至少建议包含：

- `type`：必须与 `FlowNodeMeta.type` 匹配
- `label/group/order`：用于面板与排序
- `scaffold`：用于“拖拽创建节点”时生成初始 meta（包含端口元数据与必要的 extra props）
- `portRules`：用于 editor 层限制端口数（与 core 的 min/max 保持一致）
- `editorCapabilities`：入口/出口/错误入口等能力开关（用于减少误用）

## 6. 测试：每个新节点都要有“语义保护”

建议最少覆盖两类测试：

1. core 单测：节点执行与端口语义（含 min/max 端口规则与 meta 补齐行为）
2. 集成用例：至少能被 `DefaultFlowModel` 构造、能被 `FunctionalFlowCompiler` 执行（必要时用 fixture JSON）

稳定验证入口：

- `mvn test --file core/ouroboros-logicflow/ouroboros-logicflow-core/pom.xml`
- `mvn test -pl :ouroboros-logicflow-dev -am`
