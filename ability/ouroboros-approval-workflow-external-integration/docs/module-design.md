# ouroboros-approval-workflow-external-integration

本文维护外部引擎路由、适配器合同和已接受取舍。authority=EXTERNAL_ENGINE 表示外部权威执行，authority=OUROBOROS 只表示内置引擎执行。已删除的外部镜像投影路径通过 Git 历史追溯；未来本地待办投影归[企业协作平台集成架构](../../../docs/design/cross-cutting/enterprise-collaboration-integration.md)及待办中心设计。

> 本文同时保留已接受合同、当前实现与待补能力。源码能力以 Registry、provider 端口和下文实现矩阵为准；平台能力比较保留原调研时点，未重新访问供应商接口。安全与权限、凭据、验签、身份映射相关正文按本次约定冻结。

## 结论先行

新增一个位于 `ability` 层的审批流外部对接能力。它不替换 `ouroboros-approval-workflow-core` 中已有的审批语义，也不让钉钉、BPMN 平台或任何第三方 SDK 进入审批流核心。新增能力在现有 `FlowManageService`、`FlowDefinitionService`、`FlowInstanceService` 和 `FlowTaskService` 之上提供一个**运行时路由门面**：

```text
Web / manage
      |
      v
ApprovalWorkflowRoutedFacade
      |
      +-- authority=EXTERNAL_ENGINE + RESULT_ONLY  --> start/result adapter
      +-- authority=EXTERNAL_ENGINE + FULL_PROCESS --> full external adapter
      +-- authority=OUROBOROS ------> built-in engine
      |
      +-- no mapping ----------------> selected built-in adapter
```

每个外部引擎实现必须注册一个 `ApprovalWorkflowEngineAdapterProvider`。运行时发现全部 provider，形成只读的适配器目录；流程映射只保存稳定的 `adapterId` 和外部流程定义引用，不保存 Java 类名或外部 SDK 对象。

外部流程映射有两个一等接入模式：`RESULT_ONLY`（只关注结果）和 `FULL_PROCESS`（全过程接入）。前者是默认模式：系统发起外部实例、保存业务记录与外部实例 ID 的绑定，只通过回调或轮询获取最终结果，不参与外部任务过程；后者才要求把外部实例、任务、表单、动作和结果完整接入现有审批接口。两种模式都由外部引擎作为流程真相源，外部调用失败时绝不能静默回退到 Flowable。

`authority=OUROBOROS` 继续使用内置引擎。需要禁止所有未映射流程启动时，通过 `unmapped-policy=reject` 完成切换；外部调用失败不能修改该规则或回退内置引擎。

## 背景与目标

当前审批流已经把审批定义、引擎转译、流程实例、任务动作和事件分成了稳定接口，主线唯一可用的内置引擎为 Flowable，Activiti6 仅保留在 `legacy-compat/`。外部流程平台通常具有不同的流程定义 ID、用户标识、动作名称、状态码、表单和回调协议，因此不能把外部 API 直接塞进现有引擎实现或 `WorkflowModel` 的 `json` 字段。

本能力解决以下问题：

1. 允许多个外部流程引擎共存，并由流程映射选择具体适配器和具体外部流程定义。
2. 为结果型接入只提供启动、业务记录绑定和最终结果同步；为全过程接入再把系统动作（`approve`、`reject`、`rollback`、`transfer`、`addSign` 等）映射为外部动作，并把外部结果映射回 `FlowInstanceEvent`、`FlowTaskEvent` 和 `TaskState`。
3. 全过程接入保持现有 Web 合同和审批中心调用方式不变；结果型接入不伪造本地任务，调用方使用业务记录、跟踪绑定和外部实例引用查询结果。
4. 支持第三方回调、轮询补偿、幂等和乱序事件处理。
5. 使适配器可以独立发布、独立注册和独立测试，不修改平台核心即可增加钉钉、企业微信、专业 BPM 或自研服务。

## 两种接入模式

### `RESULT_ONLY`：只关注结果（默认）

适用于外部系统负责完整审批过程、Ouroboros 只需要知道最终结果的场景。系统只执行一次外部启动，并持久化以下关联：

```text
businessRecord(scope, businessType, businessId)
        -> resultBinding(mappingVersion, externalInstanceId)
        -> external callback or polling
        -> terminal result mapping
        -> business result event / optional local status update
```

该模式的约束：

- 必须实现 `ExternalWorkflowStarter`，以及 `ExternalCallbackCodec`（HTTP 回调入口或自有接收通道）或 `POLLING` 补偿能力；实例查询（含 `queryChangedInstances` 增量对账）是补偿能力，不要求任务查询。
- 不创建本地 `FlowTask`，不向审批中心承诺待办、表单、审批按钮、转交或加签；`FlowTaskService`、`FlowInstanceService` 的任务操作对该模式返回 `EXTERNAL_CAPABILITY_UNSUPPORTED`。
- 启动请求必须带业务记录的类型、业务主键和幂等键；返回 `trackingId`、外部实例引用和可选外部详情地址。外部实例 ID 只作为外部绑定事实保存，不能写入内置 `processDefinitionId`。
- 结果映射至少覆盖 `RUNNING`、成功、拒绝、取消和异常终态；只关注结果时，只有终态才发布统一结果事件（见“结果归一化与业务回调直连”），过程状态用于查询和超时监控。
- 外部启动成功但响应丢失时只能通过幂等查询、回调和对账恢复，不能再启动一个 Flowable 实例。

### `FULL_PROCESS`：全过程接入

适用于希望在 Ouroboros 审批中心查看外部任务、表单并发起审批动作的场景。外部引擎仍是流程真相源，但系统通过本地 ID 绑定和路由门面完整呈现外部过程：

- 必须实现启动、实例查询、任务查询；要启用本地审批按钮，还必须实现表单和对应任务/实例动作能力。
- 外部任务首次出现时生成 `localTaskId`，外部实例生成 `localInstanceId`；所有 `FlowInstanceService`、`FlowTaskService` 调用按绑定版本路由到外部适配器。
- 映射激活时按适配器能力逐项校验系统动作。缺少 `rollback`、`addSign` 或其他动作时，必须禁用该动作或拒绝激活，不能运行时猜测。
- 回调和轮询用于同步外部状态；本地事件分发器只接收规范化后的结果，不成为第二套状态机。

## 与当前代码事实的对齐

本定稿基于对当前 `core/ouroboros-approval-workflow` 实现的事实核对，后续实现以本节为基线：

- 引擎无关 seam 已存在：`FlowInstanceService`、`FlowTaskService`、`FlowDefinitionService`、`FlowManageService` 与 `FlowTaskAction` 定义在 `ouroboros-approval-workflow-core`；Flowable 是**唯一**可用的内置引擎：`ApprovalWorkflowEngineSelectionVerifier` 对 `ouroboros.workflow.approval.engine` 的任何非 `flowable` 取值在启动期直接抛异常；Activiti6 已移出主线到 `legacy-compat/ouroboros-approval-workflow-activiti6`，不参与反应堆构建。本设计只在其上增加按流程标识的运行时路由。
- Flowable 实现以 `@Service` Bean 形式由 `ApprovalWorkflowFlowableAutoConfiguration` 的 `@ComponentScan` 装配；路由门面以 `@Primary` 暴露时，内部引擎 Bean 必须通过显式 qualifier 或具体类型引用，避免自注入与歧义（桥接规则见 [SPI 治理](../../../docs/design/cross-cutting/spi-governance.md)）。
- 部署链路是显式的：保存/刷新不等于部署，测试平台显式调用 deploy 后才可发起。因此外部映射的存在性验证挂在 deploy 与 activate 两个时机，而不是保存映射时。
- 能力分级已有先例：engine-testkit 的 `EngineCapability`（`STABLE/COMPATIBLE/DEFERRED`）与 `EngineTestAdapter` 已在跨引擎契约测试中运行；本设计的能力枚举与适配器契约测试沿用同一分级思想，不另造概念。
- 规范状态与事件已存在：`TaskState`、`FlowInstanceEvent` 以及 `TaskEventDispatcher` / `InstanceEventDispatcher` 可直接复用为外部结果翻译后的发布通道。
- `WorkflowModel` 已有 `processDefinitionId` 字段；本设计通过新增 `executionMode`、`integrationMode`、`engineAdapterId`、`engineMappingId`、`engineMappingVersion`、`externalDefinitionRef` 字段做兼容扩展，不改变现有读取语义（这些字段是**部署结果快照**，由 `FlowDeploymentResult` 回写；映射表本身不再存模式）。结果型接入不把外部实例 ID 写入 `processDefinitionId`。

## 非目标

- 不把外部引擎的流程模型复制为平台的第二套审批定义语言。
- 不让外部引擎绕过平台的用户、租户、权限和审计规则。
- 不让映射配置承载密钥、Token、Java 类名、脚本或任意 URL 回调逻辑。
- 不在第一阶段承诺把任意外部节点转换成完整的本地设计器节点；只需要最终结果的适配器使用 `RESULT_ONLY`，需要人工跳转的全过程适配器才使用 `LINK` 交互模式。
- 不通过对接模块创建/更新/部署外部流程定义：外部系统是定义作者，平台只引用（引用式对接）。“设计器一键发布到外部平台”属于独立的定义转译与部署能力，不在本设计范围。
- 不删除或重构现有 Flowable/Activiti6 服务；它们先被包装为内置适配器。

## 领域模型与上下文映射

### 术语

| 术语 | 含义 |
|---|---|
| 系统流程标识 (`flowKey`) | Ouroboros 内部流程模型的稳定标识，来自 `WorkflowModel.id`。 |
| 外部适配器 (`adapter`) | 实现本设计接口、负责一个外部流程协议的实现。适配器 ID 必须稳定且全局唯一。 |
| 外部连接 (`connection`) | 某个适配器的一组运行时连接配置，例如钉钉企业应用或 BPM 租户。密钥由统一配置能力管理。 |
| 外部流程定义引用 | `(adapterId, connectionId, namespace, definitionId, definitionVersion)`，指向外部平台中的已发布定义。 |
| 流程映射 (`mapping`) | 把一个系统流程标识绑定到一个外部流程定义，并声明动作、结果和执行模式。 |
| 接入模式 (`integrationMode`) | `RESULT_ONLY` 只启动并同步最终结果；`FULL_PROCESS` 接入实例、任务、表单、动作和结果全过程。仅适用于 `authority=EXTERNAL_ENGINE`。 |
| 交互执行模式 (`executionMode`) | 仅 `authority=EXTERNAL_ENGINE` 的 `FULL_PROCESS` 有意义：`LINK` 表示跳转外部处理，`PROXY` 表示系统代理外部动作。它不能替代 `integrationMode`，也不表达流程真相源；内置执行（`authority=OUROBOROS`）不使用该字段。 |
| 流程真相源 (`authority`) | `EXTERNAL_ENGINE` 表示外部引擎拥有实例状态；`OUROBOROS` 表示本地引擎拥有实例状态。 |
| 外部绑定 (`binding`) | `FULL_PROCESS` 保存本地实例/任务 ID 与外部 ID；`RESULT_ONLY` 保存业务记录与外部实例 ID，均固定映射版本。 |
| 规范动作 | 现有 `FlowTaskService.doAction` 使用的系统动作名称，不随外部平台变化。 |
| 规范结果 | `TaskState`、`FlowInstanceEvent.EventType` 和 `outcome` 的系统状态。 |
| 统一结果事件 (`resultEvent`) | 内部与外部引擎共同发布的规范化流程结果事件；载荷模型为 `FlowInstanceEvent`，经 Spring `ApplicationEventPublisher` 发布（`InstanceEventDispatcher` 为兼容委托）。 |
| 回调绑定 (`callbackBinding`) | 业务模型投影生成的元数据，把流程标识/业务记录类型绑定到回调逻辑流，替代 `callbackUrl` 伪 HTTP 路由。 |

