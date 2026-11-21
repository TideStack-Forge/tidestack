# 审批流定义元数据与图契约

适用基线：当前分支的审批定义 codec 与 Flowable 实现；历史条件求值记录保留其原验证日期。

Activiti6 已移入 `legacy-compat/`，下文涉及它的内容仅说明历史适配或目标兼容边界，不表示主线支持选择该引擎。

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档收口审批流定义在模块边界上的正式数据契约。它回答三类问题：

- 审批流定义文档在顶层应该长什么样。
- 节点、连线、条件、候选人、表单权限这些子结构各自承载什么语义。
- 哪些字段属于正式稳定 contract，哪些只是当前实现仍存在的兼容字段或编辑器扩展字段。

> 注意：本页定义的是正式 contract，不自动等价于仓库里所有现存实现细节。与本页不一致的旧 literal、旧字段名、旧嵌套结构或编辑器临时扩展，统一归入“当前已知漂移”，不能继续当成新功能、夹具或文档的事实来源复制。

## 1. 契约层次

审批流定义当前要分四层理解，而不是把所有 JSON 都当成同一层事实：

1. **入口兼容层**
   - 允许接收历史流程文档和内部 V2 图定义文档。
   - 兼容格式只允许停留在 codec 入口，经归一化后必须转换为正式资源元数据。
2. **正式资源元数据 contract**
   - 设计时保存、运行时管理 API 和 `WorkflowModel.json` 持久化统一使用 `formatVersion = 1` 的 `ApprovalWorkflowDefinitionMetadata`。
   - 这是唯一对外资源合同，也是本页的主体。
3. **内部引擎编译产物**
   - 部署前由 codec 将资源元数据编译为 `formatVersion = 2` 的引擎定义。
   - 其中 `id / name / formType / formKey` 只属于内部引擎适配，不得作为运行时管理元数据字段继续暴露。
4. **编辑器扩展字段**
   - 只服务设计器会话或画布布局，不参与引擎语义。
   - 这类字段可以存在，但不能和正式审批语义字段混成一层。

## 2. 顶层文档 contract

正式资源元数据的字段如下：

| 字段 | 是否必须 | 含义 | 说明 |
|---|---|---|---|
| `formatVersion` | 是 | 资源元数据版本 | 当前固定为 `1` |
| `name` | 是 | 流程定义标识 | 文件名、路由参数和运行时管理均使用该名称 |
| `title` | 是 | 流程名称 | 展示名称，不得在运行时改名为 `name` |
| `description` | 否 | 流程描述 | 说明性文本 |
| `version` | 是 | 定义版本 | 正整数；运行时持久化列必须与此值一致。**缺省时 codec 会填 1**（`ApprovalWorkflowDefinitionMetadata` 字段初始值），即"漏写 version 的静态资产会被当作 version=1 接受"，不会报错 |
| `enabled` | 是 | 是否启用 | 显式 `false` 的定义可管理、不可部署。**缺省时 codec 会填 `true`**，同上 |
| `form` | 是 | 关联表单 | 固定包含 `formType / formId`，运行时不得展开或改名 |
| `notification` | 否 | 流程级通知模板绑定 | 缺失时不触发通知 |
| `editorLayout` | 否 | 编辑器布局 | 不参与引擎语义 |
| `nodes` | 是 | 节点列表 | 只承载显式持久化节点 |
| `edges` | 是 | 连线列表 | 统一使用 `SEQUENCE` 边表达图关系 |

最小示例：

```json
{
  "formatVersion": 1,
  "name": "purchase-approval",
  "title": "采购审批",
  "description": "",
  "version": 1,
  "enabled": true,
  "form": {
    "formType": "business-model",
    "formId": "demo.purchase"
  },
  "notification": {
    "taskCreatedTemplate": "tpl-task-created",
    "taskTimeoutRemindTemplate": "tpl-task-timeout-remind",
    "instanceCompletedTemplate": "tpl-instance-completed",
    "instanceRejectedTemplate": "tpl-instance-rejected"
  },
  "nodes": [],
  "edges": []
}
```

