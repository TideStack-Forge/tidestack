# Approval Workflow Regression Contract

## Objective

验证审批流回归场景在真实产品边界内成立：

1. 在 develop-shell 中创建并配置业务模型，而不是走“简单表单设计器”捷径；
2. 在 approval workflow definition 管理面中创建、编辑、绑定并保存审批流程静态元数据；
3. 提交并确定应用元数据版本，使后续运行时只消费已提交版本；
4. 在业务模型工作流配置中写入 `workflow.bindings[0].processId`、`workflow.bindings[0].statusField`、`workflow.bindings[0].statusMap`；
5. 由基座运行或刷新应用元数据，应用实例发现 `workflow/{name}.workflow.json` 并刷新运行时 `WorkflowModel`；
6. 在测试平台应用实例中显式部署运行时模型后，再完成发起、待办处理、基础动作、高级动作、会签、历史和完成态验证；
7. 对已接管的 metadata-layer 定义提供正式 reset 操作，而不是通过物理删除或删除实例库流程替代；
8. 在环境异常时，能清楚区分 lifecycle 503、nginx `/actuator/` 路径问题、前端壳加载失败、后端异常、以及测试契约/selector 漂移。

## Scope

- 在范围内：
  - 业务模型创建与配置：基础信息、字段/关联、视图、接口、工作流特性及保存/提交语义
  - 审批流程定义创建与编辑：单签、会签、串行、并行、版本化定义保存为 `workflow/{name}.workflow.json`
  - 应用元数据版本确定：开发平台保存后必须单独提交，不允许把保存误述为部署
  - 审批流程定义表单绑定：通过 approval workflow base-info 能力绑定 `form.formType` / `form.formId`
  - 应用元数据刷新：基座从 metadata path 发现 `workflow/*.workflow.json` 并刷新运行时 `WorkflowModel`
  - 审批流程部署与下线：通过运行时 approval workflow definition API/UI 执行 `deploy` / `offline`
  - 审批流程重置：通过 `reset` 执行恢复，保留 Flowable 历史部署和流程实例
  - 业务模型工作流绑定：`workflow.bindings[0].processId`、`workflow.bindings[0].statusField`、`workflow.bindings[0].statusMap.*`
  - 运行时发起审批：`init-form` / `start`
  - 基础审批动作：`approve`、`reject`、`rollback`
  - 高级审批动作：`transfer`、`addSign`、`freeReject`、`freeApprove`
  - 单签与会签节点
  - 串行与并行审批模式
  - 条件判断与稳定 V2 分流合流语义：XOR 用多出边条件分支，OR 用显式 `JoinNode(joinType=OR)` 汇聚
  - 当前稳定节点类型：`StartNode`、`ApprovalNode`、`HandleNode`、`CcNode`、`SubflowNode`、`JoinNode`、`EndNode`
  - 多用户协作审批场景
  - 审批历史、流程图、流程状态追踪与完成态验证
  - 环境预检、生命周期健康检查、Docker/bootstrap 诊断、微前端注入与快速后端 jar 替换指引
- 不在范围内：
  - 低代码平台其他无关模块功能回归
  - 审批流程设计器纯视觉/像素级交互测试
  - 定时器、通知节点等当前场景未覆盖能力
  - `freeRecover` 浏览器覆盖（当前用户要求的高级动作范围仅为 4 个）
  - 性能压测、并发压测和大规模数据量基准
  - 为了让测试通过而引入新的平行 fixture 模型或产品旁路

## Fixtures

- Services:
  - 无强制外部三方服务；环境由本地 Docker/bootstrap 组合提供
- Runtimes:
  - `runtimes/develop` - 开发平台运行时
  - `runtimes/mothership` - 应用运行基座 / Flowable runtime 宿主
- Frontends:
  - `frontends/develop-shell` - 开发平台前端壳
  - `frontends/runtime-shell` - 运行时前端壳
- Data:
  - `data/app-metadata-root` - 应用元数据仓根目录
  - `data/approval-workflow-org-seed` - 审批流组织架构种子
  - `data/approval-workflow-roles-seed` - 审批流角色种子
  - `data/approval-workflow-users-seed` - 审批流用户种子（含 `awf.approver1/2/3`）
  - `data/approval-workflow-business-model-seed` - 审批流业务模型种子（含 `AWF_BM_EXPENSE_REQUEST`）
  - `data/approval-workflow-flows-seed` - 审批流定义种子
- Identities:
  - `identities/admin` - 管理员（创建业务模型、流程定义、部署）
  - `awf.approver1` / `awf.approver2` / `awf.approver3` 由 `data/approval-workflow-users-seed` lifecycle 自动创建，不再作为人工注入的 identity fixture

## Pass Criteria