### bounded context 关系

```text
审批流上下文（core）
  FlowDefinition / WorkflowModel / FlowTaskService / FlowInstanceEvent
             ^              规范模型与事件
             |
外部对接上下文（ability）
  Mapping + Routing + Binding + Callback Inbox + Adapter Registry
             |
      Anti-Corruption Layer
             v
外部流程上下文（钉钉、BPM、其他服务）
```

外部对接上下文是审批流上下文的 ACL。外部响应先转换为规范快照和规范事件，外部字段、HTTP 错误码、SDK 类型不得泄漏到 core、manage 或 Web VO。

## 模块结构与依赖

建议新增模块族：

```text
ability/ouroboros-approval-workflow-external-integration-core
  - 适配器接口、规范请求/响应、能力枚举、映射值对象

ability/ouroboros-approval-workflow-external-integration-runtime
  - provider 发现、映射仓储、路由门面、实例/任务绑定、回调入口、补偿任务

ability/ouroboros-approval-workflow-external-integration-web
  - 适配器目录、流程映射配置、验证/激活、回调接收 REST API

ability/ouroboros-approval-workflow-external-integration-dingtalk (首批预置)
  - 钉钉 OA/流程中心协议 ACL、SDK/HTTP 客户端、SPI 注册

ability/ouroboros-approval-workflow-external-integration-bpm (可选)
  - 专业 BPM 协议 ACL、SPI 注册
```

当前首期实现不再单独拆出 `integration-web` 子模块，管理入口、目录查询、映射验证、回调入口和结果跟踪 API 正式落在
`ouroboros-approval-workflow-external-integration-runtime` 的 `runtime.web` 包内。理由是该模块尚未上线，Web 端点与运行时映射、连接解析、
权限守卫和 typed data model 强耦合，拆分会先引入额外 Maven 模块与重复装配边界，而不能减少当前发布风险。后续只有在外部集成 Web
面需要独立发布、独立权限包或跨基座复用时，才重新拆出 `integration-web`；拆分时不得改变现有端点契约或新增兼容双轨。

依赖方向：

```text
external-* adapter -> integration-core
integration-runtime -> integration-core + approval-workflow-core/manage/runtime
integration-web -> integration-runtime + existing web/security/configuration
```

`integration-core` 不依赖 Spring、Flowable、数据库、HTTP 客户端或外部 SDK。`integration-runtime` 可以依赖现有审批流接口和动态数据模型；数据库实现位于 runtime 的最外层。该安排遵守“依赖指向策略、外部细节在外圈”的规则，也避免让 `approval-workflow-core` 反向依赖 `ability`。

## 适配器接口

不要让一个适配器被迫实现所有外部能力。钉钉官方存在两种不同的接入方向：一类是钉钉作为流程真相源，另一类是 Ouroboros 作为真相源、钉钉只承载待办中心。若把两者塞进一个“启动/查询/动作/回调”胖接口，第三方钉钉应用只能在运行时抛出“不支持”，这会把设计错误推迟到生产。这里保留一个最小标识接口，并按能力拆分可选端口：

```java
public interface ApprovalWorkflowEngineAdapterProvider {
  ApprovalWorkflowEngineDescriptor descriptor();

  ApprovalWorkflowEngineAdapter create(ApprovalWorkflowEngineConnection connection);
}

public interface ApprovalWorkflowEngineAdapter {
  Try<ExternalHealth> health();
}

// 现状：定义侧由三个窄端口承担（ouroboros-approval-workflow-external-integration-core）
public interface ExternalProcessDefinitionDiscovery {
  Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request);
}

public interface ExternalProcessDefinitionValidator {
  Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition);
}

public interface ExternalProcessDefinitionFormReader {
  Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition);
}

public interface ExternalWorkflowStarter {
  Try<ExternalStartResult> start(ExternalStartCommand command);
}

public interface ExternalInstanceReader {
  Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance);

  Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query);
}

public interface ExternalRecordReader {
  Try<ExternalRecordPage> queryRecords(ExternalInstanceRef instance);
}

public interface ExternalTaskReader {
  Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query);

  Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task);

  Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task);
}

public interface ExternalWorkflowActionExecutor {
  Try<ExternalActionResult> execute(ExternalActionCommand command);
}

public interface ExternalCallbackCodec {
  Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request);
}
```

接口语义：

- `descriptor()` 只通过 provider 暴露，不从实现类名推断。它声明 `adapterId`、显示名称、版本、规范动作候选集和连接类型档案；端口级能力由 registry 按所实现端口派生，descriptor 不重复声明。
- 定义侧端口已拆分：`ExternalProcessDefinitionDiscovery`（给配置页面提供外部流程定义候选项）、`ExternalProcessDefinitionValidator`（`validateDefinition` 在映射验证和激活前确认定义存在、已发布且版本可用）、`ExternalProcessDefinitionFormReader`（读取定义表单 schema）。原 `ExternalProcessDefinitionCatalog` 已不存在。
- `ExternalWorkflowStarter`、`ExternalInstanceReader`、`ExternalRecordReader`、`ExternalTaskReader` 和 `ExternalWorkflowActionExecutor` 只用于 `authority=EXTERNAL_ENGINE`，分别声明启动、实例读取、审批记录读取、任务读取和动作能力。`RESULT_ONLY` 只需要 starter，实例读取（含 `queryChangedInstances` 增量对账）仅作为补偿；`FULL_PROCESS` 才要求 task reader，只有支持本地代理审批的适配器才实现动作端口。启动命令接收规范的启动人、业务记录、表单、流程变量、幂等键和外部定义引用，并可选携带抄送人（`cc`）与发起时指定/自选审批人（`approvers`/`targetSelectActioners`，仅当外部定义允许覆盖时透传系统候选人解析结果）；动作命令必须带规范动作名、当前用户、评论、表单、payload、幂等键和映射版本。
- `ExternalRecordReader` 承接审批记录/时间线查询（`getTimeLine` 与耗时统计的规范来源）；不实现该端口的适配器不得伪造记录。
- `ExternalTaskReader.getTaskForm` 与 `ExternalProcessDefinitionFormReader` 承接任务表单与定义表单 schema：前者支撑本地审批中心表单快照，后者在启动前校验表单字段映射（控件数量、类型、复合值序列化）。
- ~~`ExternalTaskCenterMirror` 只用于 `authority=OUROBOROS`，负责把本地实例/任务创建或更新到外部待办中心~~（**该端口已于 2026-09-11 删除**；现在没有任何镜像端口，外部快捷操作入口随之移除）。
- `ExternalCallbackCodec` 是唯一允许接触原始 HTTP/SDK 回调的地方；验证签名、时间窗和重放保护后返回规范化事件包列表。批量推送（一次回调携带多个事件）在 codec 内拆分，入口与 inbox 始终按单个事件处理。
- 回调接收有两种形态：平台 HTTP 回调入口（默认）与适配器自有接收通道（长连接、MQ、内部订阅）。后者由适配器模块自建消费者，完成验签与解析后调用 runtime 提供的 `ExternalEventIngestService` 写入 inbox，与 HTTP 入口共用同一套幂等键与重试路径，适配器不得绕过 inbox 直接发布事件。
- 回调订阅的开通方式各平台不同（钉钉/企业微信在管理后台人工配置回调 URL，飞书需调用订阅 API）。适配器负责声明并执行订阅开通/续期/校验，`validateDefinition` 与 `health` 必须包含订阅状态检查；平台只提供回调入口 URL 模板与连接凭据。
- 适配器不直接写平台数据库，不直接发布 `FlowInstanceEvent`，也不决定映射是否生效。Registry 在激活时按 descriptor 检查所需能力，禁止把缺失能力留给运行时。

推荐的 `Capability`：

`DEFINITION_DISCOVERY`、`DEFINITION_VALIDATION`、`DEFINITION_FORM`、`EXTERNAL_START`、`EXTERNAL_START_RECOVERY`、`EXTERNAL_INSTANCE_QUERY`、`EXTERNAL_FINAL_RESULT`、`EXTERNAL_RECORD_QUERY`、`EXTERNAL_TASK_QUERY`、`EXTERNAL_TASK_ACTION`、`EXTERNAL_INSTANCE_ACTION`、`EXTERNAL_INSTANCE_CANCEL`、`CALLBACK`、`POLLING`。

（`MIRROR_*` 已删除；`EXTERNAL_INSTANCE_ACTION`/`EXTERNAL_INSTANCE_CANCEL` 目前无 provider 声明，
属未落地的预留项，详见 `Capability` javadoc 与下文的适配器实现能力矩阵。）

能力集由 registry 按适配器实现的端口**派生**（端口 `instanceof` 判定），descriptor 只声明超出端口表达的细粒度项（能力等级、规范动作候选集、连接类型档案）。若实现类声称支持某个能力但未实现对应端口，启动期校验失败；不允许 descriptor 与端口实现各说各话。`LINK` 交互由 `executionMode` 表达、`externalUrl` 由启动/查询结果携带，不再设置 `LINK_*` 能力。

`authority=EXTERNAL_ENGINE + integrationMode=RESULT_ONLY` 要求 `EXTERNAL_START`、`EXTERNAL_FINAL_RESULT` 以及回调或轮询能力，不要求任务端口；可选声明 `EXTERNAL_INSTANCE_CANCEL` 开放发起人撤销入口——但该能力目前没有任何 provider 声明，撤销入口对所有渠道恒返回“能力不支持”，属未落地的预留项。`FULL_PROCESS` 要求实例/任务查询，启用本地审批按钮时再要求所声明动作对应的 `EXTERNAL_TASK_ACTION` / `EXTERNAL_INSTANCE_ACTION`。`authority=OUROBOROS` 只表示“内置引擎执行”，镜像写入能力与镜像投影路径已删除，不再要求任何 `MIRROR_*` 能力。`LINK`、`PROXY` 只保留为 `executionMode` 的交互值，不能代替 `integrationMode`。映射激活前由能力校验阻止不完整配置。

