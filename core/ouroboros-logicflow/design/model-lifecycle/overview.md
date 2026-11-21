# LogicFlow 模型生命周期与解析（MetaProvider/Provider/覆盖策略）深化说明

更新日期：`2026-11-19`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“模型提供/覆盖策略/集成边界”的深化说明，目标是把以下问题一次性讲清楚：

- 逻辑流模型（FlowModelMeta）从哪里来，如何被选择（高优先级覆盖低优先级）
- `isEnabled`、`isValid`、`validationState` 各自的职责边界是什么，为什么要解耦
- 运行时 `FlowModel` 如何被构造与缓存，刷新时如何切换
- 为什么“高优先级模型即使校验失败也不回退”，以及这在代码里如何保证

本文件第 1–7 节与第 9 节描述当前实现的行为契约；第 8 节为 Deferred。第 9 节的独立刷新已在原 runtime 与生命周期发布边界实现，运行时管理模块通过该合同应用覆盖。

核心入口：

- Meta 层：[`FlowModelMetaCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMetaCenter.java)
- Model 层：[`FlowModelCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelCenter.java)
- Runtime provider：[`DefaultLogicFlowProvider`](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/DefaultLogicFlowProvider.java)
- App meta provider：[`AppLogicModelMetaProvider`](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/AppLogicModelMetaProvider.java)
- dev 保存/诊断快照：[`LogicModelDevelopServiceImpl`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImpl.java)、[`LogicFlowValidationSnapshotSupport`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java)

## 1. 两层提供者：MetaProvider vs Provider（不要混）

LogicFlow 的“模型生命周期”分两层：

1. **Meta 层（持久化的 FlowModelMeta）**
   - 由 `FlowModelMetaProvider` 提供
   - 通过 `FlowModelMetaCenter` 做“覆盖策略选择”和“全集聚合”
2. **Runtime 层（可执行的 FlowModel）**
   - 由 `FlowModelProvider` 提供
   - 通过 `FlowModelCenter` 做“覆盖策略选择”和“运行时取用”

这种分层的意义是：

- Meta 层可以来自多渠道（文件、数据库、远端、低代码仓库）
- Runtime 层可以有不同构建方式（提前构建、按需构建、热刷新）
- 覆盖策略只需要在中心类里定义一次，避免散落在业务代码中

接口定义：

- [`FlowModelMetaProvider`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMetaProvider.java)
- [`FlowModelProvider`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelProvider.java)

## 2. 覆盖策略：高优先级覆盖低优先级，但只看 isEnabled

平台基本策略是：

- 同名模型：高优先级覆盖低优先级
- 高优先级模型禁用后，回退到低优先级模型
- 高优先级模型**校验失败**也不自动回退（否则会掩盖问题）

因此在 LogicFlow 里必须保证：

- “选择哪个模型”只看 `isEnabled`
- “模型是否可执行”由 `isValid/getErrors` 决定（执行时 fail-closed）
- “校验是否通过”的快照只用于诊断展示（validation snapshot），不能驱动启用/禁用

这条策略的落点可以在单测里直接看到：

- [`FlowModelCenterPrioritySelectionTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelCenterPrioritySelectionTest.java)
  - 高优先级 enabled 但 invalid：仍然被选中（不回退）
  - 高优先级 disabled：回退到低优先级
  - `getFlowModelMap()` 同名保留高优先级

## 3. Meta 层：FlowModelMetaCenter 的选择与聚合

### 3.1 单个模型：provider chain（优先级 + enabled gate）

`FlowModelMetaCenter` 构造了一条 provider chain：

- 按 provider 优先级顺序逐个尝试 `getFlowModelMeta(name)`
- 若拿到的 meta `isEnabled == true`，立即返回
- 否则继续 fallback 到下一个 provider

核心代码：

- [`FlowModelMetaCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMetaCenter.java)

这一条链路体现了策略底线：

- disabled 的高优先级模型不会遮挡低优先级模型
- enabled 的高优先级模型会遮挡低优先级模型（无论它是否 valid）

### 3.2 全量模型：getFlowModels 的聚合（只保留 enabled）

`FlowModelMetaCenter.getFlowModels()` 会聚合所有 providers 的 map：

- 按 sorted providers 顺序 reduce
- 对每个 meta：只有 `isEnabled == true` 才放入结果 map

因此：`getFlowModels()` 返回的是“**当前启用的 meta 集合**”，它不是“全部 meta（含禁用）”。

## 4. Runtime 层：DefaultLogicFlowProvider 如何构造与刷新 FlowModel

### 4.1 为什么需要 runtime provider

