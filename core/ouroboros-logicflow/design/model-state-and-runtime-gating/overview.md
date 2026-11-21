# LogicFlow 模型状态与 Runtime Gating（validationState / isEnabled / isValid）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN-MODEL-LIFECYCLE.md`](../model-lifecycle/overview.md)、[`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md) 和 [`DESIGN-ADAPTER-AND-TRANSACTION-BOUNDARIES.md`](../adapter-and-transaction-boundaries/overview.md) 的一个交叉深化。它专门回答一类非常容易混淆的问题：

- `validationState`、`isEnabled`、`isValid` 到底分别是什么。
- 为什么高优先级 invalid 模型仍会覆盖低优先级模型。
- 为什么 editor 会提示“已保存但存在后端校验错误”，而 runtime 仍可能尝试取到这个模型。
- direct caller、runtime provider、web-adapter 对“有问题的模型”分别如何处理。

这份文档的目标不是重新讲一次生命周期，而是把“模型状态”和“执行 gating”拉成一张更清晰的图，避免这些概念在不同文档里看起来像同一个字段。

## 1. 为什么要把“状态”单独拿出来讲

LogicFlow 现在至少同时存在三类状态：

- **选模状态**
  - 决定高优先级模型是否覆盖低优先级模型
- **诊断状态**
  - 决定保存后如何把后端校验结果反馈给建模者
- **执行状态**
  - 决定一个 `FlowModel` 当前是否真的能执行

如果把这三者混在一起，就会出现常见误判：

- 看到 `validationState=INVALID` 就以为 runtime 不会选中该模型
- 看到 `isEnabled=true` 就以为模型一定可执行
- 看到 `flowModel.isValid()==false` 就以为这个状态已经被持久化回 meta

当前 LogicFlow 的设计恰恰是刻意把这几条线拆开的。

## 2. 三条状态线：当前真实职责分工

当前最核心的状态可以整理成下表：

| 状态 | 所在层 | 当前作用 | 是否持久化 |
| --- | --- | --- | --- |
| `isEnabled` | `FlowModelMeta` / `FlowModel` | 决定覆盖关系、是否允许进入运行时选择面 | 是 |
| `validationState` 与验证状态相关字段 | 建模态/列表页/编辑页诊断读面 | 给建模态展示权威后端诊断 | 否（不属于正式持久化模型 contract） |
| `isValid()` + `getErrors()` | `DefaultFlowModel` | 决定该运行时模型是否可执行、失败原因是什么 | 否 |

这三条线的一个关键结论是：

- **`validationState` 不是 runtime gate**
- **验证状态相关字段不属于正式持久化模型字段**
- **`isEnabled` 不是可执行性证明**
- **`isValid()` 不是持久化模型字段**

## 3. 诊断态：validation 状态相关字段是校验读面，不是 runtime 开关

### 3.1 哪些字段属于 validation 状态相关字段

按当前格式规范，validation 状态相关字段包括：

- `validationState`
- `validationSummary`
- `validationErrors`
- `validationDiagnostics`
- `lastValidatedTime`

这些字段的正式归类与 contract 以：

- [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)

为准。这里强调的是：

- 它们属于建模态诊断读面。
- 它们不属于正式持久化模型 contract。
- 即使读面暂时返回了这组字段，也不应把它们当成 runtime gate 或正式持久化模型事实。

相关入口：