### 2.1 顶层字段的长期约束

- `name / title / version / enabled / form / nodes / edges` 构成定义文档的核心骨架。
- `form.formType / form.formId` 是流程定义自己的正式绑定关系，不能被顶层 `formType / formKey` 或任何页面级摘要字段替代。
- `notification` 只绑定通知模板 key，不承载通知渠道、收件人或渲染配置；字段整体缺失、某个模板字段缺失或模板 provider 找不到该 key 时，运行时静默跳过，不影响流程流转。
- 顶层不应再引入第二套“ownerModel / boundBusinessModel / bindingContext”之类的平行概念。
- `color`、`editorMeta`、`editorLayout` 之类字段不属于审批语义核心字段；若存在，必须明确归类为扩展字段。

### 2.2 运行时管理格式

运行时管理格式必须完整保留 2.1 的资源元数据字段和名称，只允许追加下列管理字段：

| 字段 | 含义 |
|---|---|
| `status / statusName` | 草稿、已部署、已更新、已下线等管理状态 |
| `processDefinitionId` | 当前部署快照指针 |
| `source / adopted` | 定义来源及是否已被实例层接管 |
| `creator / saveAt` | 最近保存人和保存时间 |
| `category / icon` | 运行时管理展示信息 |

运行时管理格式不得再出现 `id`、`flowKey`、顶层 `formType`、顶层 `formId` 或 `formKey`。这些字段分别与 `name`、`form.formType`、`form.formId` 同义，会制造双写和语义冲突。

### 2.3 内部部署编译产物

部署适配器仍可使用内部 `formatVersion = 2` 图定义，其中 `id` 对应资源 `name`、`name` 对应资源 `title`、`formType / formKey` 对应资源 `form.formType / form.formId`。该映射只存在于 codec 和引擎边界，不是设计时或运行时管理 API 合同。

## 3. 图语义 contract

### 3.1 基本结构

- 正式审批定义使用 `nodes + edges` 表达结构化流程图。
- 不再把 `next`、`branches[].first` 这类历史导航字段当成结构化资源图主合同。
- 所有图结构关系统一通过 `edges` 表达。

### 3.2 图级约束

当前正式 contract 要求：

- 全流程必须且只能有一个发起节点。
- 所有节点必须从发起节点可达。
- 当前稳定主链不支持环路。
- 结构化资源图只允许 `SEQUENCE` 边。

### 3.3 分支与汇聚

- 结构化资源图没有显式 split node 概念。
- 任意显式持久化节点只要 `outgoing > 1`，就表示它是分流起点。
- 没有下游显式 OR 汇聚时，多出边默认按 XOR 理解。
- OR 汇聚需要显式 `JoinNode(joinType=OR)` 表达。
- `BRANCH` / `CONTINUE`、`ForkNode`、`GatewayNode` 都不属于稳定资源图 contract。

这一层只定义图语义，不单独再创造 `branch-edge`、`ConditionNode` 或 split helper node。

## 4. 节点 contract

### 4.1 节点通用字段

所有显式持久化节点共享下面这组基础字段：

```json
{
  "type": "节点技术类型",
  "id": "node-id",
  "name": "节点名称"
}
```

按节点类型不同，还会附带各自的配置字段。

三者的语义边界必须分开：

- `type`：持久化 contract 中的稳定技术类型标识，采用英文 `*Node` 命名，不直接使用中文界面文案。
- `name`：这一个节点实例自己的业务名称，例如“经理审批”“财务审批”。
- `displayName`：界面展示用的中文类型名，例如“审批节点”“合并节点”；它是产品文案，不是 `type` 字段的替代写法。

> `next` 是旧结构兼容字段，不属于正式资源图 contract。

### 4.2 稳定节点类型

