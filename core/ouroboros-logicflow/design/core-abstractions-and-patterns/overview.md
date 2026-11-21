# LogicFlow 核心抽象与设计模式（Core Abstractions / Design Patterns）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 场景走查：[`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md)
>
> 源码地图：[`DESIGN-CONCEPT-TO-SOURCE-CROSSWALK.md`](../concept-to-source-crosswalk/overview.md)

这份文档只聚焦一个问题：

- **LogicFlow 现在到底是由哪些顶层抽象搭起来的，它们之间靠什么模式协作，而不是靠一堆局部 if/else 拼出来。**

它不是运行时细节清单，也不是节点目录；它补的是“抽象骨架层”。

如果说：

- [`DESIGN.md`](../../DESIGN.md) 给的是总览
- [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md) 讲的是装配
- [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md) 讲的是执行

那么这份文档讲的是：

- **这些机制背后复用的抽象分层和模式选择为什么能成立。**

## 1. 先给一句总判断

当前 LogicFlow 的核心结构，不是“一个解释器 + 一堆节点类”，而更接近下面这套组合：

1. **元数据对象**
   - 描述流程长什么样。
2. **运行时对象图**
   - 把元数据装配成可执行模型、节点和连线。
3. **装配与校验管线**
   - 在执行前把坏图拒绝掉。
4. **编译型执行管线**
   - 把合法图编译成可复用的执行函数。
5. **显式信号对象**
   - 用 `Decision`、`FlowOutcome` 这类对象承载“路径是否命中”“流程如何成功结束”等语义。

这也是为什么当前设计能够同时守住三件事：

- public API 尽量稳定
- runtime 语义逐步增强
- editor/dev 不能反向定义 core 语义

## 2. 顶层抽象栈：从持久化模型到运行时对象图

### 2.1 `FlowModelMeta` 与 `FlowModel` 不是同一个层级

最上层先要分清两个对象：

- `FlowModelMeta`
  - 模型元数据，描述节点、连线、入口出口、类型、启用状态等。类中仍有内部兼容诊断字段；正式持久化模型 JSON 会剔除这些字段，校验快照通过独立派生响应信封提供。
  - 代码：[`FlowModelMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)
- `FlowModel`
  - 运行时公开合同，对外提供 `execute(...)`、节点访问、错误列表等能力。
  - 代码：[`FlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModel.java)

这是一条非常基础但必须守住的边界：

- `meta` 负责“描述”
- `model` 负责“执行”

只要这条边界不混，很多问题都会变得容易判断：

- `validationState` 为什么属于派生诊断读面
- `isValid` 为什么是 runtime 事实
- `NodeDefine` 为什么不能替代 runtime truth

### 2.2 `DefaultFlowModel` 是装配器，不是 DTO 包装器

`DefaultFlowModel` 的角色不是“把 meta 包起来”，而是：

- 构造连接
- 构造节点
- 绑定对象引用
- 聚合设计期错误
- 调用静态分析
- 决定是否编译执行函数

代码入口：

- [`DefaultFlowModel.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)

这也是为什么它更接近：

- **Assembler + Validator Pipeline**

而不是：

- 普通的模型实现类

### 2.3 `FlowNodeMeta` 与 `FlowNode` 也是同一套分层

节点层同样是两层：

- `FlowNodeMeta`
  - 描述节点 type、端口元数据、附加属性、启用状态等。
- `FlowNode`
  - 运行时节点合同，负责端口、连线、执行、错误面等。

代码入口：

- [`FlowNodeMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodeMeta.java)
- [`FlowNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNode.java)

这一层同样体现出一个稳定原则：

- **持久化配置不直接等于运行时能力；运行时对象会基于连接、最小端口数、节点基类规则做补齐。**

### 2.4 `FlowConnectionMeta` 与 `FlowConnection` 让图关系保持显式

除了 model 和 node，当前对象图里还有第三类基础对象：

- `FlowConnectionMeta`
  - 持久化的边关系描述，只保存 source/target 与端口索引。
- `FlowConnection`
  - 运行时边对象，持有 sourceNode / targetNode 的引用视图。

代码入口：

- [`FlowConnectionMeta.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowConnectionMeta.java)
- [`FlowConnection.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowConnection.java)
- [`DefaultFlowConnection.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowConnection.java)