## Provider 注册与发现

每一种实现都必须同时满足以下注册约定：

1. 实现 `ApprovalWorkflowEngineAdapterProvider`，提供稳定的 `adapterId`。
2. 在实现模块的 `src/main/resources/META-INF/services/` 下创建文件：

   ```text
   com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider
   ```

3. 文件列出 provider 的全限定类名；不得只依赖 `@Component` 或包扫描。
4. `WorkflowEngineAdapterRegistry` 使用现有 `SPI` 工具发现 provider，启动时完成去重、排序和能力索引。
5. `adapterId` 重复、descriptor 不合法、provider 构造失败时启动失败；禁止静默覆盖或依赖 ServiceLoader 顺序。
6. 适配器需要 Spring 生命周期的模块还必须按 [Spring AutoConfiguration 装配模式](../../../docs/design/cross-cutting/spring-autoconfiguration.md) 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册其自动配置，并按现有模块保留 `spring.factories` 兼容声明；服务文件与自动配置缺一不可，避免出现“能发现但无法装配”或“能装配但不可发现”的半注册状态。

适配器需要 Spring 管理资源时，provider 仍然保持无状态，`create(connection)` 内部使用 runtime 提供的 HTTP、配置和时钟端口；不能通过静态全局变量持有 Spring Bean。确实需要 Spring 生命周期的实现可以在 adapter 模块提供独立 AutoConfiguration，把已构造的 provider 通过显式 bridge 注册，但 SPI 服务文件仍然是正式发现入口。

主线内置 Flowable 不进入外部适配器注册表（Activiti6 仅为历史兼容模块）：无映射路径由路由门面经显式 qualifier 直接委托内置 Bean，沿用内置 AutoConfiguration；主线选择校验器只接受 `ouroboros.workflow.approval.engine=flowable`。外部映射只允许指向注册表中的外部适配器，避免把“内置回退”伪装成外部配置。

## 流程映射聚合

`WorkflowEngineMapping` 是聚合根，按作用域、流程标识和版本管理。映射引用外部流程定义，不复制外部流程图。

### 映射领域示意

以下 JSON 表达领域关系，不是管理 API 可直接提交的完整请求体。实际保存字段以 `EngineMappingSaveVO` 和 `WorkflowEngineMapping` 为准，`integrationMode` / `executionMode` 只由 Registry 派生。

```json
{
  "schemaVersion": 1,
  "scope": { "applicationId": "expense-app", "environment": "prod" },
  "flowKey": "expense-reimbursement",
  "adapterId": "example-bpm",
  "connectionId": "bpm-corp-main",
  "externalDefinition": {
    "namespace": "corp-a",
    "definitionId": "PROC_EXPENSE_V3",
    "definitionVersion": "42"
  },
  "authority": "EXTERNAL_ENGINE",
  "actionMappings": [
    { "systemAction": "approve", "externalAction": "agree" },
    { "systemAction": "reject", "externalAction": "refuse" }
  ],
  "resultMappings": [
    { "externalStatus": "RUNNING", "systemState": "PROCESSING" },
    { "externalStatus": "APPROVED", "systemEvent": "APPROVED", "outcome": "approved" },
    { "externalStatus": "REJECTED", "systemEvent": "REJECTED", "outcome": "rejected" },
    { "externalStatus": "CANCELED", "systemEvent": "CANCELLED", "outcome": "cancelled" }
  ],
  "callback": { "mode": "WEBHOOK", "subscriptionRef": "bpm-expense-prod" }
}
```

字段含义和约束：

- `flowKey` 在同一作用域只能有一个 `ACTIVE` 映射；作用域使用现有应用/租户上下文解析，不由调用者随意传入。
- `adapterId` 和 `connectionId` 必须存在；连接凭据只存配置中心引用。
- `externalDefinition` 必须包含稳定 ID；外部版本字段可以为空，但必须保存适配器校验时的 `schemaHash` 和校验时间，禁止隐式绑定“当前最新”。钉钉以 `processCode` 为稳定标识，官方没有承诺统一模板版本号。
- 接入模式与交互模式**不是可填字段**：由 Registry 按适配器实际实现的端口派生（实例查询 + 任务查询 → `FULL_PROCESS`，否则 `RESULT_ONLY`；动作执行端口 → `PROXY`，否则 `LINK`）。`RESULT_ONLY` 不允许配置任务动作、任务表单或本地待办映射，`FULL_PROCESS` 才允许配置这些能力。
- `authority` 是流程真相源的一等字段：`EXTERNAL_ENGINE` 或 `OUROBOROS`；模式是派生结果，禁止用模式单独推断流程真相源。
- `actionMappings` 只能使用平台已经声明的规范动作和适配器声明的外部动作。请求字段映射采用受限路径表达式，不允许执行脚本。
- `resultMappings` 至少覆盖运行中、成功、拒绝、取消和错误终态；未覆盖的外部状态进入 `UNKNOWN` 并触发告警，不得默认为成功。
- 映射保存为不可变版本；编辑 `ACTIVE` 映射会生成新草稿，激活后新流程使用新版本，运行中的外部实例仍绑定旧版本。
- `ACTIVE` 之前必须完成定义存在性、接入模式能力、authority 能力、动作覆盖、结果覆盖、回调/轮询可达性和连接健康检查。

映射状态：`DRAFT -> VALIDATING -> ACTIVE -> PAUSED/RETIRED`，验证失败进入 `INVALID`。`PAUSED` 禁止新流程启动，但允许查询和处理已存在实例；是否允许处理由适配器能力和运维策略决定。

### 结果型映射示例

结果型映射不配置任务动作，也不创建本地待办：

```json
{
  "flowKey": "supplier-onboarding",
  "adapterId": "dingtalk-oa",
  "connectionId": "dingtalk-corp-main",
  "externalDefinition": { "definitionId": "PROC_SUPPLIER_V2", "schemaHash": "sha256:..." },
  "authority": "EXTERNAL_ENGINE",
  "resultBinding": {
    "businessRecordType": "supplier",
    "businessRecordIdPath": "$.supplierId",
    "terminalResultRequired": true
  },
  "resultMappings": [
    { "externalStatus": "APPROVED", "outcome": "approved" },
    { "externalStatus": "REJECTED", "outcome": "rejected" },
    { "externalStatus": "CANCELED", "outcome": "cancelled" }
  ]
}
```

## 运行时路由

### 路由规则

```text
resolve(scope, flowKey)
  -> ACTIVE mapping + EXTERNAL_ENGINE + RESULT_ONLY  => result-only starter/result processor
  -> ACTIVE mapping + EXTERNAL_ENGINE + FULL_PROCESS => full external adapter + mapping.version
  -> ACTIVE mapping + OUROBOROS      => built-in adapter + mapping.version
  -> no mapping          => configured built-in adapter
  -> no mapping + reject => ROUTE_NOT_CONFIGURED
```

执行路由是二分支决策（外部权威 / 内置权威）。镜像投影钩子（`TaskCenterMirrorHook`）与其 outbox 已删除，见文首说明；实例一旦产生 binding，后续实例/任务事件仍必须使用 binding 中保存的 `mappingId`、`mappingVersion` 和 `connectionId` 路由，不得按当前 `ACTIVE` 映射重路由旧实例。

路由结果必须包含 `mappingId`、`mappingVersion`、`adapterId`、`connectionId` 和执行模式，后续所有调用复用同一个结果。禁止在一次流程启动中先查映射、失败后再无条件尝试 Flowable。

发起审批统一进入 `FlowInstanceService.start(FlowStartCommand)`。业务模型 Action、逻辑流 `startProcess` 节点、通用 HTTP 与高代码不选择接入模式；`RoutedFlowInstanceService` 使用同一次解析得到的 ACTIVE 映射，内部委托既有内置、完整外部或结果型执行路径。`RESULT_ONLY` 缺少任务查询能力不构成发起禁令；业务模型前置校验按 `EXTERNAL_START` 能力准入。

统一结果明确表达平台执行引用、真实启动状态、外部跳转与任务查询能力：

```java
public interface FlowInstanceService {
  Try<FlowStartResult> start(FlowStartCommand command);
}
```

`FlowStartResult.executionId` 可以指向内置实例、外部实例绑定或结果跟踪绑定，不承诺存在本地任务。`status` 区分 `STARTING / STARTED / START_UNKNOWN / FAILED`；未知结果继续由已有启动事实和恢复机制处理，不能据返回执行引用伪造 STARTED 事件。旧字符串重载保留为已受理执行引用的兼容投影，失败事实返回失败；通用 HTTP 保留旧 `data` 字符串，并增加 `startResult` 结构化结果。旧结果型 HTTP `/external/result-bindings/start` 也转入同一服务，仅保留请求和响应格式的兼容转换。

`ExternalApprovalStartService` 是存量代码兼容面，其实现把旧命令转成统一命令并调用 `FlowInstanceService`，不再独立解析映射。统一路由将结果型映射交给 `ResultOnlyStartExecutor.startMapped`，复用正式映射、独立启动意图事务、外部事务外调用、租约、绑定、案件、Outbox 与恢复机制；业务记录、回调、表单、自选审批人、抄送和幂等键完整传递。`RESULT_ONLY` 仍不生成伪本地任务，任务操作依据适配器真实能力执行。

`instanceVariables` 中的业务记录、回调、案件、轮次和幂等键属于平台发起上下文。组装外部命令时通过 `FlowInstanceVariables.customVariables` 提取自定义流程变量；平台上下文保存在启动事实、绑定与案件中，业务记录和幂等身份使用外部命令的专属字段。这样飞书、企业微信等不支持自定义流程变量的适配器不会因平台上下文本身而拒绝发起；调用方实际提供的自定义变量仍完整传给适配器，按其真实能力校验，不静默丢弃。

### 对现有接口的接入

`integration-runtime` 提供以下路由门面，并以 `@Primary` 暴露给现有 Web/manage 接口：

- `RoutedFlowManageService implements FlowManageService`
- `RoutedFlowDefinitionService implements FlowDefinitionService`
- `RoutedFlowInstanceService implements FlowInstanceService`
- `RoutedFlowTaskService implements FlowTaskService`

这些门面只做路由、规范模型转换、绑定解析和错误映射。没有映射时调用当前选中的内置适配器；`EXTERNAL_ENGINE + FULL_PROCESS` 映射调用外部 starter/reader/action 端口；`EXTERNAL_ENGINE + RESULT_ONLY` 只调用 starter 和结果读取/回调端口；`authority=OUROBOROS` 映射仍调用内置适配器，不进入执行路由分支（镜像投影钩子已删除）。主线 Flowable 具体 Bean 继续由原 AutoConfiguration 创建，内部引擎代码通过具体类型或明确 qualifier 取得，不会误注入路由门面。