| `type` | `displayName` | 主要字段 | 说明 |
|---|---|---|---|
| `StartNode` | `发起节点` | `permission` | 全流程唯一入口，名称固定，不承载审批候选人配置 |
| `ApprovalNode` | `审批节点` | `subType`、`candidates`、`permission`、`actions`、`noCandidateMode`、`reAssignUsers`、`allowRejectToSelf`、`notify`、`timeout` | 审批主节点 |
| `HandleNode` | `办理节点` | `candidates`、`permission`、`notify` | 执行型人工办理节点，只支持 complete 办理动作 |
| `CcNode` | `抄送节点` | `candidates` | 抄送动作节点；路过即登记流程可见人员，不产生待办任务 |
| `SubflowNode` | `子流程节点` | `subFlowKey`、`rejectAction`、`inParameters`、`outParameters` | 子流程调用节点 |
| `JoinNode` | `合并节点` | `joinType` | 当前稳定只开放 `OR` |
| `EndNode` | `结束节点` | `outcome` | 表达终止态节点，缺省 outcome 为 `approved` |

`notify` 是人工任务节点的通知开关，仅适用于 `ApprovalNode` / `HandleNode`。字段缺省按 `true` 处理；显式 `false` 时不发送任务创建通知。`CcNode` 本轮不纳入节点级 `notify`，因为它不产生任务事件。

### 4.3 审批节点细化

审批节点当前分两种子类型：

- `单签`
- `会签`

单签节点核心字段：

```json
{
  "type": "ApprovalNode",
  "subType": "单签",
  "id": "approve-a",
  "name": "经理审批",
  "candidates": [],
  "permission": [],
  "actions": ["reject"],
  "noCandidateMode": "rejected",
  "allowRejectToSelf": true,
  "reAssignUsers": [],
  "notify": true
}
```

会签节点额外字段：

```json
{
  "type": "ApprovalNode",
  "subType": "会签",
  "multipleMode": "并行",
  "percentage": 100,
  "rejectAction": "reject"
}
```

当前默认值约束：

- `noCandidateMode` 缺省时按 `rejected`
- `allowRejectToSelf` 缺省时按 `true`
- `notify` 缺省时按 `true`
- `timeout` 缺失时不创建超时策略
- `multipleMode` 缺省时按 `并行`
- `percentage` 缺省时按 `100`
- `rejectAction` 缺省时按 `reject`

审批节点超时策略字段：

```json
{
  "timeout": {
    "duration": "PT2H",
    "action": "remind",
    "transferTo": "user-a"
  }
}
```

约束：

- `duration` 使用 ISO-8601 duration 字符串，例如 `PT30M`、`PT2H`。
- `action` 当前支持 `remind`、`autoApprove`、`autoReject`、`transfer`。
- `transfer` 必须配置非空 `transferTo`；其他 action 不读取该字段。
- Flowable 默认路径已通过非中断 boundary timer 执行超时策略；Activiti6 兼容路径按 capability boundary 记录，不改变定义合同。

### 4.4 合并节点与结束节点说明

- `JoinNode` 的业务语义是“允许多条命中路径在下游显式汇聚”，当前稳定只开放 `joinType = OR`。
- `EndNode` 的业务语义是“显式终止态节点”，不再依赖 synthetic end helper 或隐式尾节点推断来表达产品概念。
- `EndNode.outcome` 是终止结果锚点；缺省按 `approved` 处理，推荐值为 `approved` / `rejected`，自定义值只作为实例读侧展示，不参与业务模型 statusMap 特殊映射。
- “合并节点”“结束节点”是产品显示名，不是持久化 `type` literal。

### 4.5 办理节点说明

- `HandleNode` 是稳定资源图节点，持久化 `type` 固定为 `HandleNode`，中文“办理节点”只是 `displayName`。
- `candidates` 复用现有候选人结构，表达办理待办的候选处理人，不能为空。
- `permission` 复用现有表单字段权限结构，表达办理节点上的表单可见/可编辑边界。
- `HandleNode` 不引入 `actions` / `subType` / `noCandidateMode` / `rejectAction` / `allowRejectToSelf` / `multipleMode` / `percentage` / `reAssignUsers` 等审批专属字段。
- 运行时办理任务只接受 `complete` 动作，不承载 approve / reject / rollback 等审批语义。
- `notify` 复用人工任务节点通知开关语义，缺省按 `true`。

