# ouroboros-approval-workflow 设计文档

## 1. 概述

`ouroboros-approval-workflow` 是平台的审批流引擎模块，负责统一审批流程定义、流程转译、候选人解析、任务操作和流程事件处理。它与 `logicflow` 同属“流程类能力”，但职责更聚焦审批语义和工作流引擎适配。

## 2. 模块结构

聚合 `pom.xml` 显示该模块当前包含：

- `ouroboros-approval-workflow-core`
- `ouroboros-approval-workflow-manage`
- `ouroboros-approval-workflow-engine-testkit`
- `ouroboros-approval-workflow-flowable`
- `ouroboros-approval-workflow-web`
- `ouroboros-approval-workflow-dev`
- `ouroboros-approval-workflow-qb-spring-boot-starter`
- `ouroboros-approval-workflow-dynamic-form`
- `ouroboros-approval-workflow-business-model-form`
- `ouroboros-approval-workflow-runtime`

这说明它内部已经明显拆成流程定义层、引擎适配层、共享测试契约层、管理层、Web 暴露层、设计态层和表单集成层。

生产装配路径只有 `flowable` 一条：

- `ouroboros-approval-workflow-runtime` 依赖 `ouroboros-approval-workflow-flowable`，
  是**完整装配面**（含发起需求解析、静态定义发现与刷新、候选人解析、通知与生命周期）
- `ouroboros-approval-workflow-qb-spring-boot-starter` 是**库式部分装配面**：它只带
  flowable/manage/web/dynamic-form，**不含 runtime**。因此走这条路径时宿主要自行提供
  `StartRequirementsResolver`（否则 web 层的 `ApprovalWorkflowInstanceController` 装配失败）
  以及静态定义发现与刷新；平台自身的装配走 `ouroboros-ability-all-runtime` + runtime 模块，
  不经由该 starter（当前仓库内无任何模块依赖它，只有 `bom` 登记）
- `ouroboros.workflow.approval.engine` 只接受 `flowable`（或留空取默认值）；取其他值由
  `ApprovalWorkflowEngineSelectionVerifier` 在启动期以 `WORKFLOW_ENGINE_NOT_FOUND` 失败
- `ouroboros-approval-workflow-activiti6` 已移出主线并归档到 `legacy-compat/`：它既不在反应堆里、
  也缺少必需的 SPI 实现（`TrustedFlowTaskActionService`），且与主线语义已经分叉。归档原因、
  已分叉的三处语义与迁回步骤见 `legacy-compat/ouroboros-approval-workflow-activiti6/README.md`

`ouroboros-approval-workflow-manage` 负责流程模型与分类管理。

## 3. 核心模型与机制

### 3.1 流程定义与转译

`FlowDefinition` 是审批流程定义根对象，`ApprovalWorkflowTranspiler` 则负责把定义转译到具体工作流引擎实现。这条边界决定了模块能够在保持审批语义稳定的同时，对接不同引擎实现。

当前后端已经在这条边界上引入了版本化定义管线：

- V1 继续使用基于 `next` / `branches[].first` 的 `FlowDefinition`
- V2 新增 `formatVersion = 2` 的 `nodes + edges` 文档格式
- 两种格式都会先经由 `CompositeFlowDefinitionCodec` 归一化为 `StructuredFlowDefinition`
- Flowable 转译、运行时遍历、BPMN 扩展属性读写优先围绕 canonical graph 工作
- manage/web 层在入口允许 V1/V2 输入；为兼容既有设计器，create/update 统一持久化
  `formatVersion = 1` 的正式资源元数据（`ApprovalWorkflowDefinitionMetadata`，契约见
  [`design/definition-metadata-and-graph-contracts/overview.md`](design/definition-metadata-and-graph-contracts/overview.md) §2），
  `formatVersion = 2` 只作为部署前的内部引擎编译产物，不落库
- 读取返回持久化的静态元数据；设计器默认渲染模式已切到 React Flow
- `workflow-diagram-viewer` 等运行时只读视图也已切到 React Flow
- deploy 会在下游调用前把历史定义标准化成 V2，但不会因为纯 deploy 触发数据库回写

这意味着审批流模块内部已经不再把 V1 DTO 视为唯一运行时模型，而是把“多版本定义文档 + 单一 canonical graph”作为后续演进基线。