这样做的价值在于：

- 图关系不是靠节点在运行时临时反查字符串拼出来
- 连接引用绑定可以在装配阶段一次性完成
- 后续静态分析与执行编译都可以站在显式对象图上工作

## 3. 节点基类体系：模板骨架比单个节点更重要

### 3.1 `AbstractNode` 提供的是“节点通用骨架”

`AbstractNode` 是当前节点体系最重要的抽象基类之一。它统一承载了：

- 节点与 `FlowModel` / `FlowNodeMeta` 的绑定
- incoming / outgoing connection 的运行时视图
- 输入输出端口的补齐逻辑
- `isAsync` / `isEnabled` / `isValid` 这类通用行为
- 设计期错误聚合
- 输出端口元数据合法性校验

代码入口：

- [`AbstractNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractNode.java)

这说明一个关键点：

- **新节点首先是在接入一套骨架，而不是从零发明一套执行协议。**

### 3.2 `AbstractSimpleNode` / `AbstractDecisionNode` / `AbstractTerminalNode` 是能力模板

在 `AbstractNode` 之上，当前又分出几类更具体的模板：

- `AbstractSimpleNode`
  - 把“单输入、单输出”的常见节点形状模板化。
  - 代码：[`AbstractSimpleNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractSimpleNode.java)
- `AbstractDecisionNode`
  - 把“多输出分支节点”的 decision 生成逻辑模板化，并显式 opt-in selective-output 语义。
  - 代码：[`AbstractDecisionNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractDecisionNode.java)
- `AbstractTerminalNode`
  - 作为终点节点的骨架标记层。
  - 代码：[`AbstractTerminalNode.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/AbstractTerminalNode.java)

这几层叠起来之后，节点家族的扩展就不会变成：

- 每加一个节点就重写一套执行边界

而会更接近：

- 在既有骨架上声明“我是哪一类节点”

## 4. 反复出现的模式：当前实现真正依赖的不是名词，而是协作关系

### 4.1 模式一：元数据层与运行时层分离

这是全模块最重要的模式，几乎贯穿所有设计：

- `FlowModelMeta -> FlowModel`
- `FlowNodeMeta -> FlowNode`
- `FlowConnectionMeta -> FlowConnection`

它的价值在于：

- 让 authoring / persistence / runtime 能够分层演进
- 让静态分析能在运行前执行
- 让 editor/dev 能消费配置合同，但不直接支配 runtime truth

如果这层被打破，后面会立刻出现的问题包括：

- editor fallback 被误当正式语义
- runtime 合法性无法与持久化快照分离
- 新语义只能靠“多加几个字段 + 运行时猜测”硬凑出来

### 4.2 模式二：装配器 + 校验管线

`FlowModelFactory.prepare(...).initialize(...)` 负责准备与初始化流程集合，内部调用 `DefaultFlowModel.initialize(...)` 串起校验与编译。不能直接调用其包内构造器替代这条装配链。初始化时还会从定义快照计算 SHA-256 `revision`，供运行绑定核对；未初始化时调用 `getRevision()` 会抛出异常。`revision` 不包含运行诊断快照，也不代表部署已验收。

它不是一步把一切做完，而是按顺序串起：

1. connection 物化
2. node 物化
3. 连接与节点引用绑定
4. 端口边界与节点自身错误检查
5. entry / exit / errorEntry 解析
6. 图级静态分析
7. fail-closed 或编译执行

这套顺序的意义不是“写法整齐”，而是让错误能停在最合适的层：

- 能在构造期发现的，不推到运行期
- 能在静态分析发现的，不推到执行时
- 不合法模型不进入执行编译，而是在执行入口返回固定失败结果

### 4.3 模式三：工厂 + SPI registry，而不是把所有节点写死

`NodeFactory` 与 `NodeBuilder` 构成的是：

- **Factory + Strategy Registry + SPI Extension**

代码入口：

- [`NodeFactory.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeFactory.java)
- [`NodeBuilder.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeBuilder.java)

它们的协作关系是：

- `NodeBuilder` 负责“我是否支持这个 meta，以及如何 build”
- `NodeFactory` 负责“统一扫描、统一匹配、统一后置校验”

这套设计的关键价值是：