### 4.6 抄送节点说明

- `CcNode` 是稳定资源图节点，持久化 `type` 固定为 `CcNode`，中文“抄送节点”只是 `displayName`。
- `candidates` 复用现有候选人结构，表达的是流程可见人员，不是待办处理人。
- `CcNode` 不引入 `content` / `channel` / `actions` / `permission` / `subType` 等审批或通知字段。
- 流程运行到 `CcNode` 时才登记可见性；未命中的分支不触发登记。

## 5. 连线 contract

### 5.1 连线字段

正式边对象的核心字段如下：

| 字段 | 是否必须 | 含义 | 说明 |
|---|---|---|---|
| `id` | 是 | 连线标识 | 供画布选择、排序和回写使用 |
| `type` | 是 | 连线类型 | 当前稳定 contract 固定为 `SEQUENCE` |
| `sourceId` | 是 | 起点节点 ID | 必须引用存在的节点 |
| `targetId` | 是 | 终点节点 ID | 必须引用存在的节点 |
| `condition` | 否 | 路由条件 | 仅在需要按条件分流时出现 |
| `order` | 否 | 路由顺序 | 用于稳定多出边展示和解释顺序 |

`name` 当前仍在部分实现对象中存在，但它不是稳定审批语义字段，不应作为区分分支语义的正式依据。

### 5.2 路由条件

比较条件：

```json
{
  "left": { "type": "field", "field": "amount" },
  "op": "greater",
  "right": 1000
}
```

组合条件：

```json
{
  "conjunction": "and",
  "children": []
}
```

当前正式操作符命名应使用：

- `equal` / `not_equal`
- `greater` / `greater_or_equal`
- `less` / `less_or_equal`
- `between` / `not_between`
- `is_empty` / `is_not_empty`
- `like` / `not_like`
- `starts_with` / `ends_with`
- `select_equals` / `select_not_equals`
- `select_any_in` / `select_not_any_in`

#### 操作符的右侧取值约定

`right` 的类型决定操作符是否合法，实现侧对不匹配的类型会抛 `FlowDefinitionCodecException`（不静默降级）：

| 操作符 | `right` 形态 | 说明 |
|---|---|---|
| `equal` / `not_equal` / `select_equals` / `select_not_equals` | 标量（字符串或数字） | 字符串按字面量比较 |
| `greater` / `greater_or_equal` / `less` / `less_or_equal` | 数字或可比较标量 | |
| `between` / `not_between` | **两个元素的数组** `[下界, 上界]` | 闭区间；元素按字符串序定位下上界，ISO-8601 日期可用 |
| `is_empty` / `is_not_empty` | 忽略 | 只判 `字段 == null`；空字符串不算 empty |
| `like` / `not_like` | 字符串 | 子串匹配（`contains`），不是 SQL 通配 |
| `starts_with` / `ends_with` | 字符串 | 前缀 / 后缀匹配 |
| `select_any_in` / `select_not_any_in` | **数组** | 判定"字段值属于该集合" |

#### 引擎表达式形态（已用 Flowable 实际求值验证）

条件最终由 Flowable 的 JUEL 求值，**表达式必须能真的算出来**——只断言字符串长相会漏掉下面这类缺陷。
2026-09-12 实测发现并修复了两个叠加的缺陷，合起来使**所有字符串条件恒假且不报错**：

1. **条件对象给字符串右值预加了一层双引号**（`CompareCondition.toExpression`），
   与操作符的字面量引号叠加成 `level == '"特级"'`。这是主因，而且位于操作符**之上**一层——
   只测 `OpEnum` 的用例永远看不到它；
2. 操作符渲染字符串字面量时不加引号，生成 `level.equals(特级)`，`特级` 被 JUEL 当作变量名。

因此约定：**字面量渲染只在操作符一处负责**（`OpEnum.literal`），条件对象只做转发，不得再加工右值。
生成表达式时必须遵守：