### 部署结果兼容

现有 `FlowManageService.deploy(...)` 返回的字符串语义是内置引擎的 `processDefinitionId`，不能把外部定义 ID 写进这个字段。应在 core 增加兼容扩展：

```java
public record FlowDeploymentResult(
    String executionMode,
    String integrationMode,
    String authority,
    String adapterId,
    String mappingId,
    Integer mappingVersion,
    String processDefinitionId,
    String externalDefinitionRef) {
  public static FlowDeploymentResult builtIn(String processDefinitionId) {
    return new FlowDeploymentResult("BUILT_IN", null, "OUROBOROS", null, null, null, processDefinitionId, null);
  }
}

public interface FlowManageService {
  // 现有方法保持兼容
  Try<String> deploy(String flowKey, String flowDefinitionJson);

  default Try<FlowDeploymentResult> deployForRuntime(String flowKey, String flowDefinitionJson) {
    return deployNewVersion(flowKey, flowDefinitionJson).map(FlowDeploymentResult::builtIn);
  }
}
```

`RoutedFlowManageService.deployForRuntime` 对 `authority=EXTERNAL_ENGINE` 的映射只执行外部定义验证，不调用 Flowable，返回 `executionMode=EXTERNAL` 和 `integrationMode=RESULT_ONLY/FULL_PROCESS`；对 `authority=OUROBOROS` 的映射继续部署/刷新内置定义并保留内置 processDefinitionId。`WorkflowModelService` 使用新结果更新 `WorkflowModel`：外部权威模式不写入内置 `processDefinitionId`，结果型和全过程模式都记录 `integrationMode`。这样不会把“钉钉待办中心同步”误认为已经切换了流程执行引擎。

### ID 与绑定

外部 ID 不能直接冒充内置引擎事实 ID。`FULL_PROCESS` 启动外部流程后：

1. 生成平台 `localInstanceId`，持久化 `(localInstanceId, adapterId, externalInstanceId, mappingId, mappingVersion)`。
2. 外部任务首次出现时生成 `localTaskId`，持久化 `(localTaskId, localInstanceId, adapterId, externalTaskId, mappingVersion)`。
3. 平台接口向调用方只返回 local ID；路由门面按绑定解析外部 ID。
4. 绑定记录保存可选的外部 revision/sequence、最后外部事件时间、最后同步时间和状态，避免回调乱序覆盖新状态。

`RESULT_ONLY` 不生成 `localInstanceId` 或 `localTaskId`，而是持久化 `(businessRecordType, businessRecordId, adapterId, externalInstanceId, mappingId, mappingVersion)` 结果绑定，并返回 `trackingId`。业务服务通过 tracking API 或统一结果事件查询最终状态；不要求钉钉或 BPM 的 ID 格式与平台一致。

因此只有 `FULL_PROCESS` 的 `taskId`、`instanceId` 才保持现有审批中心 URL 合同；结果型接入使用业务记录和 `trackingId` 合同。

### 查询与能力降级

- 按 `flowKey` 读取平台流程定义时，外部映射直接从 `WorkflowModel` 解析，不依赖本地 BPMN 部署。两种外部模式的初始表单都复用平台 `FlowFormService` 与发起节点字段权限；外部任务表单另按适配器实际能力读取，`RESULT_ONLY` 不提供任务表单。
- 按 `instanceId`/`taskId` 的查询先查 binding，再路由到 adapter。
- 用户待办列表需要跨引擎聚合：内置 adapter 和各外部 adapter 分别使用稳定游标查询，路由门面按 `(updatedAt, localId)` 做合并，不能对多个引擎使用 offset 拼接。
- `FULL_PROCESS` 的 `LINK` 交互模式没有本地任务表单时，`getTaskForm` 返回明确的 `EXTERNAL_INTERACTION_REQUIRED`（错误码常量名以实现为准）；`RESULT_ONLY` 的任务接口直接返回 `EXTERNAL_CAPABILITY_UNSUPPORTED`，Web 层只返回外部详情地址和 tracking 状态，不伪造空表单。

### 统计与初始表单语义

- 流程设计事实（定义、设计器图、版本、初始表单 `getInitialForm`）继续以系统 `WorkflowModel` / `FlowDefinitionService` 为唯一来源；映射只接管执行，不复制外部流程图。
- `getInstanceFlowDefinition(instanceId)` 对外部实例仍返回系统流程定义（发起时版本），外部节点详情由 adapter 快照补充。
- 统计接口第一阶段只覆盖内置引擎实例；外部实例统计在第二阶段基于 binding 表与 inbox 派生聚合。`getDurationStatistic` 依赖 adapter 的记录快照能力，能力不满足时返回 `EXTERNAL_CAPABILITY_UNSUPPORTED`，不伪造数据。
- 待办列表跨引擎合并时，外部引擎无稳定游标能力则按 `updatedAt` 上限截断并标记 `hasMore`，禁止 offset 拼接造成重复/遗漏。

## 动作与结果映射

### 动作

`FULL_PROCESS` 的平台 Web 合同仍通过 `SubmitTaskVO.action` 和 `FlowTaskService.doAction(...)` 进入。`RESULT_ONLY` 不开放任务动作，业务结果由结果处理器发布。路由门面完成以下顺序：

1. 从 local task binding 取出 mapping 版本。
2. 使用当前用户、真实任务状态和平台权限做前置校验。
3. `integrationMode=RESULT_ONLY` 时不查动作映射，也不执行本地或外部代理动作；返回 tracking 状态和可选外部地址，最终结果通过回调/查询回流。
4. `integrationMode=FULL_PROCESS` 且 `executionMode=LINK` 时不执行本地或外部代理动作，返回外部访问地址和 `EXTERNAL_INTERACTION_REQUIRED`；审批人在外部详情页完成动作，结果通过回调/查询回流。
5. 其他全过程模式在 mapping 版本中查找 `systemAction`；未配置直接返回 `ACTION_NOT_MAPPED`。`authority=OUROBOROS` 时调用内置 `FlowTaskService` 完成本地动作，不再执行已删除的 mirror 投影；其他外部模式才使用受限字段映射构造 `ExternalActionCommand`。
6. 外部权威模式按 descriptor 选择 starter/reader/action executor；结果型只更新 result binding，全过程模式更新实例/任务 binding。
7. 只在收到可确认的外部成功或已接受结果后更新 binding；最终审批结果以流程真相源的回调或查询快照为准。

系统动作至少包括：`approve`、`reject`、`rollback`、`transfer`、`addSign`、`freeApprove`、`complete`、`cancel`、`pause`、`resume`；`remind`（催办）作为可选规范动作，由适配器在 descriptor 中声明，未声明则审批中心不展示催办入口。适配器可以声明不支持某些动作，映射激活时必须显式选择“禁用”或配置外部动作，不允许运行时猜测。

### 结果

外部状态先由 adapter 归一化为 `ExternalResultCode`，再由 mapping 转换为：

- 任务状态：`CREATED`、`PROCESSING`、`APPROVED`、`REJECTED`、`HANDLED`、`CANCELLED` 等 `TaskState`。
- 实例事件：`STARTED`、`APPROVED`、`REJECTED`、`SUSPENDED`、`RESUMED`、`CANCELLED`。
- 业务结果：`outcome` 和 `closeReason`。

转换成功后复用已有 `TaskEventDispatcher` / `InstanceEventDispatcher`，使通知、待办、业务回调和审计继续使用现有扩展点。外部事件不是直接调用某个 Web Controller，也不能绕过现有事件分发器。

## 回调、轮询与一致性

### 回调入口

提供统一入口：

```text
GET/POST /api/enterprise-callbacks/{connectionId}/callback
```

入口位于中立 Connector：读取受大小和时间预算限制的请求，由连接派生 provider，调用供应商协议验签/解密，再分发给关注该事件的插件。审批插件翻译为原信封，沿 `ExternalCallbackReceiptService` 写入 `external_event_inbox`，可靠受理后入口才返回协议 ACK。旧供应商控制器及通用审批接收地址均退出，不双重入箱；不得在 HTTP 线程中直接执行审批动作或业务回调。

事件 inbox 至少记录：`eventId`、`adapterId`、`connectionId`、`externalInstanceId`、可选的 `externalTaskId`、可选的外部 sequence、外部事件时间、接收时间、原始摘要、处理状态、失败次数和最后错误。唯一键为 `(adapterId, connectionId, eventId)`；同一事件重复投递必须返回成功但不重复发布领域事件。

处理器按以下规则更新：

1. 先查 instance/task binding，使用绑定时保存的 mapping 版本。
2. `RESULT_ONLY` 先查 result binding；`FULL_PROCESS` 再查 instance/task binding。找不到绑定的事件进入待关联队列，不得根据外部 ID 临时创建本地任务。
3. 有 sequence 时按 sequence 去重；没有 sequence 时按外部事件时间做初筛，并查询外部详情或本地对账确认，不覆盖已确认的新状态。
4. 未知外部状态进入 `UNKNOWN`，保留原始状态并告警，不自动结束流程。
5. `RESULT_ONLY` 只有终态才发布统一结果事件；`FULL_PROCESS` 按映射发布任务/实例规范事件，两类事件都经统一发布通道（`ApplicationEventPublisher`，`InstanceEventDispatcher` 为兼容委托）进入业务回调直连与通知、待办投影等既有监听器（见“结果归一化与业务回调直连”）。规范事件发布和 binding 更新在同一平台事务中完成；外部 HTTP 调用不放入数据库事务。
6. 失败事件进入重试队列，超过次数进入死信，支持受保护的人工重放。

适配器只有 `POLLING` 能力时，由 scheduler 按连接和映射配置拉取增量快照，使用同一 inbox 和 revision 规则。轮询是补偿机制，不是第二套状态机。

## 主流引擎结果回传形态与适配映射

### 结果回传的五种主流形态

| 形态 | 代表 | 回传方式 | 补偿/一致性机制 |
|---|---|---|---|
| Webhook 推送 | 钉钉 OA、企业微信审批、飞书审批 | 平台向配置的回调 URL POST 事件 | 签名验签（AES/HMAC + token）；投递失败按退避重试（飞书 15s/5min/1h/6h 共 4 次）；部分平台按类型有序投递（飞书排队重试），部分不保证单调序号（钉钉）；业务侧幂等去重 + 详情查询补全 |
| API 订阅 + Webhook/长连接 | 飞书事件订阅 | 先按审批定义 Code 调用订阅 API，事件按应用配置方式推送（可长连接） | 同上，外加订阅状态需要开通/续期/校验 |
| 纯拉取查询 | 钉钉旧版详情、企业微信/飞书实例与任务查询、自托管 BPM REST（Flowable/Camunda 7 远程引擎、泛微/蓝凌类 OA） | 平台周期性查询实例/任务状态 | 无推送通道或推送不可达（内网）；按时间窗/游标增量分页 + 详情对账；无稳定游标时按 `updatedAt` 截断 + `hasMore` |
| 任务拉取 + 同步完成 | Camunda 8 Zeebe job worker | worker 拉取 job，完成/失败时同步回写结果 | job 不完成流程不推进；超时/重试由引擎侧管理；结果是同步响应而非异步通知 |
| MQ/事件流 | Zeebe record exporter、内部消息总线型 BPM | 引擎把事件导出到 MQ/流，适配器自带消费者 | 消费位点与重试由 MQ 侧保证；业务侧仍需幂等与对账 |
| 嵌入/同进程 | 内置 Flowable/Activiti | 本地事务内同步事件 | 本地事务 + 统一结果事件（`ApplicationEventPublisher`）语义 |