- 节点扩展不必改编译器主干
- 内置节点和扩展节点走的是同一条创建通道
- 端口元数据校验可以在工厂出口统一收口

所以现在新增节点时更推荐：

- 新增 `NodeBuilder` + 对齐元数据/校验

而不是：

- 在多个地方塞硬编码分支

### 4.4 模式四：模板方法，而不是每个节点各写一套 execute 协议

当前节点执行层最明显的模式，是 `AbstractNode` 体系里的模板方法：

- `AbstractNode.execute(...)` 统一包裹 `Try`
- `AbstractSimpleNode` 统一把 `List<Object>` 收缩成单输入
- `AbstractDecisionNode` 统一把“分支命中结果”组织成 `Decision` 列表

这带来的好处是：

- 错误处理边界更统一
- 节点家族行为更可预期
- 运行时编译器不需要为每个具体节点写特殊分支

也正因为如此，判断一个新节点设计是否健康时，一个很实用的问题是：

- **它能不能自然落进某个现有模板？如果不能，它到底是在扩展节点能力，还是在偷偷引入新的控制流原语？**

### 4.5 模式五：显式信号对象，而不是隐式布尔旗标

当前运行时有两个特别重要的“信号对象”：

- `Decision`
  - 表达路径是否命中，以及命中后携带什么值。
  - 代码：[`Decision.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/Decision.java)
- `FlowOutcome`
  - 表达流程如何成功结束，并把 `EXIT` 与 `RETURN` 在编译器内部区分开。
  - 代码：[`FlowOutcome.java`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/FlowOutcome.java)

这类设计的价值在于：

- 把“路径命中”“成功终点身份”这类语义显式化
- 避免散落的布尔变量和临时约定慢慢变成不可维护的隐式协议

同时也要明确：

- `FlowOutcome` 是内部信封，不是公开合同
- 对外仍然是 `FlowModel.execute(): Try<Object>`

### 4.6 模式六：fail-closed 边界

当前实现不是“能跑就先跑”，而是明确把 fail-closed 做成结构性边界：

- 模型未启用：`execute()` 直接失败
- 模型不合法：`execute()` 直接失败
- scope 结束但没命中成功终点：按失败处理
- runtime 发现同组互斥输出同时命中：按失败处理

这不是保守，而是为了保持系统的一个核心特征：

- **坏图不能用沉默成功把问题掩盖掉。**

## 5. 这套抽象骨架给后续演进留下了哪些“正确的入口”

### 5.1 如果你要加新节点

优先问：

1. 它能落在哪个现有基类模板上？
2. 它只是在扩展节点能力，还是在引入新的调度语义？
3. 它应该通过 `NodeBuilder` + 元数据 + 静态分析接入，还是必须改编译器？

如果第三个问题的答案是“必须改编译器”，那它大概率已经不是普通节点扩展，而是在碰控制流原语。

### 5.2 如果你要加新语义

优先尝试的演进路径应该是：

- 元数据字段
- 节点能力门控
- 静态分析
- runtime 防御性校验

而不是：

- 先让 editor 表达出来，再让 core 被动兼容

### 5.3 如果你要改 dev / editor

最需要记住的是：

- `NodeDefine` 不是 runtime truth
- scaffold 不是最终端口形状
- 前端 warning 不是执行语义

也就是说，作者态可以帮助表达，但不能反向定义抽象骨架。

## 6. 建议阅读顺序

如果你已经读完 [`DESIGN.md`](../../DESIGN.md)，下一步可以按目标继续：

1. 想理解“模型是怎样被装配和拒绝坏图的”
   - 看 [`DESIGN-MODEL-ASSEMBLY-PIPELINE.md`](../model-assembly-pipeline/overview.md)
2. 想理解“运行时怎样执行、怎样传播 Decision、怎样切入错误流”
   - 看 [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
3. 想理解“节点扩展应该落在哪些抽象点上”
   - 看 [`DESIGN-NODE-EXTENSIONS.md`](../node-extensions/overview.md)
4. 想理解“哪些对象是公开合同，哪些只是内部信号”
   - 看 [`DESIGN-RESULT-CONTRACTS-AND-DIAGNOSTIC-SURFACES.md`](../result-contracts-and-diagnostic-surfaces/overview.md)