- 业务模型以真实产品路径 `/dev/{appName}/business-model*` 创建/编辑成功，且不再以“审批表单创建”误述
- APF-FORM-001 能证明业务模型至少完成必需创建字段（`name`、`title`）和保存/提交路径区分
- APF-FLOW-001 / APF-FLOW-002 能证明审批流程定义保存为 `workflow/{name}.workflow.json` 静态应用元数据
- APF-BIND-001 能明确验证两类绑定均成立：
  - 审批流程定义中的 `form.formType` / `form.formId`
  - 业务模型中的 `workflow.bindings[0].processId` / `workflow.bindings[0].statusField` / `workflow.bindings[0].statusMap`
- APF-METADATA-001 能证明静态文件只包含定义字段，不包含 `source/adopted/status/processDefinitionId/definitionId/id`
- APF-ASSET-001 / APF-ASSET-COMMIT-001 能证明开发平台保存和应用元数据提交是两个步骤，保存只产生静态文件，提交才确定可被基座运行的版本
- APF-RUNTIME-BOOT-001 / APF-RUNTIME-LAYER-001 能证明基座从 `AppContext.homePath` 对应应用元数据路径启动或刷新，并刷新运行时 `WorkflowModel`，且刷新后尚未部署、未接管、没有 `processDefinitionId`
- APF-DEPLOY-001 能证明运行时部署成功执行，且部署后才产生 `processDefinitionId`，部署结果能从 Flowable/definition 状态侧验证
- APF-METADATA-LAYER-UPDATE-001 能证明更高静态 `version` 刷新后，已部署 metadata-layer 进入 `updated` 而不是继续显示 `deployed`，重新部署后才产生新的 Flowable 版本
- APF-METADATA-LAYER-ADOPT-001 能证明实例接管后的 metadata-layer 不再被静态资产覆盖
- APF-RESET-DESIGN-001 能证明 reset 操作的完整产品语义：下线当前可发起流程、保留 Flowable 历史部署和流程实例、用来源元数据覆盖当前模型、设置 `status=draft`、清空 `processDefinitionId`、设置 `adopted=false`，并要求后续显式 deploy 生成新的引擎版本；不得使用物理删除替代
- APF-METADATA-LAYER-REMOVE-001 能证明删除未接管静态 metadata-layer 后不可新发起，但历史部署、历史实例和审计数据仍保留
- APF-RUNTIME-REFRESH-001 能证明热刷新复用启动刷新主链，而不是走独立旁路
- APF-SUBMIT-001 能证明 runtime `init-form` 和 `start` 成功执行，流程实例启动
- APF-CONDITION-XOR-001 能证明稳定 V2 条件分支不依赖 `GatewayNode`，而是通过普通节点多出边和 `condition` 选择唯一命中分支
- APF-OR-JOIN-001 能证明分流合流通过普通多出边和显式 `JoinNode(joinType=OR)` 表达，命中分支都完成后才继续
- APF-NODE-TYPES-001 / APF-SUBFLOW-001 能证明当前稳定节点类型均被运行时或衔接用例覆盖，其中 `CcNode` 不产生人工待办，`SubflowNode` 必须先部署 child 再部署 parent
- Batch 4 业务模型主闭环必须以新建/编辑表单提交按钮作为第一入口，证明 `submit -> start-process -> processInfo -> approval page -> submitTask -> timeline` 全链路成立；列表页“发起流程”只作为补充/手动入口验证
- Batch 4 不得用公共 approval workflow start API、approval-center 发起页或 API 直调替代业务模型主闭环成功证据；approval-center 是后续聚合能力，不是业务模型流程的必需中心入口
- 基础审批动作（approve / reject / rollback）执行后，任务/状态/历史符合预期
- APF-ADVANCED-DIALOGS-001 必须证明普通审批人能加载 approval-center 四个高级动作弹窗 schema，selector GET 成功返回可选项，且 confirm action 来源于弹窗 schema 的 canonical task API 配置
- 高级审批动作（transfer / addSign / freeReject / freeApprove）执行后，payload 目标、任务流转和历史证据符合预期
- 多用户、串行会签、并行会签与通过比例场景均能给出明确状态或任务分布证据
- 审批历史、流程图、完成态、表单数据传递在运行时端可追溯
- 当执行失败时，证据足够区分环境问题、产品缺陷、或测试契约/selector 漂移

## Failure Classification

- 依赖 / 环境问题：
  - develop 或 mothership 未启动，或 bootstrap 未完成
  - `/actuator/health` 持续返回 503，说明 lifecycle 未完成
  - nginx 未暴露 `/actuator/`，导致外部健康检查失真
  - app-metadata-root 或 approval-workflow seeds 未挂载/未生效
  - `approval-workflow-users-seed` 未执行，或未生成当前数据库对应的 `approval-workflow-identities.json` employeeId 映射