- 字符串字面量要带**单引号**并转义内嵌引号与反斜杠；数字/布尔原样。
- **JUEL 不支持 `{a,b}` 集合字面量**（词法错误），**也不支持 `in` 运算符**，
  而 `[a,b]` 在 EL 里是数组、没有 `contains` 方法 ——
  集合判定只能用等值比较的析取链：`(left == 'a' || left == 'b')`。
- `between` 不得就地排序调用方传入的数组。
- 承载形态是 `${expr}`：**不带 `${}` 的裸表达式交给 `createExpression` 会被原样当成字面量返回**，
  既不求值也不报错。判断"条件是否生效"必须看求值结果。

回归防线：`ConditionExpressionEvaluationTest`（flowable 模块，13 个用例，其中 2 个以
`CompareCondition` 为入口专门防上面的第 1 条复发）用与运行时相同的求值路径
（`ProcessExpressionManager` + `MapDelegateVariableContainer` + `Converter.toBoolean`）
覆盖每个操作符的命中与不命中两种取值。

## 6. 子结构 contract

### 6.1 候选人

```json
{ "type": "User", "users": ["u1"] }
{ "type": "Role", "roles": ["manager"] }
{ "type": "Position", "positions": ["director"] }
{ "type": "UserSelect", "mode": "single" }
{ "type": "Starter", "isChecked": true }
```

### 6.2 表单权限

```json
{
  "name": "fieldName",
  "label": "字段名",
  "visible": true,
  "writable": true,
  "required": false
}
```

### 6.3 子流程输入输出参数

```json
{
  "source": "childResult",
  "expression": null,
  "target": "result"
}
```

注意这里的 JSON 字段名是 `expression`，不是 `sourceExpression`。

## 7. 编辑器扩展字段

当前可以接受的设计器扩展字段，应限制在不会改写审批语义的范围内。现阶段最稳定的一项是：

```json
{
  "editorLayout": {
    "nodePositions": {
      "node-a": { "x": 100, "y": 100 }
    }
  }
}
```

长期约束：

- `editorLayout.nodePositions` 只表达节点布局。
- `viewport` 当前不属于稳定持久化 contract。
- 未经正式文档登记的新扩展字段，不应直接作为产品 contract 扩散。

## 8. 当前已知漂移与重点审查项

下面这些差异说明“现有实现”还没有完全收口到正式 contract，后续应逐步治理。已经完成收口的项不再列为 drift，只在“已收口事实”中说明：

### 8.1 已收口事实

- 后端节点 `type` 主链已经统一到 `StartNode / ApprovalNode / HandleNode / CcNode / SubflowNode / JoinNode / EndNode`；中文节点类型名只作为 UI displayName 使用，不再作为稳定入口 alias。
- `TimerNode` 与 `NoticeNode` 已从当前稳定节点合同、后端解析入口、两引擎转译入口、设计器插入/转换入口和稳定夹具中退场；通知触达由后续 `approval-notification-touchpoints` 承接，时间语义由后续 `ApprovalNode.timeout` 承接。
- 审批事件通知触达已收口到现有 `FlowTaskListener` / `FlowInstanceListener` 事件分发链路；流程级 `notification` 与人工节点级 `notify` 由 codec、Flowable / Activiti6 事件创建点和 runtime listener 保留并消费。
- `ApprovalNode.timeout` 已成为审批节点稳定字段；Flowable 默认路径通过非中断 boundary timer 承接 remind / autoApprove / autoReject / transfer，时间提醒复用流程级 `notification.taskTimeoutRemindTemplate`。
- `EndNode` 已经是后端强类型节点，并进入稳定校验与 Flowable / Activiti6 转译边界；`EndNode` 不允许存在出边，正常到达 EndNode 时通过 `outcome` 写入 `__processOutcome` 并投影到实例详情 / 实例事件 / 通知载荷。
- 设计时审批流静态资产已收口为单文件 `workflow/{name}.workflow.json`；静态文件使用 `formatVersion/name/title/version/enabled/form/nodes/edges`，不写入 `source/adopted/status/processDefinitionId/definitionId/id` 等运行态字段。运行时 `WorkflowModel` 使用同名 `version` 保存当前定义版本，并通过 `source/adopted` 区分实例创建与元数据层刷新，未接管记录仅在 incoming 静态 `version` 更高时更新。
- 正式交付链路为“开发平台保存静态文件 → 提交应用元数据版本 → 基座按 `AppContext.homePath` 加载或刷新 → runtime 发现校验并刷新元数据层 `WorkflowModel` → 测试平台显式 deploy 到 Flowable”。保存和刷新都不等于部署，runtime 刷新组件不得调用 Flowable deploy，manage 不再承担 classpath 或 app root 扫描职责。
- 元数据层更新规则已固定：同 `version` 同内容幂等，低 `version` 忽略，同 `version` 不同内容失败；高 `version` 未接管时 `draft` 保持草稿、`deployed/offline/updated` 进入 `updated` 并清空当前运行时部署指针，显式重新部署成功后才写入新的 `processDefinitionId`；已接管实例定制不被静态资产覆盖。
- 稳定资源图测试夹具已经不再把 `edge.name`、旧操作符命名或 `BRANCH/CONTINUE` 当作稳定主合同。
- `editorLayout.nodePositions` 是当前唯一稳定持久化布局扩展；`viewport` 只作为旧输入兼容或会话态读取。
- 前端资源图导出已经不再创建 detached `V2BranchNode`，React Flow 导出统一只写正式节点、`SEQUENCE` 边和扁平通知/子流程字段。
- 未绑定流程在基础信息提交后不再伪造 `form.formType: "default"`；只有明确选中业务模型时才写 `form.formType: "business-model"` 与 `form.formId`。
- 现行画布 drop 入口只允许可见 palette 节点进入，因此隐藏的 `branch` 节点不会从正式交互路径重新回流到 persisted V2 定义。