`FlowModelMeta` 只是持久化结构；运行时执行需要：

- 节点构造（NodeFactory + NodeBuilder/SPI）
- 连接绑定（source/target）
- 静态分析与 fail-closed
- 编译执行函数（FunctionalFlowCompiler）

这些都由 `DefaultFlowModel` 完成。

### 4.2 DefaultLogicFlowProvider 的缓存策略（按需 + 批量刷新）

`DefaultLogicFlowProvider` 维护一份只包含完成态模型的已发布 `flowModelMap`：

- `getFlowModel(name)`：
  - 先查已发布快照；刷新期间仍可取得上一代已有模型
  - 只有当前 metadata 代已经完成 Model 发布时，缓存 miss 才允许按需构建
  - 按需模型以 copy-on-write 方式形成新的不可变快照
- 刷新事件到来时，会私下批量构建候选模型，完成后一次性替换已发布快照

核心代码：

- [`DefaultLogicFlowProvider`](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/DefaultLogicFlowProvider.java)

### 4.3 刷新事件：Refreshing vs Refreshed（两段式）

provider 监听三类事件：

- `OuroborosMetadataRefreshingEvent`：
  - 在 metadata provider 开始替换来源前，把快照标记为 refreshing
  - 保留上一份完成态快照，但禁止缓存 miss 从尚未提交的新 metadata 按需构建
- `OuroborosModelRefreshingEvent`：
  - 创建全部候选流程壳和对应初始化器，不替换已发布快照
- `OuroborosModelRefreshedEvent`：
  - 在可重入同步边界中临时解析同批次流程壳并执行所有延迟初始化器
  - 全部成功后原子发布不可变快照；失败时恢复上一份完成态快照，并保持 miss 不可见

这一点的意义是：

- 两阶段构建仍允许 `CallFlowNode` 在初始化时解析同批次稍后创建的流程壳
- 候选模型只对持有 provider 可重入锁的初始化线程可见，外部读取不会观察到半成品
- 首次发布前、刷新期间和刷新失败后，按需路径都不能绕过完成态发布边界

平台生命周期过滤器仍可阻挡刷新期间的外部请求，但模型完成态正确性不再依赖该过滤器。

### 4.4 为什么 DefaultLogicFlowProvider 要有 SingletonHolder（SPI 与 Spring 的接缝）

`FlowModelCenter` 从 Java SPI 加载 `FlowModelProvider`，但 `DefaultLogicFlowProvider` 自身是 Spring Bean。

因此采用了一个桥接方式：

- Spring Bean 构造时把自己写入 `SingletonHolder.INSTANCE`
- SPI 实际加载的是 `SingletonHolder`（一个静态代理），它把调用转发给 Spring Bean 实例

SPI 文件：

- `core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services/com.ouroboros.logicflow.model.FlowModelProvider`

## 5. FlowModelCenter：运行时取用的唯一入口（覆盖策略落点）

### 5.1 getFlowModel：只看 isEnabled，不看 isValid

`FlowModelCenter.getFlowModel(name)` 做的事情非常克制：

- 依次调用 providers 的 `getFlowModel(name)`
- 取到 `Optional.present` 的 model 后，再过滤 `FlowModel.isEnabled`
- 第一个满足条件的就是最终模型

核心代码：

- [`FlowModelCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelCenter.java)

这与平台策略对齐：

- enable 决定“覆盖关系”
- valid 决定“是否可执行”，但不影响覆盖关系

当模型 invalid 但 enabled：

- `FlowModelCenter` 仍会选中它
- `DefaultFlowModel.execute()` 会 fail-closed（返回 `FlowDesignException`）
- adapter 或调用方会看到显式错误，而不会悄悄跑到低优先级模型

### 5.2 getFlowModelMap：同名只保留一个（高优先级胜出）

`FlowModelCenter.getFlowModelMap()` 负责给“模型列表”使用：

- 遍历 providers 的 `getFlowModelMap()`
- 只把 enabled 的模型放入结果 map
- 同名时用 `putIfAbsent`，保证先出现的（更高优先级）被保留

这一点也有单测覆盖：

- [`FlowModelCenterPrioritySelectionTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelCenterPrioritySelectionTest.java)

## 6. AppLogicModelMetaProvider：从 app home 读取逻辑流模型

当前 runtime 提供了一个默认 meta provider，从应用目录加载低代码模型：

- path：正式后缀 `{appHome}/logic-flow/**/*.logicflow.json`，兼容旧 `.json`；先加载旧格式再加载正式后缀，同名模型由正式后缀覆盖（名称大小写不敏感）。
- event：监听 `OuroborosMetadataRefreshingEvent` 触发 refresh