### 与设计的映射（舒适区检查）

| 形态 | 设计落点 | 舒适度 |
|---|---|---|
| Webhook 推送 | `ExternalCallbackCodec` + HTTP 回调入口 + inbox（`eventId` 幂等、可选 sequence + 事件时间 + 详情对账） | 已覆盖；飞书“有序排队重试”落在 inbox 按事件时间/sequence 处理，钉钉“无序”落在详情对账 |
| 订阅 + 长连接 | `ExternalCallbackCodec` + 适配器自有接收器 → `ExternalEventIngestService` | 原设计只有平台 HTTP 入口，本次已补 ingest seam |
| 纯拉取 | `POLLING` 能力 + `ExternalInstanceReader.queryChangedInstances` + 详情对账 | 原设计只有单实例 `getInstance`，本次已补批量增量端口 |
| 同步完成 | `ExternalWorkflowActionExecutor.execute` 同步返回 `ExternalActionResult` | 已覆盖，`RESULT_ONLY`/`FULL_PROCESS` 均天然支持 |
| MQ/事件流 | 适配器自有消费者 → `ExternalEventIngestService` | 同长连接，补 ingest seam 后覆盖 |
| 嵌入/同进程 | 现有引擎事件 + 统一结果事件 | 现状即此形态 |

### 补偿机制的统一清单（设计已承诺）

- 投递侧重试与退避由外部平台负责，平台侧只需幂等（inbox `(adapterId, connectionId, eventId)` 唯一键）。
- 幂等去重：同一事件重复投递返回成功但不重复发布领域事件。
- 详情补偿查询：回调载荷不全时用 `getInstance`/`getTask` 补全（钉钉任务事件只有实例级字段）。
- 对账：`POLLING` 轮询 + `queryChangedInstances` 增量 + 死信人工重放。
- 发起幂等：start 幂等键 + result/instance binding 唯一约束。
- 全量快照型载荷（企业微信 `SpRecord` 携带全部节点明细）：codec 直接归一化为等价事件序列，不需要补偿查询，但不假设所有平台都这样。

## 主流引擎能力对比矩阵（外部对接视角）

图例：✓ 原生支持且对接方可通过 API/凭证调用；△ 受限或部分（依赖应用类型、权限、版本或需自建扩展）；✗ 不支持或未提供；— 不适用或未核实。各单元格以对应平台官方文档为准，接入前必须按真实租户验证。