### 8.2 待治理漂移

1. **编辑态 middle split helper 仍在主链附近**
   - 正式资源图没有 split node / `ConditionNode` / branch-edge 产品概念；
   - 当前编辑器仍有 `分流节点 / 条件节点` 的 middle graph 与 property editor / validation / synthetic rendering 代码；
   - 只要保存出口已经强制 canonicalize，就不直接破坏 persisted contract，但这仍是后续 editor 主链收口的重点。
2. **条件操作符命名以 `greater`、`less_or_equal` 等稳定命名为唯一词表**
   - `greaterThan`、`lessThanOrEqual` 等旧驼峰命名**不再被接受**：`OpEnum.getValue` 对未登记的操作符抛 `FlowDefinitionCodecException` 并列出受支持词表；
   - 此前未知操作符会被静默降级为 `equal`，使 `{"op":"greaterThan"}` 编译成 `amount == 1000`、条件路由走错分支且无异常无日志；
   - 设计器与静态资源必须输出 `equal`/`not_equal`/`greater`/`greater_or_equal`/`less`/`less_or_equal`/`between`/`not_between`/`is_empty`/`is_not_empty`/`like`/`not_like`/`starts_with`/`ends_with`/`select_*` 词表内的取值。
3. **内部 V2 未知字段只在归一化中间态保留**
   - `FlowDefinitionV2Normalizer` 的 V2 分支会保留原始 JSON 上的未知字段（codec.decode 的返回值被丢弃、
     随后 canonicalize 原始 JSON），所以"透传"只在归一化过程中成立。
   - 一旦装进 Java 模型再重新编码（manage 的保存路径就是"解码 → 重编码"），节点/边上的未知字段会被
     `@JsonIgnoreProperties(ignoreUnknown = true)` 丢弃，只有 `editorLayout` 被手工救回。
   - 结论：**持久化即丢弃**。需要长期保留的扩展字段必须显式建模，不能依赖未知字段透传。

## 9. 相关文档

- 图语义专题：[`../gateway-join-semantics.md`](../gateway-join-semantics.md)
- 模块设计摘要：[`../../../../docs/design/modules/core/ouroboros-approval-workflow.md`](../../../../docs/design/modules/core/ouroboros-approval-workflow.md)
- 审批总体行为见[总体需求](../../../../docs/requirements/workflow/overview.md)。