核心代码：

- [`AppLogicModelMetaProvider`](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/AppLogicModelMetaProvider.java)

注意：

- 它本身不负责覆盖策略（只是一个来源）。
- 是否启用的 gate 由 `FlowModelMetaCenter` 负责。

## 7. 典型调用方的行为：adapter 与 callFlow

### 7.1 Web adapter：不回退，直接显式报错

`HandlerFunctionAdapter` 委托 `LogicFlowExecutionFacade`。默认实现经 `FlowModelCenter` 选模、检查设计错误并执行，缺失或 invalid 返回 gate failure；adapter 将其映射为 bad request。高优先级 invalid 模型不会被低优先级模型掩盖。详见[Adapter 与事务边界](../adapter-and-transaction-boundaries/overview.md)。

### 7.2 CallFlowNode：子流程解析发生在节点构造期

`CallFlowNode` 在节点构造时解析 `flowName -> FlowModel`：

- 解析失败会在节点 errors 中记录（模型因此 invalid）
- 执行时直接调用持有的 `flowModel.execute(...)`

入口：

- [`CallFlowNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/CallFlowNode.java)

这意味着（这是“已实现行为”，不是缺陷判定）：

- 子流程的解析结果与当前 `DefaultFlowModel` 的初始化时刻一致
- 如果需要“每次执行都重新解析子流程以支持热更新”，那会引入新的语义与性能开销，应作为独立能力评估（Deferred）

## 8. Deferred：可能值得做，但必须谨慎推进

以下方向属于“可能有价值，但需要显式设计与测试”的增强点：

1. **子流程解析的动态化**
   - 现在解析在构造期完成；动态化需要定义刷新与一致性语义。
2. **FlowModelCenter 缓存/热刷新策略收敛**
   - 当前 provider 自己维护缓存；是否要在 Center 层收敛仍需评估。
3. **禁用模型的可观测性**
   - `getFlowModels()` 当前只返回 enabled；如果列表页需要“展示禁用的同名模型”，需要另一个 API/聚合口径。

## 9. 逻辑流独立刷新

运行时管理保存或撤销覆盖时，独立重建逻辑流全集，不重放 Context、Metadata、Model 通用生命周期，不刷新数据模型、业务模型投影、菜单或路由。独立刷新固定上次成功完整 Context 所用的默认元数据目录和当前稳定引用目录，通过原 Meta Center 的快照选择合同合并一致的持久覆盖；不扫描交付目录或附带应用其他元数据变化。启动、部署及依赖合同变化仍使用完整 Context 刷新。

独立入口沿原 Provider、Factory 和完成态发布点实现，不增加模型注册表或执行器。全部父子模型同批构造，保留 `CallFlowNode` 构造期绑定语义；校验失败保留旧完成态，明确显示持久目标尚未应用，不自动删除覆盖或回落默认。模型、准确元数据、来源、默认目录及发布证明同一次替换，按需补建不得从待发布覆盖绕过检查。

构建持有原稳定上下文读边界，不占用 Provider getter 的长时互斥；发布前释放读边界，再在原发布锁的短时写边界内检查上下文代次、原流程快照和候选依据，满足条件才切换。完整刷新、独立刷新和按需补建共享条件发布点；禁止读锁升级和过期候选覆盖新完成态。切换可能等待已有稳定业务调用退出，不承诺零等待。

独立构建失败、发布条件不符或等待超时，不改变完整 Context 的失败状态，不发送全局启动失败事件；旧模型与管理 HTTP 在原 Context 可用时继续服务，可重试或条件回退。完整 Context 已失败时，独立刷新不能恢复它，仍须完成一次成功的完整刷新。发布证明绑定实际捕获的覆盖代次与默认目录，不将“已保存”或读取到最新数据库版本当成已生效。

正式合同为 `FlowModelRefreshSupport`、`FlowModelPublication`、`FlowModelCatalogue` 与 `FlowModelBuildReport`，由原 `DefaultLogicFlowProvider` 实现。引用报告来自 Factory 实际解析经过的 `NodeReferenceCatalog`，用于父流反向影响闭包及跨流环路诊断。来源观察为可选合同；未声明来源的 Provider 可以继续执行，但运行时管理将其视为只读。

运行时管理由 [ability 模块](../../../../ability/ouroboros-logicflow-runtime-manage/README.md)装配。父子流同代、并发读取、完整刷新竞争、候选失败保留旧态及重试已有行为测试；这些验证不代表目标环境已启用或发布。
