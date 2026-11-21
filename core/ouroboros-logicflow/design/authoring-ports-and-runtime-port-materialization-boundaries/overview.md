# LogicFlow Authoring 端口声明与运行时端口物化边界（Authoring Ports / Runtime Port Materialization Boundaries）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 端口与数据契约总论：[`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
>
> NodeDefine 与 editor contract：[`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md)
>
> 模型装配：[`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md)

这份文档单独拆的是一条很容易在跨层讨论里被写混的边界：

- `FlowNodeMeta.inputPorts/outputPorts`
- `NodeDefine.scaffold/portRules`
- `FlowNode.getInputPorts()/getOutputPorts()`

它们都和“端口”有关，但不是同一层真相。

如果说 [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) 更像“端口、连接、类型合同”的总论，这份文档更聚焦在：

- **authoring 侧端口声明、editor 侧端口起步规则、runtime 侧真实端口物化之间的边界**

## 1. 先给结论：当前至少有三份“端口真相”，但只有一份是 runtime 真相

当前最值得先记住的结论有五条：

1. `FlowNodeMeta.inputPorts/outputPorts` 是持久化节点声明，不是最终执行端口全集。
2. `NodeDefine.scaffold/portRules` 是 authoring contract，负责“起步”和“编辑体验”，不是 runtime 语义权威。
3. `FlowNode.getInputPorts()/getOutputPorts()` 才是运行时真实端口面；它由 `AbstractNode` 结合 metadata、连线使用到的最大端口索引、以及节点 min/max 端口规则共同物化。
4. `DefaultFlowModel` 的端口越界校验是对“物化后的 runtime 端口数”做校验，而不是只对 raw metadata 长度做校验。
5. 所以“metadata 没写这个端口”“editor 当前只画了这些端口”，都不能直接推出“运行时一定没有这个端口”。

如果只记一句口径，应该记这个版本：

- **authoring 端口声明不等于 runtime 端口真相，runtime 端口由 core 物化。**

## 2. 必须拆开的三层对象

### 2.1 持久化节点声明：`FlowNodeMeta`

`FlowNodeMeta` 里的：

- `inputPorts`
- `outputPorts`

当前表达的是：

- 这份模型保存下来时，节点自身显式声明了哪些端口 metadata

它不直接保证：

- 这就是运行时最终可见的全部端口
- 这就是 editor 拖入时的初始端口形态
- 这就是节点类型允许的端口上下界

所以它更像：

- **持久化端口声明**

而不是：

- **最终执行面快照**

### 2.2 authoring 起步合同：`NodeDefine.scaffold/portRules`

`NodeDefine` 里的：

- `scaffold`
- `portRules`

当前表达的是：

- 节点拖入画布时的默认实例结构
- editor 怎样生成/限制 authoring 侧可编辑端口

它们解决的是：

- 新建节点时先长什么样
- 端口编辑器能不能增删、怎么补齐、展示到什么程度

它们不直接解决：

- runtime 最终会执行哪些端口
- 端口越界在 core 里应不应该判 invalid
- 节点 min/max 端口合同是什么

所以它更像：

- **authoring 起步合同**

而不是：

- **core 端口语义合同**

### 2.3 运行时真实端口面：`FlowNode`

当前真正的 runtime 端口权威入口是：

- `FlowNode.getInputPorts()`
- `FlowNode.getOutputPorts()`

对大多数节点来说，它们最终由：

- [`AbstractNode`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)

动态物化。

这才是后续会被：

- 连线越界校验
- 输入完整性检查
- 类型兼容检查
- 运行时编译执行

真正消费的端口面。

## 3. runtime 端口是怎样被物化出来的

### 3.1 输入端口数量不是只看 metadata 长度

`AbstractNode.getInputPorts()` 当前取的是：

- `meta.inputPorts.size()`
- `maxIncomingPortIndex + 1`
- `minInputPortCount()`

三者的最大值，然后再受：

- `maxInputPortCount()`

上限约束。

也就是说，输入端口数量同时受三类因素约束：

1. 作者显式写了多少个输入端口 metadata
2. 图上的入边已经把哪个端口索引用起来了
3. 节点类型自己要求至少要有几个输入端口

### 3.2 输出端口数量同理

`AbstractNode.getOutputPorts()` 当前取的是：

- `meta.outputPorts.size()`
- `maxOutgoingPortIndex + 1`
- `minOutputPortCount()`

三者的最大值，然后再受：

- `maxOutputPortCount()`

上限约束。

这就是为什么：

- metadata 里只写了 1 个输出端口
- 但图上有连线用到了 port index 2

时，runtime 仍可能先尝试补齐到 3 个输出端口，再决定是否越界。

### 3.3 补齐出来的不是“空壳”，而是最小可运行端口

当 metadata 端口不足时，`AbstractNode` 会补出默认端口：

- 输入端口：`DefaultFlowNodeInPort`
- 输出端口：`DefaultFlowNodeOutPort`

默认行为是：

- 自动生成默认名字
- 默认数据类型为 `any`

所以“补齐端口”当前不是只补一个占位符，而是补一份：

- **最小可运行合同**

### 3.4 0 输入 / 0 输出都可以合法存在

当前节点是否允许 0 输入 / 0 输出，不由 metadata 是否为空决定，而由节点类型自己的：

- `minInputPortCount()`
- `maxInputPortCount()`
- `minOutputPortCount()`
- `maxOutputPortCount()`

共同决定。

因此当前这些结论都成立：

- metadata 里没有输出端口，不等于节点非法
- metadata 里没有输入端口，也不等于节点非法
- `ReturnNode` 或 `TestSinkNode` 这类 0 输出节点可以是完全合法的

