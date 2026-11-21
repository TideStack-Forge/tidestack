# LogicFlow 作品化契约（Dev/Editor/测试）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“工具链契约”相关章节的深化说明，重点回答：

- editor 侧到底能做哪些校验，哪些必须交给后端
- dev 侧保存时如何做权威校验与诊断快照（validation snapshot）
- “启用/禁用”和“校验是否通过”在平台覆盖策略下如何解耦
- 跨前后端的集成测试资产应当如何组织，避免合并后丢失

如果你更关心 editor 作为一个嵌入式部件时，`Graph` / DnD / 属性面板 runtime 由谁拥有、何时销毁、哪些 expose/bridge 只是宿主辅助面，请继续看：

- [`DESIGN-EDITOR-EMBEDDING-AND-LIFECYCLE-BOUNDARIES.md`](../editor-embedding-and-lifecycle-boundaries/overview.md)

本文件只描述“当前已落地的契约”与“允许的演进方式”，不讨论未来大改方案（未来方案请放在 `DESIGN.md` 的 Deferred 章节里）。

## 1. 总体原则：谁说了算

为了避免工具链“创造 core 不支持的语义”，这里把权威边界明确写死：

- `logicflow-core` 是语义权威：什么是合法图、什么是合法终点、什么是可执行模型、端口语义如何解释与收口。
- `logicflow-dev` 是权威校验的承载者：保存/更新模型时必须生成后端校验快照，并把诊断信息可视化给用户。
- editor 以用户体验为目标：除“模型名称为空”以外的校验尽量不阻塞保存，但必须能把风险提示和后端诊断呈现出来。
- 测试是契约守门人：凡是跨层契约（core/dev/editor）都必须被至少一条跨栈用例保护。

## 2. 核心数据面：FlowModelMeta 是跨层公共语言

`FlowModelMeta` 是跨层公共语言，但这里要区分“正式流程模型事实”和“工具链诊断读面”：

1. **正式流程模型事实**：节点、连线、入口/出口/错误入口、输入输出类型等。
2. **验证状态相关字段**：后端权威校验的结果摘要与可定位诊断，用于建模态展示与导航。

代码：

- [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)

字段分层与正式格式规范以：

- [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)

为准；本文件不再单独维护第二套 schema。

### 2.1 验证状态相关字段（读面/诊断态）

当前 validation 状态相关字段包括：

- `validationState`: `VALID` / `INVALID`
- `validationSummary`: “第一条错误信息”，用于列表页快速展示
- `validationErrors`: 全量错误 message 列表（面向“人读”）
- `validationDiagnostics`: 结构化诊断（面向“机器定位”，含 nodeNames）
- `lastValidatedTime`: 后端最近一次校验时间戳

注意：

- 这组字段要先被视为一组“验证状态相关字段”，而不是正式流程模型事实字段。
- 它们服务的是建模态展示/诊断，不是运行时开关。
- 它们**不属于正式持久化模型 contract，不写入正式 JSON**。请求 `includeValidationSnapshot=true` 时返回 `{model, validationSnapshot}`；编辑器从信封接收诊断。`validationState` 由诊断、摘要和校验时间推导。

### 2.2 isEnabled 与 validationState 的解耦（平台覆盖策略）

平台的基本策略是“高优先级覆盖低优先级”。为了不让“校验失败”悄悄回退到低优先级模型从而掩盖问题：

- 是否覆盖低优先级模型，仍以 `isEnabled` 为准。
- 校验失败只会反映到 validation 状态相关读面，不会自动把 `isEnabled` 改成 `false`。

这个策略意味着：

- editor 必须能在模型列表和编辑页显式展示“后端校验失败”的状态，并让用户查看后端错误信息。
- 运行时不会因为“校验失败但仍启用”而偷偷切换到低优先级模型，避免问题被隐藏。

该策略的当前实现与诊断信封见[模型状态与 Runtime Gating](../model-state-and-runtime-gating/overview.md)；历史审查仅用于追溯。

## 3. 后端侧：保存即生成权威校验快照（dev）

### 3.1 保存时的 normalize 流程（已落地）

在 `logicflow-dev` 中，保存与更新都会先执行 normalize：

- 创建：`LogicModelDevelopServiceImpl.create(...)`
- 更新：`LogicModelDevelopServiceImpl.update(...)`

核心代码：

- [`LogicModelDevelopServiceImpl`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImpl.java)
- [`LogicFlowValidationSnapshotSupport`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java)

`LogicFlowValidationSnapshotSupport.normalize(...)` 的工作方式是：

1. 由 develop service 按应用元数据快照构造设计时引用目录，经 `FlowModelFactory` 初始化当前模型
2. 读取 `DefaultFlowModel.getErrors()` 的 `DesignException` 列表
3. 由 snapshot support 将已构造模型的错误映射为结构化诊断 `FlowModelValidationDiagnostic`
4. 归一出供列表页/编辑页消费的 validation 状态相关结果

关键点：