审批流定义在字段级和图语义级到底承诺什么，不再散落在 controller、设计器实现和测试夹具里各写一份，而是统一收口到 [`design/definition-metadata-and-graph-contracts/overview.md`](design/definition-metadata-and-graph-contracts/overview.md)。后续如果要新增字段、调整节点类型、收紧连线规则或治理兼容漂移，应先更新这份契约文档，再同步实现与测试。

### 3.2 开发到运行时生命周期

审批流定义从开发平台进入测试平台只能走一条正式生命周期：

1. 开发平台编辑定义并保存为应用元数据静态文件 `workflow/{name}.workflow.json`；
2. 开发平台提交并确定应用元数据版本；
3. 基座加载该应用元数据启动或刷新应用实例；
4. `ouroboros-approval-workflow-runtime` 发现静态定义并完成校验、形成元数据层快照。
   来源有**两处**且后者覆盖前者：先扫 classpath/JAR（随依赖包内置的定义），
   再扫 `AppContext.homePath` 下的 `workflow/` 目录（同 id 时以 homePath 为准）；
5. 在模型刷新完成后，runtime 调用 manage 的结构化刷新接口，使元数据层 `WorkflowModel` 生效；
6. 测试平台显式调用 `POST /api/approval-workflow/definition/{id}/deploy`，Flowable adapter 才执行引擎部署；
7. 部署成功后，流程才可发起；旧流程实例继续绑定旧 Flowable 版本。

这条链路中必须严格区分保存、提交、基座加载、发现刷新和部署。刷新只产生或更新运行时 `WorkflowModel`，不得调用 Flowable 部署；保存也不得隐式触发部署。`approval-workflow-manage` 只接收 runtime 传入的结构化定义集合，不扫描 classpath，也不反向依赖应用运行时或应用元数据目录。

元数据层状态规则如下：首次刷新为 `source=metadata-layer`、`adopted=false`、`status=draft`、`processDefinitionId=""`；同 `version` 同内容幂等；低 `version` 忽略；同 `version` 不同内容失败；高 `version` 未接管时 `draft` 保持草稿，`deployed/offline/updated` 进入 `updated` 并等待重新部署；已接管模型不再被静态资产覆盖；删除未接管静态定义时通过既有下线机制阻止新发起，但保留 Flowable 历史部署、历史实例和审计数据。

### 3.3 候选人解析

候选人系统通过 `Candidate`、`CandidateUserResolver` 等抽象，把“谁来审批”从具体流程节点配置中剥离出来，使审批分配逻辑可以扩展和治理。

### 3.4 任务操作与事件系统

`FlowTaskAction` 与流程/任务事件体系负责承接审批任务的执行语义和生命周期观察，是运行时审批行为的关键出口。

### 3.5 动态表单集成

动态表单与业务模型表单分别由 `ouroboros-approval-workflow-dynamic-form` 和 `ouroboros-approval-workflow-business-model-form` 接入；表单承载与审批引擎适配保持独立。

## 4. 运行时链路

该模块维护时应重点把握以下链路：

- 流程部署流程
- 流程实例执行流程
- 候选人解析流程
- 事件处理流程

这些链路共同定义了审批流从设计、部署到运行时执行的完整闭环。

## 5. 维护重点

维护该模块时，应持续守住：

- 与 `logicflow` 的边界，避免退化成通用流程编排集合。
- 与动态表单、组织结构的协作边界，避免把外部能力语义吸收到审批内核。
- 流程定义、引擎适配、候选人解析、任务操作四大面向的分工清晰。
- 新扩展优先沿转译器、候选人解析器、任务动作和事件体系接入，而不是直接穿透到引擎实现层。

## 6. 深入文档

- 自定义表单接入审批流方案（待实施）：[`design/custom-form.md`](design/custom-form.md)
- 定义元数据与图契约：[`design/definition-metadata-and-graph-contracts/overview.md`](design/definition-metadata-and-graph-contracts/overview.md)
- 分支与汇聚语义：[`design/gateway-join-semantics.md`](design/gateway-join-semantics.md)
- 外部流程引擎对接设计（模块内权威设计；Accepted）：[`ability/ouroboros-approval-workflow-external-integration/docs/module-design.md`](../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)