> **本矩阵描述的是"平台能力"，不是"当前适配器能力"。** 判断某渠道今天能不能用，必须看下面的
> [适配器实现能力矩阵（当前代码事实）](#适配器实现能力矩阵当前代码事实)，以 descriptor / capability / 端口为准。
> 已确认的偏差（2026-09-11 审查，逐条有文件行号证据）：
>
> - 发起时指定/自选审批人、抄送、同意/拒绝、转交、加签、退回、任务表单、待办查询、审批记录/时间线：
>   钉钉/企微/飞书三个适配器**全部返回 `EXTERNAL_CAPABILITY_UNSUPPORTED`**（钉钉 client 已有
>   `listTasks`/`executeTaskAction`，只是没接到端口）；矩阵里的 ✓/△ 指的是平台本身提供这些接口。
> - 催办：三家 descriptor 均未声明 `remind`，矩阵的 ✓ 同样只是平台能力。
> - MQ/事件流、订阅+长连接：三家适配器均未实现。
>
> 因此这些行**不构成"三家都支持"的承诺**，只用于评估"将来值不值得补端口"。

### 实例管理

| 能力 | 钉钉 OA | 企业微信审批 | 飞书审批 | Flowable/Camunda 7（自托管 REST） | Camunda 8（Zeebe） | 传统 OA（泛微/蓝凌等） |
|---|---|---|---|---|---|---|
| 发起实例 | ✓（控件受限、套件有限制） | ✓ | ✓ | ✓ | ✓ | ✓ |
| 发起时指定/自选审批人 | ✓（`approvers`/`targetSelectActioners`） | △（模板+表单控件） | △（可选参数） | ✓（变量驱动） | ✓（变量/网关） | △ |
| 抄送 | ✓（`ccList`/`ccPosition`） | ✓（`Notifyer`） | ✓（`cc_list`） | ✗（服务任务模拟） | ✗ | △ |
| 发起人撤销/撤回 | ✓（发起人撤销） | ✓ | ✓（撤回实例） | ✓（delete instance） | ✓（cancel） | ✓ |
| 催办 | —（未核实） | △（有催办事件，API 以官方为准） | ✓ | ✗ | ✗ | ✓（常见） |

### 任务与动作

| 能力 | 钉钉 OA | 企业微信审批 | 飞书审批 | Flowable/Camunda 7 | Camunda 8 | 传统 OA |
|---|---|---|---|---|---|---|
| 待办查询 | ✓ | ✓ | ✓ | ✓ | ✓（user task） | ✓ |
| 同意/拒绝 | △（仅企业内部应用） | △（以官方文档为准） | ✓ | ✓（complete + 变量） | ✓（complete job） | ✓ |
| 转交/转办 | ✓（转交，`redirect` 事件） | ✓（转审/转交） | ✓（transfer） | ✓（delegate/assign） | △（改派） | ✓ |
| 加签 | —（无通用语义） | ✓（添加审批人/加签） | ✓（add_sign） | △（自定义扩展） | ✗ | △ |
| 退回（rollback） | — | ✓（回退给指定审批人） | ✓（rollback） | △（自定义扩展） | ✗ | ✓ |
| 任务表单 | ✓（schema） | △ | ✓ | △（embedded form） | ✗ | △ |
| 会签/或签 | ✓ | ✓ | ✓ | ✓（multi-instance） | ✓ | ✓ |

### 查询与定义

| 能力 | 钉钉 OA | 企业微信审批 | 飞书审批 | Flowable/Camunda 7 | Camunda 8 | 传统 OA |
|---|---|---|---|---|---|---|
| 实例状态查询 | ✓ | ✓ | ✓ | ✓ | △（operate/exporters） | ✓ |
| 审批记录/时间线 | ✓（实例详情含操作记录） | ✓（`SpRecord` 全量快照） | ✓ | ✓（history） | △（需 exporter 存档） | ✓ |
| 表单 schema | ✓ | △ | ✓ | △ | ✗ | △ |
| 定义发现 | △（后台/模板接口） | △ | ✓ | ✓（repository REST） | ✓ | △ |
| 定义版本 | ✗（`processCode` 无统一版本字段） | △ | △ | ✓ | ✓ | △ |
| 定义写（创建/更新） | ✓ | △ | ✓ | ✓（deploy） | ✓ | △ |

定义写这一行仅作平台能力对比：本设计**不通过对接模块写外部定义**（引用式对接，外部系统是定义作者），避免定义作者权冲突。

### 结果回传

| 能力 | 钉钉 OA | 企业微信审批 | 飞书审批 | Flowable/Camunda 7 | Camunda 8 | 传统 OA |
|---|---|---|---|---|---|---|
| 回调推送 | ✓ | ✓ | ✓ | ✗（listener 自建） | ✗（connector 为出站调用） | △ |
| 订阅开通方式 | 后台人工配置 | 后台人工配置 | API 订阅 | — | — | — |
| 长连接 | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ |
| 重投递保证 | △（有重试，节奏以文档为准） | △ | ✓（15s/5min/1h/6h 共 4 次） | — | — | — |
| 事件单调序号 | ✗ | △（快照式状态） | ✓（按类型有序排队） | — | — | — |
| 纯拉取（对账） | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| MQ/事件流 | ✗ | ✗ | ✗ | △（listener 自建） | ✓（record exporter） | △ |
| 同步完成（job worker） | — | — | — | ✓（同步 REST） | ✓（job worker） | — |

### 横切

| 能力 | 钉钉 OA | 企业微信审批 | 飞书审批 | Flowable/Camunda 7 | Camunda 8 | 传统 OA |
|---|---|---|---|---|---|---|
| 用户身份映射 | userId/手机号（内部应用） | userid/手机号 | open_id/手机号 | 本地账号 | 本地账号 | 本地账号 |
| 多租户 | ✓（corpId） | ✓（corpid） | ✓（tenant_key） | ✓（tenant id） | ✗（分区模拟） | △ |
| 第三方应用可代理动作 | ✗（不可同意/拒绝） | △（以官方为准） | △（自建/商店应用权限差异） | ✓（凭证即权限） | ✓ | ✓ |

关键结论：**能力存在 ≠ 第三方应用可调用**（钉钉是最硬的例子）。矩阵中的 △/✗ 必须落到 descriptor 的 `connectionProfile × capability` 联合校验与映射激活拦截，不能仅凭平台能力表推断可用性。

### 能力覆盖与设计落点

| 能力 | 设计落点 | 状态 |
|---|---|---|
| 发起实例（表单/变量/幂等） | `ExternalWorkflowStarter` | 已有；钉钉/企微/飞书已接 |
| 抄送/发起时指定审批人 | `ExternalStartCommand` 可选字段（`cc`/`approvers`/`targetSelectActioners`） | **未落地**：三家适配器均返回 `EXTERNAL_CAPABILITY_UNSUPPORTED` |
| 同意/拒绝/转交/加签/退回/催办 | `ExternalWorkflowActionExecutor` + 规范动作候选集（含可选 `remind`） | **未落地**：三家均未实现 `ExternalWorkflowActionExecutor`，descriptor 的 `canonicalActions` 为空集；`remind` 另见"催办绕过动作映射" |
| 发起人撤销 | `FULL_PROCESS` 实例动作；`RESULT_ONLY` 可选 `EXTERNAL_INSTANCE_CANCEL` | **未落地**：枚举存在但无端口、无派生、无 provider 声明 |
| 待办/任务查询 | `ExternalTaskReader.queryTasks/getTask` | **未落地**：三家均未实现（钉钉 client 已有 `listTasks`，未接端口） |
| 实例查询与增量对账 | `ExternalInstanceReader.getInstance/queryChangedInstances` | 部分：`getInstance` 三家已接；`queryChangedInstances` 钉钉恒返回不支持、企微/飞书未实现 |
| 审批记录/时间线 | `ExternalRecordReader.queryRecords` | **未落地**：三家均未实现 |
| 任务表单/定义表单 | `ExternalTaskReader.getTaskForm` + `ExternalProcessDefinitionFormReader.getDefinitionForm` | 部分：定义表单已接；任务表单未接（前者依赖任务查询端口） |
| 定义发现与校验 | `ExternalProcessDefinitionDiscovery` / `ExternalProcessDefinitionValidator` / `ExternalProcessDefinitionFormReader` | 部分：校验两家已接；发现端口三家均未实现（目录端点恒抛不支持） |
| 定义写/部署 | 非目标（引用式对接） | 明确声明 |
| 回调验签/批量/自有通道 | `ExternalCallbackCodec` + `ExternalEventIngestService` | 已有；三家已接 |
| 订阅开通/续期 | 适配器职责（`validateDefinition`/`health`） | **未落地**：飞书无 subscribe/unsubscribe |
| 纯拉取/对账 | `POLLING` + `queryChangedInstances` | 框架已有（对账作业 + checkpoint + 死信），**真实渠道零声明** |
| 同步完成（job worker） | `execute` 同步返回 | **未落地**：只有 fake 适配器实现 |
| MQ/事件流 | 适配器自有消费者 → `ExternalEventIngestService` | 框架已有，**零适配器实现** |
| 用户身份映射 | `ExternalUserIdentityMapper` | 已有；三家已接 |
| 多租户/连接作用域 | `scope` + `connection` + `credentialRef` | 已有 |

### 适配器实现能力矩阵（当前代码事实）

下表是"代码今天真正实现了什么"，由 descriptor 声明与端口实现共同决定，与上面的平台能力矩阵口径不同。
判定方法：能力由 Registry 按端口 `instanceof` 派生（`EngineAdapterCapabilities.derive`），
接入模式由能力派生（`integrationModeOf`），描述里不再重复声明模式。

| 端口 / 能力 | 钉钉 | 企微 | 飞书 | fake |
|---|---|---|---|---|
| `health` | ✓ | ✓ | ✓ | ✓ |
| `discoverDefinitions`（`DEFINITION_DISCOVERY`） | ✗ | ✗ | ✗ | ✓ |
| `validateDefinition` / `getDefinitionForm` | ✓ / ✓ | ✓ / ✓ | ✓ / ✓ | ✓ |
| `start`（`EXTERNAL_START`） | ✓ | ✓ | ✓ | ✓ |
| `getInstance`（`EXTERNAL_INSTANCE_QUERY`） | ✓ | ✓ | ✓ | ✓ |
| `queryChangedInstances`（`POLLING`） | 端口在但恒不支持 | ✗ | ✗ | ✓ |
| `queryRecords`（`EXTERNAL_RECORD_QUERY`） | ✗ | ✗ | ✗ | ✓ |
| `queryTasks` / `getTask` / `getTaskForm`（`EXTERNAL_TASK_QUERY`） | ✗ | ✗ | ✗ | ✓ |
| `execute`（`EXTERNAL_TASK_ACTION`） | ✗ | ✗ | ✗ | ✓ |
| `verifyAndParseCallback`（`CALLBACK`） | ✓ | ✓ | ✓ | ✓ |
| `ExternalWorkflowStartRecovery` | ✗ | ✗ | ✓ | ✗ |
| `canonicalActions` | 空集 | 空集 | 空集 | approve/reject/transfer/remind/cancel |
| 派生接入模式 | RESULT_ONLY | RESULT_ONLY | RESULT_ONLY | FULL_PROCESS |
| 派生交互模式 | LINK | LINK | LINK | PROXY |

结论：三个真实渠道均通过统一发起契约进入；其能力仍由适配器声明，RESULT_ONLY 只提供结果绑定而不提供本地任务。
`cc`/`approvers`、任务动作、任务与记录查询、定义发现、订阅生命周期、真实轮询都还没有实现，
需要在补端口之前先确认平台侧是否真的可调用（见上面的偏差说明）。

### 内置引擎能力自查与对齐建议

按同一矩阵口径对照内置引擎（Flowable/Activiti6 现状）：

| 能力 | 内置现状 | 主流对标 | 建议 |
|---|---|---|---|
| 发起实例/初始表单 | ✓ | ✓ | — |
| 发起时自选审批人 | ✓（`UserSelectCandidate` 声明 + 发起需求动态表单 + 启动校验，仅单/多选） | 钉钉自选节点（含选择范围）、企微表单控件、飞书发起参数 | 补选择范围/数量区间；统一契约见下节 |
| 发起时覆盖固定审批人 | ✗（`taskAssignee` 仅对自选节点生效） | 钉钉 `approvers` 可整体覆盖后台流程 | 定义级 `allowOriginatorOverride` 开关 |
| 发起时抄送 | ✗（仅流程内抄送节点） | 钉钉 `ccList`/`ccPosition`、企微 `Notifyer`、飞书 `cc_list` | start 契约补 `cc` |
| 会签/或签/比例 | ✓（串行/并行/percentage） | ✓ 全平台 | — |
| 转交/转办 | ✓ | ✓ | — |
| 加签（前/后） | ✓（position + targetUserIds） | 企微加签、飞书 add_sign | 对齐度高，无需动 |
| 退回（rollback） | ✓ | 飞书 rollback、企微回退给指定审批人 | — |
| 自由审批/自由驳回/自由恢复 | ✓（平台增强能力） | 无直接对标 | 优势项，保留 |
| 撤销/撤回 | △（通用 `cancel`，未显式区分发起人撤回与管理终止） | 发起人撤销（钉钉）、已撤销/通过后撤销（企微）、撤回实例（飞书） | 显式建模发起人撤回（仅发起人、审批中可撤回）+ 撤回后允许重新发起 |
| 催办 | ✗（仅系统超时提醒） | 飞书催办 API、企微催办事件 | 补可选 `remind` 规范动作（实例/任务级） |
| 超时提醒/超时自动处理 | ✓（timer + `onTimeoutRemind` + `timeoutAction`） | 多数平台仅提醒或无 | 优势项，保留 |
| 条件网关/子流程 | ✓ | BPM 系原生 | — |
| 审批记录/时间线/统计 | ✓ | ✓ | — |

完善优先级建议：与外部契约对称的三项（发起时抄送 `cc`、发起人撤回语义、`remind` 动作）先补，它们直接影响统一结果事件与矩阵的对称性；自选审批人范围约束与 `allowOriginatorOverride` 是产品增强，随后排期；加签/超时/自由审批是优势项，不动。

### 自选审批人的统一契约与适配（目标合同，含已实现部分）

`ApprovalWorkflowStartRequirementsService`、`startOptionsJson` 与候选范围声明已有实现；以下条目保留统一目标，具体数量/成员资格边界按当前源码与技术债跟踪，不能把整节视为未开始或已全部完成。

主流方案的三段式模式：**定义声明（哪些节点自选 + 选择范围）→ 发起时选择 → 启动校验**。钉钉模板声明"发起人自选节点"、发起时填 `targetSelectActioners`；企微用表单联系人控件完成自选；飞书发起时可选 `approver` 参数。

内置实现（已核实）同样遵循三段式：定义声明 `UserSelectCandidate`（single/multiple）→ `ApprovalWorkflowStartRequirementsService` 按定义生成 user-picker 动态表单 → 提交 `StartFlowInstanceData.taskAssignee.{nodeId}` → `FlowInstanceCommandService.filterUserSelectAssignee` 启动校验。企微式"表单字段决定审批人"已由 `FormFieldCandidateProvider` 覆盖。

与主流的差距在三处：无**选择范围约束**（主流限定岗位/角色/部门/表单字段），无**数量区间与审批方式联动**（多人自选时是否可指定或签/会签），不可**覆盖固定候选节点**。

统一契约设计——前端逻辑共用、后端适配层抹平差异：

1. **发起需求契约引擎无关化**：`getStartRequirements(flowKey, formData)` 由路由层统一提供。内置返回自选节点表单（现状）；外部映射返回同一契约结构，数据来源为 mapping 的 `startOptions` 声明或适配器从外部定义解析的自选节点（`discoverDefinitions`/`getDefinitionForm`）。契约字段：`requirements[{nodeId, label, mode, min, max, candidateRange(可选: role/position/department/formField), allowOverride}]` + `cc.enabled`。
2. **提交契约统一**：`StartFlowInstanceData` 增加 `cc` 与统一的审批人选择结构；无论内置还是外部，前端提交同一结构、渲染同一套 picker 组件。
3. **适配层翻译**：内置 → 现有 `filterUserSelectAssignee` + Flowable 变量路径；外部 → 上轮已补的 `ExternalStartCommand` 可选字段（`approvers`/`targetSelectActioners`/`cc`）。外部定义不允许覆盖时，StartRequirements 不返回该项，前端自然不渲染——差异在需求契约阶段就抹平。
4. **范围约束落地**：`UserSelectCandidate` 增加可选 `candidateRange`；前端 picker 按范围过滤（组织架构模块已有数据源），后端启动校验范围合规；外部适配器把同一范围约束翻译为外部可表达形式，外部引擎缺失校验能力时由平台侧启动前兜底校验。
5. **校验闭环**：前后端共用同一份 requirements 契约做必选/数量/范围校验；外部发起失败按现有幂等与绑定回滚规则处理。

## 结果归一化与业务回调直连

### 已替代的旧链路与问题

以下是回调直连改造前的历史背景；当前投影不再生成 `ProcessCallbackAPI`。旧链路中，业务模型绑定审批流后，投影管线生成 `ProcessCallbackAPI`（`POST /api/{modelPath}/{id}/process-callback`，`INTERNAL` 作用域、`LOGIC_FLOW` handler）与回调逻辑流 `<fullName>.processCallback`；`startProcess` 模板把 `callbackUrl=/api/{modelPath}{bizData.id}/process-callback` 写入引擎启动变量。引擎事件携带 `callbackUrl`，`ApprovalProcessCallbackListener` 在 `WebAPIModelCatalog` 中按路径模式匹配 POST + LOGIC_FLOW 模型，合成 `{method, path, pathVariables, params, body, data}` 伪 HTTP 请求执行逻辑流；模板再从 `request.data`/`request.body` 解包 `outcome`/`eventType`、从 `$request.params.id` 取业务记录 ID。

该链路的问题：伪 HTTP 形状把事件层耦合到 Web 层，形成没有身份认证语义的“内部 webhook”；业务记录 ID 藏在 URL path 里；同一流程被多个业务模型绑定时依赖“一实例一 URL”；外部引擎结果没有 `callbackUrl`，无法复用这条业务处理链路——结果获取方式绑架了结果处理方式。

### 统一结果事件

当前 `TaskEventDispatcher` / `InstanceEventDispatcher` 在装配 outbox 且事务同步有效时，同事务 enqueue 并在提交后领取投递；没有该前提时直接发布。以下同步事务描述仅适用于直接发布分支，不能当作所有事件的唯一交付时机。

- `FlowInstanceEvent` 即统一结果事件：内部引擎与外部引擎发布同一事件类型。内部引擎在引擎事务内发布；外部引擎无论推（回调）、拉（查询）还是轮询补偿，都先进入 inbox，经结果映射归一化后发布同一事件。
- 发布机制采用 Spring `ApplicationEventPublisher`（事件对象作为 payload 直接发布，监听方用 `@EventListener` / `@TransactionalEventListener` 按 payload 类型订阅）。现有 `InstanceEventDispatcher` 迁移为对该发布器的兼容委托，`FlowInstanceListener` 实现类逐步改为注解监听器，不保留长期双通道。该机制与仓库既有 `OuroborosEvent`（`libs/common`）生命周期事件模式一致，也为后续 Outbox/可靠总线演进预留了同协议替换点。
- 事务语义由正式 dispatcher/outbox 决定：事务内入队、提交后投递并支持重试；无 outbox 或无事务同步时直接发布。监听方须核对实际投递阶段和重试幂等性，不假设回调一定处于原引擎事务。
- 载荷使用新模型字段：`businessContext`（`recordType` + `recordId`，业务记录定位）、`callbackFlow`（回调逻辑流绑定键）、`trackingId`（`RESULT_ONLY` 本地跟踪 ID）、`externalUrl`（`LINK` 跳转地址）。`callbackUrl` 不再进入新载荷或引擎变量。
- `RESULT_ONLY` 的 `instanceId` 取 `trackingId`，统一“每个结果事件都有本地事实 ID”的契约；只发布 STARTED（发起已被外部接受）与终态事件。业务上下文一律从本地 result binding 表取，绝不从外部回调 payload 取。
- `RESULT_ONLY` 的 STARTED 参与业务回调直连：与 result binding 同事务发布，把业务记录置为“审批中”（按绑定配置的 processing 状态码生效；未配置则跳过该分支，不写空状态）。外部启动被拒时不产生 STARTED，业务记录停留在发起前状态；响应丢失场景由幂等查询/对账补建绑定后补发。

### 业务回调直连（替代 webhook 路由）

- 投影管线不再生成 `ProcessCallbackAPI`，改为生成回调绑定元数据 `CallbackBinding {flowKey, callbackFlow, recordType, statusField, statusMap}`，注册进 `ApprovalProcessCallbackBindingCatalog`（随元数据刷新事件重建缓存，机制同现有 metadata provider）。
- `ApprovalProcessCallbackListener` 改为直接路由：按 `event.callbackFlow`，缺省时按 `event.businessContext.recordType` 查 catalog；命中后 `LogicFlowExecutionFacade.execute(callbackFlow, [统一事件载荷])`。不再做 URL 解析、路径模式匹配和伪 HTTP 请求构造。
- 高代码路由是否必要：需要，但只保留一次 O(1) 的绑定查找；路由表由低代码投影产生，高代码只提供机制不做业务决策。多业务模型绑定同一流程时按 `recordType` 命中各自绑定、各自更新各自业务记录，弥补“一实例一 URL”的局限。
- 安全：`callbackFlow`/`recordType` 只来自本地绑定元数据与持久化绑定表；外部回调 payload 仅用于推导外部状态。消灭 HTTP 形状后不再存在“回调无认证”问题——事件分发走进程内受信通道。

### 回调逻辑流模板改造

- 入口载荷从伪 HTTP request 改为统一结果事件（扁平 map）。
- 删除解包 groovy 脚本；状态路由改用已注册的 `processStatusRouter` 节点（参数即 `statusField` 与 `statusMap`，路由分支 审批中/通过/否决/撤销/终止/其他）。
- 业务记录定位 `$request.params.id` 改为 `$businessContext.recordId`。

### 获取与处理的解耦

- 获取面（推/拉/轮询补偿）只负责产出 inbox 事件；处理面统一为“inbox 处理器 → 归一化结果事件 → 既有监听器（业务回调直连、通知、待办投影、业务日志）”。
- 业务记录更新失败只影响业务侧状态同步，不影响审批结果事实（结果以绑定表与事件为准）；重试与死信沿用 inbox 规则，人工重放不改结果。

### 未上线清理策略

- 本模块尚未开放生产流量，结果回调正式路径直接收敛到 `CallbackBinding` + 统一结果事件；不保留 `callbackUrl` legacy 双读、伪 HTTP webhook 或兼容窗口。
- `ProcessCallbackAPI` 投影停止生成；新实现不再写入 `callbackUrl` 引擎变量，已存在的实验性字段按新模型清理，不为旧形态增加迁移胶水。

## 钉钉预置适配器

`dingtalk-oa` 通过 provider 注册。默认稳定 descriptor 只声明 RESULT_ONLY，以 DingTalk AppLink externalUrl 提供 LINK 交互；不声明 FULL_PROCESS、任务查询或任务动作能力。钉钉类型留在适配器边界，不向 core、manage 或 Web VO 泄漏。

原生 OA 的 start 使用钉钉发起接口，需要 originatorUserId、processCode 和 formComponentValues；发起前校验模板 schema 和字段约束。第三方应用只能声明实际可用的启动、终态回调及详情跳转能力，不能代理同意/拒绝，也不能把外部平台的能力自动视为本适配器已实现的能力。

回调验签、解密、时间窗及重放处理经 ExternalCallbackCodec；inbox 按 adapterId、connectionId、eventId 幂等，并按事件时间与详情查询补偿乱序。用户标识通过 ExternalUserIdentityMapper 显式映射，未映射任务进入待处理状态。凭据与授权边界继续遵守下文“安全与权限”。

默认 provider 不实现原生 FULL_PROCESS。后续评估需独立实验 profile 或适配器，并取得任务查询、表单、动作权限和顺序证据，不能用研究结果直接修改默认能力声明。流程中心镜像接口不是当前实现路径。

## 持久化模型

通过 `ouroboros-data` 定义模型，禁止外部适配器直接操作数据库。

所有 `*RouteKey` 唯一事实键均使用命名空间化固定长度编码：`v1:` + SHA-256 十六进制摘要。摘要输入为带长度前缀的业务事实片段，避免分隔符碰撞；字段容量统一预留为 96 字符。设计语义仍按下列业务事实描述，数据库中不保存原始拼接键。

### `t_approval_workflow_engine_mapping`

保存映射聚合的可查询字段和版本化 JSON：

`id`、`scope`、`flowKey`、`adapterId`、`connectionId`、`externalDefinitionId`、`externalDefinitionVersion`（可空）、`externalDefinitionSchemaHash`、`externalDefinitionValidatedAt`、`authority`（接入模式与交互模式**不再落库**：由 Registry 按适配器端口能力派生，见 `integrationModeOf`/`executionModeOf`）、`resultBindingJson`、`status`、`activeRouteKey`、`version`、`actionMappingsJson`、`resultMappingsJson`、`callbackJson`、`validationMessage`、`createdBy`、`createdAt`、`updatedAt`。

唯一约束：`activeRouteKey` 唯一。`activeRouteKey` 仅在 `ACTIVE` 时取 `scope + flowKey` 的稳定编码，其余状态取映射自身私有键，确保同一作用域和 `flowKey` 只有一个活动版本，同时不限制多个草稿/历史版本。`processDefinitionId` 仍只表示内置引擎部署 ID，不复用来存外部 ID。

### `t_approval_workflow_external_instance_binding`

`FULL_PROCESS` 使用本地实例绑定；`RESULT_ONLY` 使用下方的结果绑定。

`localInstanceId`、`flowKey`、`scope`、`mappingId`、`mappingVersion`、`adapterId`、`connectionId`、`externalInstanceId`、`externalInstanceRouteKey`、`externalDefinitionId`、`status`、`outcome`、`externalRevision`、`externalEventTime`、`externalUrl`、`createdAt`、`updatedAt`。

唯一约束：`externalInstanceRouteKey` 唯一，语义为 `adapterId + connectionId + externalInstanceId`；`localInstanceId` 为主键。

### `t_approval_workflow_external_result_binding`

结果型接入使用独立绑定，不创建本地实例或任务：

`trackingId`、`scope`、`flowKey`、`mappingId`、`mappingVersion`、`adapterId`、`connectionId`、`businessRecordType`、`businessRecordId`、`externalInstanceId`、`idempotencyKey`、`externalInstanceRouteKey`、`idempotencyRouteKey`、`businessRunningRouteKey`、`externalDefinitionId`、`status`、`outcome`、`externalRevision`、`externalEventTime`、`finalResultAt`、`externalUrl`、`createdAt`、`updatedAt`。

唯一约束：`externalInstanceRouteKey`、`idempotencyRouteKey`、`businessRunningRouteKey` 分别唯一。显式幂等键按 `adapterId + connectionId + idempotencyKey` 去重。业务记录字段成对可选；存在业务记录时，同一 `scope + flowKey + businessRecordType + businessRecordId` 仅允许一个 `RUNNING` 绑定，终态后释放运行中事实键；无业务记录时不生成该键、不创建业务案件，仍保存启动事实、结果绑定与事件。

### `t_approval_workflow_external_task_binding`

`localTaskId`、`localInstanceId`、`scope`、`mappingId`、`mappingVersion`、`adapterId`、`connectionId`、`externalTaskId`、`externalTaskRouteKey`、`nodeId`、`assignee`、`status`、可选的 `externalRevision`、`externalEventTime`、`externalUrl`、`updatedAt`。

唯一约束：`externalTaskRouteKey` 唯一，语义为 `adapterId + connectionId + externalTaskId`；`localTaskId` 为主键。

### `t_approval_workflow_external_pending_assignment`

外部任务快照中的 `assignee` 无法通过 `ExternalUserIdentityMapper` 解析为平台用户时，不再把外部字符串透传为平台用户，也不在用户待办列表中返回 `assignee=null` 的伪正常任务，而是记录源侧待处理 assignment：

`id`、`scope`、`flowKey`、`mappingId`、`mappingVersion`、`adapterId`、`connectionId`、`externalInstanceId`、`externalTaskId`、`externalUserId`、`assignmentRouteKey`、`reason`、`status`、`firstSeenAt`、`lastSeenAt`。

唯一约束：`assignmentRouteKey` 唯一，语义为 `scope + adapterId + connectionId + externalTaskId + externalUserId`。重复发现同一外部任务/用户组合时刷新 `lastSeenAt` 并保持 `PENDING`，由运维/后续身份映射补齐流程处理；计数路径只做纯过滤，不写 pending 队列。

### `t_approval_workflow_external_event_inbox`

保存回调/轮询事件的幂等键、顺序、处理状态和错误，不保存未经脱敏的密钥或完整敏感 payload。原始内容如确有审计要求，必须经过适配器脱敏后保存并设置保留期限。

唯一约束：`(adapterId, connectionId, eventId)` 唯一；重复事件视为幂等成功，不重复发布领域事件。

## 配置与 Web API

### 适配器目录

- `GET /api/approval-workflow/engine-adapters`
  - 返回 `adapterId`、名称、版本、能力、规范动作、连接配置 schema、健康状态。
- `GET /api/approval-workflow/engine-adapters/{adapterId}/connections/{connectionId}/definitions`
  - 调用 provider 的 `discoverDefinitions`，返回外部定义候选项。
- `POST /api/approval-workflow/engine-adapters/{adapterId}/connections/{connectionId}/health`
  - 只做受保护的连接检查，不改变映射状态。

### 流程映射管理

- `GET /api/approval-workflow/engine-mappings`
- `POST /api/approval-workflow/engine-mappings`
- `PUT /api/approval-workflow/engine-mappings/{id}`
- `POST /api/approval-workflow/engine-mappings/{id}/validate`
- `POST /api/approval-workflow/engine-mappings/{id}/activate`
- `POST /api/approval-workflow/engine-mappings/{id}/pause`
- `POST /api/approval-workflow/engine-mappings/{id}/retire`

激活接口必须返回映射版本、验证摘要、适配器和外部定义摘要。页面应展示“适配器、连接、外部流程定义、流程真相源、接入模式、交互执行模式、动作映射、结果映射、业务绑定规则、回调状态、验证错误”这些字段，不直接展示或编辑凭据。

### 结果跟踪

- `POST /api/approval-workflow/external/result-bindings/start`：旧结果绑定请求的兼容转换入口，内部调用统一 `FlowInstanceService.start(FlowStartCommand)`；业务记录字段可选，发起人、表单/变量和幂等键按统一契约处理。新业务应使用通用 `POST /api/approval-workflow/instance/{flowKey}/start`。
- `GET /api/approval-workflow/external/result-bindings/{trackingId}`：查询外部实例引用、当前状态、最终结果、最近同步时间和外部详情地址。
- 结果回调或轮询只更新 result binding，并发布带 `trackingId` 和业务记录主键的规范业务结果事件；不生成本地任务。

### 配置存储

连接配置通过 `ouroboros-configuration` 管理，建议键：

```text
workflow.external.{adapterId}.connections.{connectionId}.endpoint
workflow.external.{adapterId}.connections.{connectionId}.credentialRef
workflow.external.{adapterId}.connections.{connectionId}.options
```

`credentialRef` 只引用安全存储中的密钥；映射表和日志禁止写入 `clientSecret`、`accessToken`、签名原文。

## 页面操作与执行能力

统一 `FlowInstanceService.getProcessInfo` 返回状态、外部链接及页面能力，HTTP 入口为 `/api/approval-workflow/instance/{instanceId}/info`。业务模型列表把记录类型交给审批地址查询，路由层复用 `ApprovalCase` 定位当前或最后一次执行；启动未确认时读取既有启动事实。未扩展的旧内置查询仍按审批地址反查。

RESULT_ONLY 不存在本地任务，`processable=false`，不显示本地处理按钮。外部执行不提供本地 BPMN 实例图；只有适配器声明记录查询能力时才显示时间线，查询复用连接感知的 `ExternalRecordReader`。详情总能展示已保存状态与结果，有 `externalUrl` 才显示“打开外部审批”。实例取消按钮要求当前状态可取消且适配器具备对应实例动作能力。页面隐藏不可用组件时不请求对应 API；后端继续拒绝不支持的操作，不通过前端隐藏替代校验。

## 安全与权限

- 映射查看、编辑、验证、激活、暂停和连接健康检查使用独立权限，不复用普通流程发起权限。
- 回调 URL 按 `adapterId + connectionId` 隔离，适配器必须验签、校验时间窗和重放 nonce。
- `FULL_PROCESS` 的所有外部动作都由平台当前用户触发并记录 `userId`、`mappingVersion`、`localTaskId`、外部请求 ID；`RESULT_ONLY` 不开放外部动作。系统回调使用受信任的 `SYSTEM_ACTOR`，但仍执行状态和幂等校验。
- 外部用户 ID 到平台用户 ID 必须经过显式 `ExternalUserIdentityMapper`；找不到映射时任务进入待处理/异常状态，不把外部字符串直接当平台用户。
- 租户/应用作用域从现有上下文获取，不能由请求体中的任意 `scope` 覆盖。

## 可观测性与错误

统一错误码建议包括：`ENGINE_ADAPTER_NOT_FOUND`、`ENGINE_CONNECTION_UNAVAILABLE`、`WORKFLOW_MAPPING_NOT_ACTIVE`、`WORKFLOW_ROUTE_NOT_CONFIGURED`、`EXTERNAL_DEFINITION_NOT_FOUND`、`EXTERNAL_CAPABILITY_UNSUPPORTED`、`EXTERNAL_ACTION_NOT_MAPPED`、`EXTERNAL_INTERACTION_REQUIRED`、`EXTERNAL_EVENT_STALE`、`EXTERNAL_EVENT_UNKNOWN`、`EXTERNAL_ACTION_CONFLICT`。

日志和指标至少按 `adapterId`、`connectionId`、`flowKey`、`integrationMode`、`mappingVersion` 维度记录：调用延迟、失败率、限流、结果绑定等待时长、回调延迟、inbox 重试/死信、未知状态、任务同步延迟。外部请求日志只记录脱敏摘要和 correlation ID。

## 兼容与切换策略

无映射且策略为 internal 时，经显式 qualifier 委托内置 Bean；策略为 reject 时拒绝启动。外部流程映射先验证，再激活；新实例使用新版本，运行中实例继续按其绑定版本处理。外部引擎失败不回退内置引擎。下线映射仅影响新实例，旧实例须完成或按正式迁移/对账机制收敛。

切换验证使用 fake adapter 覆盖结果型、全过程、乱序、重试和回归行为；真实平台 smoke 使用明确配置的连接与证据。操作步骤见[切换手册](../../../docs/runbooks/approval-workflow-external-engine-migration-runbook.md)，单次实施顺序和结果归关联任务。

## 测试策略

### core/runtime 单元测试

- 映射聚合验证：重复 flowKey、版本冲突、两种 integrationMode 的必需字段、动作/结果覆盖、能力不满足。
- 路由：有效映射走外部、无映射走内置、`reject` 策略拒绝、外部失败不回退。
- ID 绑定：结果型业务记录/外部实例唯一性、重复外部 ID、映射版本固定、全过程任务/实例 local ID 解析。
- 事件归一化：结果映射、未知状态、乱序 revision、重复 eventId 幂等。

### 适配器契约测试

每个 provider 使用 `AbstractExternalEngineAdapterContractTest` 基类加各自的 `*EngineAdapterContractTest` 验证：

- descriptor 和能力声明一致；
- definition 校验不会隐式选择“最新版本”；
- 统一 start 可带业务记录，也允许无业务对象的外部审批；带幂等键并返回 tracking/external ID；`FULL_PROCESS` start/execute 带幂等键并返回可追踪外部 ID；
- 回调验签、重复回调、过期回调和未知状态行为符合合同。

钉钉额外验证：第三方连接激活 `PROXY` 动作必须失败；`LINK` 的 `doAction` 只返回 `EXTERNAL_INTERACTION_REQUIRED` 和外部地址；无 sequence 的回调按详情对账处理。

### 集成/E2E

- 结果型：映射配置 -> validate -> activate -> 发起 -> 业务记录绑定 -> 外部终态回调 -> 业务结果事件。
- 全过程：映射配置 -> validate -> activate -> 发起 -> 外部任务 -> 本地审批中心动作 -> 外部回调 -> 本地待办/通知/业务回调。
- 外部拒绝、撤回、转办、超时和连接短暂不可用。
- Flowable 未映射流程回归，保证现有引擎选择测试和审批动作测试不受影响。
- 使用 fake adapter，不把真实钉钉/BPM 网络依赖放入默认 CI；真实平台只放在显式 contract profile。

## 关键取舍

### 为什么不直接把外部实现做成 `FlowInstanceService` Bean

现有 `FlowInstanceService` 等接口是给平台调用方的门面，包含按流程、实例、任务和用户查询的不同路由维度。让每个外部系统直接实现这些接口会产生多个同类型 Bean、注入歧义、外部 ID 泄漏和跨引擎分页问题。新增一个小而深的外部适配器接口，再由唯一的路由门面实现现有接口，可以把变化集中在 ACL 中。

### 为什么不把映射写入 `WorkflowModel.json`

`WorkflowModel.json` 是审批定义文档；适配器、连接凭据、映射版本和回调状态属于运行治理。混在一起会让设计时版本覆盖运行时连接，也无法独立审计和回滚。映射必须独立建模，并通过 `flowKey` 关联。

### 为什么不在外部失败时回退 Flowable

外部请求可能已经成功但响应丢失，回退会产生双实例和双审批。映射一旦激活，外部适配器就是该流程的唯一执行来源；恢复依赖幂等查询、重试、对账和人工决策，而不是第二次启动内置流程。

## 当前约束与后续演进

映射使用不可变版本；激活新版本时旧 ACTIVE 版本退役，运行中实例继续按绑定版本解释。核心审批语义不反向依赖 ability；业务模型表单集成通过专门桥接模块连接，避免循环依赖。

FULL_PROCESS 的现有路由门面服务于外部权威引擎，未来待办中心应消费业务源事件或快照，不复用实时聚合查询作为另一条投影路径。内置任务生命周期由审批流拥有；外部事件的 revision/time 由 external-integration binding 拥有。authority=OUROBOROS 只表示内置引擎执行，镜像 hook、mirror outbox 和对应 capability 已删除。

外部事件处理区分重复、过期与新事件；没有 sequence 时按外部事件时间做单调保护。可靠事件投递使用 core outbox，保留租约及重投边界；不能把已删除的 mirror outbox 当作当前实现。

以下差距不能因旧阶段清单归档而视为完成，具体偿还仍需代码与行为证据：

- 当前待办查询对外保持 List 契约，游标在聚合器内消费；前端游标分页需要正式接口演进。
- candidateRange 的成员资格数据源接入仍需单独验证，数量约束不能替代成员资格校验。
- 钉钉默认接入不承诺 FULL_PROCESS；真实平台验证与实验 profile 证据必须注明连接类型和基线。
- 远程平台、作用域、回调与外部能力的其他差距继续在[技术债](../../../docs/tech-debt-tracker.md) DOC-001、DOC-002 等原条目跟踪。

企业微信与飞书已经有适配器实现及合同测试入口，不再列为“尚未立项”；这不等于真实租户联调或所有平台能力已通过验收。