- 后端问题：
  - 业务模型创建、保存、提交、回滚接口失败
  - 审批流程定义创建/更新静态元数据失败
  - 应用元数据刷新、运行时刷新、部署或下线失败
  - 设计时重置接口缺失、未恢复静态元数据、错误删除 Flowable 历史、未清空 `processDefinitionId` 或未将 `adopted` 置回 `false`
  - `init-form` / `start` 失败
  - 审批动作提交失败或状态流转错误
  - Flowable 引擎部署/实例/任务查询异常
- 前端壳问题：
  - `/login`、develop-shell、runtime-shell 或审批管理页面不可达
  - 微前端 entry 映射错误，导致设计器/运行时页面未正确加载
  - 浏览器 console/pageerror 暴露编译或运行时异常
- 页面交互 / 契约漂移问题：
  - 测试仍使用过期路由、字段名、按钮文案、selector
  - 把业务模型创建误当成“表单设计器”流程
  - 把开发平台保存误当成运行时部署动作
  - 高级动作弹窗或 selector 在 UI 中无实现/实现不一致
- 权限与上下文问题：
  - 审批人无法看到待办任务或无法执行对应动作
  - 用户/组织上下文、候选人、会签目标解析失败

## Evidence Requirements

- `tmp/testing-evidence/workflow-approval-flow/**` 下的 fixture harness、Playwright、日志和差异报告
- `/actuator/health`、Docker 容器状态、bootstrap 输出、nginx `/actuator/` 验证证据
- 业务模型创建/保存/提交证据：页面、网络请求、后端日志、必要时元数据证据
- 审批流程定义创建和绑定证据：页面、网络请求、`workflow/{name}.workflow.json` 静态文件证据
- 应用元数据刷新证据：metadata path、刷新日志、运行时 `WorkflowModel` 中 `source/adopted/版本/processDefinitionId` 状态
- 运行时部署证据：页面、网络请求、definition 状态或引擎部署证据
- 设计时重置证据：reset 前 deployed/adopted 状态、reset 后 draft/空 `processDefinitionId`/`adopted=false` 状态、Flowable version history 保留、显式 redeploy 后新 `processDefinitionId`
- 业务模型工作流配置证据：`workflow.bindings[0].processId`、`workflow.bindings[0].statusField`、`workflow.bindings[0].statusMap.*` 已写入的页面或接口证据
- runtime 发起与审批动作证据：浏览器页面、网络请求、timeline/diagram/task/instance 查询证据
- approval-center 聚合入口证据：`advanced-action-dialogs.json` 记录普通审批人、taskId、dialogId、selectorUrl、selectorStatus、optionCount 和 `approval-center-dialog-schema-confirm-ajax`
- 业务模型主闭环证据：`business-model-journey.json` 记录新建/编辑表单 submit 保存业务记录后调用 `/api{modelPath}/${saveResult.id}/start-process`，列表查询 `attachProcessInfo` 后进入 `/{id}/approval?instanceId=...&taskId=...`，处理页通过业务模型 `submitTask` wrapper 推动执行，最终 timeline 可见
- 手动入口对照证据：列表页“发起流程”仅作为无实例且状态允许时的补充入口，不能替代表单 submit 第一入口的通过标准
- 多用户切换、会签、历史和完成态证据
- 出现失败时，对应的后端日志、浏览器 console/pageerror、以及环境诊断结论


## 结果型执行的页面契约

`APF-RESULT-UI-001/002`：页面通过统一实例信息查询读取状态和能力，不以执行引用推断本地任务。业务列表沿审批案件找到当前执行（终态读取最后一次执行），既有内置流程仍支持审批地址反查。详情中的流程图、审批记录分别受 `diagramAvailable`、`timelineAvailable` 控制；只有 `externalUrl` 存在才提供外部链接，实例取消由 `cancellable` 控制。RESULT_ONLY 不生成任务、不显示本地任务动作。记录能力由适配器 Registry 派生，记录查询复用 `ExternalRecordReader`，不回退内置引擎。

前端组件集成验证位于 `frontends/web/main-app/tests/integration/approval-workflow/result-only-presentation.spec.js`，使用真实发布 Schema 和 Amis 渲染器、受控 API 响应，证明按钮与请求行为；不替代真实供应商账号联调。

先运行 `BusinessModelWorkflowEntryUiSchemaContractTest` 生成 `ouroboros-business-model-default/target/generated-test-schemas/process-detail.json`，再于 `frontends/web/main-app` 运行 `OB_TEST_GENERATED_PROCESS_DETAIL=<生成文件的绝对路径> node node_modules/@playwright/test/cli.js test tests/integration/approval-workflow/result-only-presentation.spec.js --config=playwright.config.js`，即可额外验证真实投射的业务详情页。不提供路径时，该跨构建产物用例明确跳过，其余管理端场景仍执行；不能据此宣称业务详情页已验收。