- [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)
- [`FlowModelMetaValidationStateFieldsTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelMetaValidationStateFieldsTest.java)

### 3.2 这些字段什么时候生成

当前 `logicflow-dev` 在保存前会统一做一次 normalize：

- `create(...)` 前归一化
- `update(...)` 前归一化

入口：

- [`LogicModelDevelopServiceImpl`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImpl.java)
- [`LogicFlowValidationSnapshotSupport`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java)

`LogicFlowValidationSnapshotSupport.normalize()` 的当前行为是：

1. develop service 从应用元数据快照和当前工作区构造引用目录，经 `FlowModelFactory` 初始化提交模型
2. 收集 `getErrors()` 中的 `DesignException`
3. 映射为结构化诊断与摘要
4. 生成供建模态消费的 validation 状态相关结果

### 3.3 诊断态不会自动改写 `isEnabled`

当前设计的关键底线是：

- 校验失败只会反映为 validation 状态相关读面出现 INVALID/diagnostics
- 不会自动把 `isEnabled` 改成 `false`

对应测试：

- [`LogicFlowValidationSnapshotSupportTest`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupportTest.java)
- [`LogicModelDevelopServiceImplValidationTest`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImplValidationTest.java)

这正是平台覆盖策略的一部分：

- 如果高优先级模型一改坏就自动停用，会把问题隐藏在低优先级模型下面

## 4. 选模态：`isEnabled` 决定覆盖关系，但不保证能执行

### 4.1 Meta 层只按 `isEnabled` 选

`FlowModelMetaCenter` 的 provider chain 当前只看：

- 是否能拿到 meta
- meta 是否 `isEnabled == true`

入口：

- [`FlowModelMetaCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMetaCenter.java)

因此：

- 高优先级 enabled meta 会挡住低优先级 meta
- 高优先级 disabled meta 会放行低优先级 meta
- `validationState` 在这一层完全不起作用

### 4.2 Model 层也只按 `isEnabled` 选

`FlowModelCenter.getFlowModel(name)` 当前同样只做：

- 从 provider 获取 `FlowModel`
- 过滤 `FlowModel.isEnabled()`
- 取第一个满足条件的模型

入口：

- [`FlowModelCenter`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelCenter.java)

这意味着运行时的“覆盖关系”仍然只看 `isEnabled`。

### 4.3 高优先级 invalid 模型仍然会被选中

这一点已有明确回归保护：

- [`FlowModelCenterPrioritySelectionTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelCenterPrioritySelectionTest.java)

它固定了当前契约：

- 高优先级 enabled 但 invalid：仍然胜出
- 高优先级 disabled：回退到低优先级

这不是 bug，而是为了防止“问题被低优先级模型掩盖”。

## 5. 执行态：`isValid/getErrors` 是 runtime 模型内部事实

### 5.1 `isValid` 来自 `DefaultFlowModel` 构造结果

`DefaultFlowModel` 在 `initialize(...)` 期间完成：

- 节点构造
- 连接绑定
- 静态分析
- 错误收集

然后基于 `errors.size() == 0` 计算：

- `isValid()`

入口：

- [`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)

因此：

- `isValid()` 不是持久化字段
- 它是“这次 runtime 实例化之后”的即时事实

### 5.2 `getErrors()` 是执行层最权威的“为什么不能执行”

当前 `DefaultFlowModel.getErrors()` 返回的是 `DesignException` 列表。

这份列表同时被两类调用方复用：

1. `logicflow-dev`
   - 用它生成 validation snapshot
2. `web-adapter` / direct caller
   - 用它决定是否显式拒绝或 fail-closed

也就是说：

- `validationDiagnostics` 是 `getErrors()` 的派生诊断读面；请求 `includeValidationSnapshot=true` 后在响应信封返回，不写入正式模型文件
- 但 runtime 真正看的是 `getErrors()` 本身

## 6. Runtime Gating：同一个 invalid 模型，在不同调用方看到的反馈并不完全一样

### 6.1 direct caller：`execute()` 自己 fail-closed

如果某个调用方直接持有一个 `DefaultFlowModel` 并调用 `execute()`：

- 若 `isEnabled == false`
  - 返回 `FlowDesignException("流程未启用")`
- 若 `isValid == false`
  - 返回 `FlowDesignException("流程存在错误: ...")`

入口：