- 这是“权威校验”入口，不依赖 editor 的校验结果。
- 这里的校验口径与 `logicflow-core` 的 `DefaultFlowModel` 静态分析完全一致（因为就是构造同一个 core 模型）。

### 3.2 仅阻塞“模型名称为空”（已落地）

当前 dev 模块在 `validateModel(...)` 里只硬阻断一件事：

- 模型名称不能为空

代码：

- [`LogicModelDevelopServiceImpl.validateModel`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/impl/LogicModelDevelopServiceImpl.java)

普通草稿保存的其他结构/语义问题进入 validation snapshot，交由前端以 warning/disabled 的方式呈现。发布准备使用独立的 `validateForPublication(...)`：先归一化并要求 `VALID`，失败返回“关联逻辑流校验失败”；`prepareCreate(...)` 也调用这条发布校验链。草稿可保存不代表可发布。

## 4. 前端侧：保存阻塞与 warning 的分层

### 4.1 保存阻塞（Save Blocking）

editor 侧通过 `validate()` 返回 save-blocking 的 message。当前策略与后端一致：

- 只阻塞“模型名称为空”
- 其他问题不阻塞保存

代码：

- [`registerComponent.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/registerComponent.js)
- [`saveBlockingValidation.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/editorValidation/saveBlockingValidation.mjs)

### 4.2 图校验与 warning（Graph Warnings）

editor 会做图结构与连线合理性校验，并把这些问题以 warning 的方式呈现（不阻塞保存）：

- 防止明显的端口越界、缺失定义、无效连线等造成“保存成功但后端必定 invalid”的糟糕体验。
- 但 editor 不应替代 core 的权威静态分析，更不应把“推断语义”写成“必然正确的语义”。

典型入口（随代码演进可能调整，但原则不变）：

- [`validate.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/validate.js)
- [`LogicFlowPlugin.js`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/LogicFlowPlugin.js)

## 5. 节点定义契约：NodeDefine（dev 输出，editor 消费）

### 5.1 NodeDefine 的角色

`NodeDefine` 是“节点类型目录”的统一输出格式，用于：

- editor 左侧面板展示（label/icon/group/order 等）
- editor 的端口规则与 scaffold（properties/portRules/scaffold/editorCapabilities）
- 终点/异常终点的视觉表达（`isTerminal` / `terminalKind`）

核心代码：

- [`NodeDefine`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java)
- [`LogicflowNodeDefineController`](../../ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/controller/LogicflowNodeDefineController.java)
- [`JarLogicFlowNodeDefineProvider`](../../ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/provider/JarLogicFlowNodeDefineProvider.java)

### 5.2 终点三态（terminalKind）

为避免“所有终点都画成 RETURN”的歧义，NodeDefine 支持：

- `terminalKind = successful`（例如 `ReturnNode`）
- `terminalKind = exception`（例如异常终点节点）
- 未声明则按普通终点或普通节点处理

这套三态的视觉表达与后端“成功终点/错误终点”的语义边界要保持一致；否则会造成建模者误解。

## 6. 端口元数据与 editor 兼容层：不要让兼容层变成新语义

当前 selective-output 的最小语义是 `exclusiveGroupId + defaultBranch`（详见 [`DESIGN.md`](../../DESIGN.md)）。

但工具链需要处理一个现实问题：

- 历史模型可能没有显式写这些字段（或字段不完整）。
- editor 需要对某些内置节点做兼容性归一（normalization），避免旧模型在新 UI 中出现“看似可分支但端口无语义”的割裂体验。

目前 editor 的一部分归一逻辑位于：

- [`outputPortNormalization.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortNormalization.mjs)

这里的底线是：

- 归一只用于“兼容旧模型的显示/编辑体验”。
- core 的语义仍以节点是否显式支持 `supportsSelectiveOutputMetadata()` 为门控。
- editor 不能依赖归一去表达 core 不支持的节点类型或语义，否则会出现“前端可建模、后端无法执行”的断裂。

## 7. 跨栈集成测试：用例资产必须可合并且可复用

跨前后端的契约变更最容易在“本地跑通”但“合并后丢失”。为了避免这种情况：

- fixtures 与 scenario 必须纳入 git（不能依赖 untracked 文件）。
- 不要在夹具里写个人路径、个人仓库地址等信息（避免 PII 与环境耦合）。
- 资产应可被不同模块的 E2E 测试复用（保持泛化），但允许动态调整测试栈（例如启动第三方容器）。

当前逻辑流的跨栈用例示例：

- `testing/integration/feature/lowcode/logicflow-validation-diagnostics/`
  - [`scenario.yaml`](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml)
  - `contract.md`
- fixtures：
  - `testing/common/fixtures/...`

## 8. 演进规则（不破坏核心设计原则）

当你要增强工具链契约时，建议遵循以下规则：

1. **新增字段优先，避免破坏旧字段语义**
2. **后端先落地权威语义与校验，再让 editor 消费**
3. **任何“兼容层归一”都必须有测试保护，并写清楚它的作用域**
4. **validation snapshot 的目的只有一个：帮助定位问题，而不是驱动运行时开关**