## 4. 为什么连线越界是在模型装配期判，而不是在 editor 里猜

### 4.1 `DefaultFlowModel` 的顺序决定了这是一条 core 规则

`DefaultFlowModel.initialize(...)` 当前顺序是：

1. 先构造连接
2. 再构造节点
3. 再把连接与 source/target 节点实例绑定起来
4. 然后校验连线端口是否越界

这条顺序很重要，因为它意味着：

- 节点在构造时就已经能看到全图连线集合
- runtime 端口数已经可以基于连线使用到的最大索引被物化出来
- 端口越界判断天然属于 core 模型装配与校验阶段

### 4.2 越界是对“物化后端口数”判，不是对 raw metadata 长度判

`DefaultFlowModel` 当前检查的是：

- `sourceOutputPort >= sourceNode.getOutputPorts().size()`
- `targetInputPort >= targetNode.getInputPorts().size()`

所以它比较的并不是：

- raw metadata 里声明了几个端口

而是：

- 结合连线索引与 min/max 规则物化之后，runtime 实际能承载几个端口

这解释了两个常见现象：

1. 有些“看起来超出 metadata 长度”的连线并不会 invalid，因为 runtime 会先补齐。
2. 有些连线虽然 metadata 没问题，最终仍会 invalid，因为补齐后触碰了节点 maxPortCount 上限。

### 4.3 这也是为什么无效连线不能被当成前端展示问题

一旦越界成立，它当前会落成 core 的：

- `InvalidConnectionDetectedException`

这说明“端口越界”当前不是：

- 纯 editor 端视觉问题
- authoring 小提示问题

而是：

- **模型装配级 invalid**

## 5. dev/editor 在这条边界上到底负责什么

### 5.1 `scaffold` 负责“起步”，不负责“收口”

当前 editor 在拖入节点或 harness 创建节点时，会优先使用：

- `nodeDefine.scaffold`

这决定的是：

- 新建节点一开始长什么样

但它不决定：

- 最终 runtime 会看到多少端口
- 连线之后是否会被补齐更多端口
- 某些端口是否因为 maxPortCount 而被拒绝

### 5.2 `portRules` 负责“怎么编辑”，不负责“怎么执行”

当前 `portRules` 解决的是：

- `limit`
- `fixed`
- `auto`

这些 authoring 侧规则。

它们会影响：

- editor 里怎样生成/补齐端口
- 用户能否继续增删端口

但它们当前不直接决定：

- `AbstractNode` 的 runtime 端口物化公式
- `DefaultFlowModel` 的越界与 invalid 判定
- 节点执行期到底按哪个端口列表取值

### 5.3 `NodeDefine` 不能被写成 runtime 端口真相

这条线当前必须继续坚持：

- `NodeDefine` 是 authoring contract
- `FlowNodeMeta` 是持久化声明
- `FlowNode.getInputPorts()/getOutputPorts()` 是 runtime 真相

也正因为如此，当前 editor 的正确角色是：

- 消费并展示端口 authoring contract
- 为建模体验做最小补齐与兼容

而不是：

- 自行宣布 runtime 一定有哪些端口

## 6. 这条边界和 selective-output 的关系

### 6.1 `exclusiveGroupId + defaultBranch` 仍然只是端口 metadata

输出互斥字段当前挂在：

- `FlowNodePortMeta`

上。

因此它当然会随着：

- raw metadata 端口
- 或 built-in 节点在 runtime 构造时生成的端口 metadata

一起进入 runtime 端口面。

但这里仍要继续拆开两层：

1. 端口 metadata 有没有被带上
2. 节点有没有显式 opt-in selective-output 语义

### 6.2 editor fallback 仍然只是兼容安全网

当前 editor 可以为了：

- `switch` 等内置节点

做 authoring 侧归一化和兼容补齐。

但这层仍然只是：

- 让 authoring 产物更容易落到 core 已支持的最小合同

不是：

- 让 editor 取代 core 决定 runtime 端口语义

所以这条边界和 selective-output 结合之后，正确口径仍然是：

- **authoring 可以携带 metadata，core 决定 metadata 怎样变成 runtime 语义。**

## 7. 当前最容易犯的四类误读

1. **把 `FlowNodeMeta.inputPorts/outputPorts` 当成最终执行端口全集**
   - 错，因为 runtime 还会基于连线索引和 min/max 端口规则物化。
2. **把 `NodeDefine.scaffold/portRules` 当成 core 端口语义**
   - 错，因为它们当前只解决 authoring 起步与编辑体验。
3. **认为“没有输出端口”天然非法**
   - 错，因为 0 输出节点当前是合法能力形态之一。
4. **认为连线只要 metadata 长度够就一定合法**
   - 错，因为最终仍要经过 runtime 端口物化与 maxPortCount 约束。

## 8. 当前仍未做成什么

当前还没有一套更强的“authoring 端口模型 = runtime 端口模型”的统一系统。

这意味着当前仍未提供：

- 一份可以被 editor 100% 预测 runtime 端口面的独立 port profile
- 一套把 authoring 端端口规则完整下放到 core 的通用合同
- 一条“authoring 端口列表改了，就能无损推出 runtime 行为”的保证

这不是缺陷被忽略，而是当前仍在坚持的边界选择：

- **先让 core 的端口物化与校验规则清楚**
- **再决定是否有必要把 authoring 侧镜像成更强的统一模型**

如果未来要推进这条线，优先顺序也应当是：

1. 先定义 runtime 端口面需要哪些稳定字段
2. 再决定哪些字段值得提升成 authoring 公共合同
3. 最后才考虑 editor 是否要完全镜像这套 runtime 端口面