- [`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)

这意味着 direct caller 看到的是：

- **执行期 fail-closed**

### 6.2 web-adapter：执行前显式拒绝

Web adapter 通过 `LogicFlowExecutionFacade` 统一执行。默认 Facade 按名称经 `FlowModelCenter` 选模，拒绝空名称、缺失模型及非空设计错误，返回 `LogicFlowExecutionGateException`；adapter 将该失败映射为 HTTP 400。校验通过后才调用模型 `execute()`。统一选模与 gate 的实现位于 `DefaultLogicFlowExecutionFacade`，adapter 不再自己维护 `checkFlowModel`。

### 6.3 这不是矛盾，而是不同调用边界的收口方式

当前两条路径并不冲突：

- runtime 模型本身仍具备 fail-closed 自保护
- adapter 作为平台入口，会更早把 design-time 问题拦在执行前

所以当前正确表述应是：

- invalid 模型在系统里可以被“选中”
- 但不会被“安静执行成功”

## 7. 用户可见面：editor 看到的是诊断态，runtime 处理的是执行态

当前建模态和运行时看到的是同一问题的两个投影：

### 7.1 editor / 列表页

主要消费的是 validation 状态相关读面：

- `validationState`
- `validationSummary`
- `validationErrors`
- `validationDiagnostics`

因此用户看到的是：

- “已保存，但存在后端校验错误”
- 可查看结构化诊断并定位节点

### 7.2 runtime / adapter

主要消费的是：

- `isEnabled`
- `getErrors()`
- `isValid()`

因此系统做的是：

- 是否选中这个模型
- 是否允许进入执行
- 如果不允许，如何显式报错

这正说明当前闭环已经有了两面：

- **诊断面**：帮助人定位问题
- **执行面**：防止坏模型悄悄跑起来

## 8. 当前闭环还没有进化成完整状态机

虽然这套分层已经能自洽，但它仍不是完整的系统级模型状态机。

当前还没有统一回答的问题包括：

1. runtime/provider 是否应显式区分“已加载但 invalid”的模型状态。
2. `FlowModelCenter.getFlowModelMap()` 之外，是否需要“含 invalid/disabled 同名模型”的治理视图。
3. 刷新/重载时 invalid 模型的可观测性和事件反馈是否要独立建模。
4. adapter/runtime 是否需要统一向外暴露“selected-but-invalid”这类状态，而不只是 bad request。

这也是为什么当前更准确的说法是：

- 已经有“闭环雏形”
- 但还没有“统一模型状态机”

## 9. 当前验证保护：哪些事实已经被测试锁住

与这条状态线直接相关的回归保护包括：

- 现有实现对 validation 字段的兼容/clone/旧 JSON 读写行为仍有回归保护
  - [`FlowModelMetaValidationStateFieldsTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelMetaValidationStateFieldsTest.java)
- 保存时 normalize 会保留 `isEnabled` 并写回有序诊断
  - [`LogicFlowValidationSnapshotSupportTest`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupportTest.java)
  - [`LogicModelDevelopServiceImplValidationTest`](../../ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImplValidationTest.java)
- 高优先级覆盖策略只看 `isEnabled`
  - [`FlowModelCenterPrioritySelectionTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelCenterPrioritySelectionTest.java)

这些测试一起锁住的核心事实是：

- `validationState` 只是诊断信息
- validation 状态相关字段不应被误当成 runtime gate
- 覆盖关系仍只看 `isEnabled`
- invalid 模型不会因为保存而被自动停用

## 10. 演进建议：如果以后要补成完整状态机，顺序应该怎么走

如果未来要把这套“雏形闭环”演进成完整状态机，建议顺序是：

1. 先定义运行时最小状态集
   - 例如 `selected / disabled / invalid / executable`
2. 再决定这些状态是否需要在 runtime/provider 层显式暴露
3. 最后再决定 editor、列表页、adapter 如何统一消费这些状态

不建议反过来从页面提示或接口文案倒推状态模型，因为那样很容易把：

- `validationState`
- `isEnabled`
- `isValid`

重新揉回一团。

当前最值得继续守住的底线仍然是：

- **选模只看 `isEnabled`**
- **诊断只由 validation snapshot 表达**
- **执行只由 `isValid/getErrors` 决定是否 fail-closed**

只要这三条线不再混淆，LogicFlow 后续无论继续补 runtime 状态机，还是继续补 editor/adapter 产品化反馈，都会稳得多。
