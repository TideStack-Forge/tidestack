# Approval Workflow Regression Cases

| Case ID | Objective | Preconditions / Input | Owner Surface | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| APF-RESULT-UI-001 | 结果型执行的详情与跳转不依赖本地任务 | RESULT_ONLY 执行；分别有/无 externalUrl；无流程图、记录能力 | generated business detail + instance detail | integration | 不请求本地任务、流程图或不支持的时间线；有地址才显示且能打开外部审批；仍展示真实状态与结果 | schema output, browser requests, screenshots |
| APF-RESULT-UI-002 | 实例操作按引擎能力显示 | 运行中实例，分别支持/不支持取消；内置与外部引擎 | instance list | integration | 仅 cancellable=true 时显示强制结束，点击后请求正式取消接口；已终态和不支持的引擎不显示该操作 | service assertions, browser requests, screenshots |
| APF-TOPO-001 | 验证双运行时拓扑、health、fixture 和身份前置条件 | `develop`、`mothership`、`develop-shell`、`runtime-shell` 可访问；`/actuator/health` 可区分 503/200；approval seeds 已加载；`approval-workflow-users-seed` 已重建 `awf.approver1/2/3` 并输出 employeeId 映射 | harness | harness | topology/preflight 报告先落证据，并明确运行时、前端、健康状态和 deterministic user seed mapping | fixture harness report, health logs, bootstrap output, approval-workflow-identities.json |
| APF-FORM-001 | 创建并配置业务模型（非简单表单设计器） | 管理员可登录 develop-shell；已知业务模型真实入口 `/dev/{appName}/business-model`；可访问新建页 `/create`；至少确认 `name`、`title` 为必填 | develop-shell | e2e | 业务模型按真实页面成功创建，并完成最小必要配置与保存/提交语义验证；不再使用“新建审批表单”表达 | browser trace, network log, backend logs, metadata evidence |
| APF-FLOW-001 | 设计审批流程（单签） | 已有目标业务模型；approval workflow definition 管理入口可达；支持保存到应用元数据 | develop-shell | e2e | 单签流程定义创建并保存成功，生成 `workflow/{name}.workflow.json` 静态元数据文件 | browser trace, metadata file evidence |
| APF-FLOW-002 | 设计审批流程（会签/高级动作配置） | APF-FLOW-001 已完成；设计器可编辑定义；高级动作范围限定为 transfer/addSign/freeReject/freeApprove | develop-shell | e2e | 会签（串行/并行）和高级动作配置保存成功，静态元数据文件版本更新且可回读 | browser trace, metadata file evidence |
| APF-BIND-001 | 完成流程定义绑定与业务模型工作流绑定 | 已有业务模型和流程定义；approval workflow base-info API 可返回 form 列表/字段；业务模型启用 workflow 特性 | develop-shell | e2e | 两类绑定均成功：definition 侧 `form.formType/form.formId` 绑定成立；业务模型侧 `workflow.bindings[0].processId` / `workflow.bindings[0].statusField` / `workflow.bindings[0].statusMap.*` 写入成功 | browser trace, base-info API evidence, business-model save evidence |
| APF-METADATA-001 | 验证审批流静态元数据资产 | APF-FLOW-001/002 已完成；应用元数据目录可读 | app-metadata-root | harness | 每条流程只有 `workflow/{name}.workflow.json` 单文件，含 `formatVersion/name/title/version/enabled/form/nodes/edges`，不含运行态字段 | metadata JSON evidence |
| APF-ASSET-001 | 开发平台保存后静态流程定义文件存在 | APF-FLOW-001/002 已完成；保存动作已返回成功 | develop-shell + app-metadata-root | harness | 保存只产生 `workflow/{name}.workflow.json`，没有运行时 `WorkflowModel` 部署字段，没有 `.json + .meta.json` 双文件 | metadata file evidence, save network evidence |
| APF-ASSET-COMMIT-001 | definition 与应用元数据版本提交一致 | APF-ASSET-001 已完成；开发平台提交入口可用 | develop-shell + app-metadata-root | harness | 提交动作单独执行并固定应用元数据版本；提交后的 `version` 与静态 definition 内容一致 | commit network evidence, metadata revision evidence |
| APF-RUNTIME-BOOT-001 | 基座使用指定 app root 启动 | APF-ASSET-COMMIT-001 已完成；mothership 指向当前 app metadata root | mothership | harness | 基座从 `AppContext.homePath` 加载应用元数据，服务 READY 前完成审批流资产发现校验 | runtime logs, health evidence |
| APF-RUNTIME-LAYER-001 | 基座刷新 metadata-layer 但 Flowable 尚无部署 | APF-RUNTIME-BOOT-001 已完成；静态资产合法 | mothership | harness | `WorkflowModel` 为 `source=metadata-layer/adopted=false/status=draft/processDefinitionId=""`，Flowable 未出现该流程部署 | WorkflowModel evidence, Flowable deployment query evidence |
| APF-RUNTIME-LAYER-002 | 基座从应用元数据刷新运行时模型 | APF-METADATA-001 已完成；mothership 运行或刷新应用元数据 | mothership | harness | 应用实例发现 `*.workflow.json` 并刷新 `WorkflowModel`，`source=metadata-layer`、`adopted=false`、来源版本正确、未部署时无 `processDefinitionId` | refresh logs, WorkflowModel evidence |
| APF-BUSINESS-CORE-001 | 通过业务模型主入口发起并推动审批 | APF-BIND-001 已完成；业务模型 add schema 包含 submit 后 `start-process` 动作；已部署流程可产生审批人待办 | develop-shell + business-model generated wrapper | e2e | 新建业务记录后按表单提交链调用 `/api{modelPath}/${saveResult.id}/start-process`，列表回读 `processInfo`，业务模型 approval 页 wrapper `submitTask` 推动任务完成，业务状态为 `APPROVED` 且 timeline 可见 | business-model-journey.json, browser trace, processInfo evidence, timeline evidence |
| APF-DEPLOY-001 | 部署运行时审批流程定义到流程引擎 | APF-RUNTIME-LAYER-002 已完成；运行时 `WorkflowModel.name` 已知；deploy API 可达 | mothership | harness | 运行时 deploy 成功，模型状态变为 deployed，部署后才产生 `processDefinitionId`，并能从 Flowable/definition 侧验证部署结果 | deploy API evidence, runtime logs, Flowable/deployed status evidence |
| APF-METADATA-LAYER-UPDATE-001 | 高版本刷新进入 updated 并重新部署新引擎版本 | APF-DEPLOY-001 已完成；开发平台保存更高 `version` 的同名静态定义并单独提交应用元数据 | develop-shell + mothership | harness | refresh 后运行时模型从 deployed 变为 updated 且不再可发起；显式 deploy 后变回 deployed 并生成新的 Flowable processDefinitionId，新实例使用新版本 | metadata diff, WorkflowModel before/after, Flowable version evidence |
| APF-METADATA-LAYER-ADOPT-001 | 接管后不被 metadata-layer 覆盖 | APF-RUNTIME-LAYER-001 已完成；运行时对 metadata-layer 定义做过实例定制并标记 adopted | mothership | harness | 后续同名更高版本静态资产刷新不覆盖实例定制内容，adopted 记录保持实例修改 | WorkflowModel diff evidence, refresh logs |
| APF-RESET-DESIGN-001 | 重置流程并保留 Flowable 历史 | 已存在真实 `source=metadata-layer` 流程；该流程可部署，且运行时实例定制后 `adopted=true` | runtime-shell | e2e | reset 内部先下线当前引擎定义，因此不能再发起新实例，但 reset 前已发起实例仍可继续流转；随后当前模型被来源元数据覆盖、`status=draft`、`processDefinitionId` 清空、`adopted=false`；Flowable 历史部署/版本仍可查询；后续显式 deploy 生成新 `processDefinitionId` 并恢复发起 | reset API evidence, blocked/new start evidence, existing task completion, WorkflowModel before/after, version history evidence |
| APF-METADATA-LAYER-REMOVE-001 | 删除静态 metadata-layer 后不可新发起但历史保留 | APF-DEPLOY-001 已完成；静态文件从应用元数据删除并提交 | develop-shell + mothership | harness | 未接管 metadata-layer 通过下线机制不可新发起；Flowable 历史部署、历史实例、审计数据仍可查询 | metadata deletion evidence, availability query, history evidence |
| APF-RUNTIME-REFRESH-001 | 基座刷新测试容器的开发 workspace | APF-ASSET-COMMIT-001 已完成；测试容器存在且支持 workspace reload | develop-shell + mothership | harness | 通过正式 app-container reload 将开发平台 app workspace 刷新到测试容器，运行时发现/校验/refresh `workflow/*.workflow.json` 并产生 `source=metadata-layer` 模型；不通过运行时 definition API 伪造 | container reload evidence, refresh logs, WorkflowModel evidence |
| APF-SUBMIT-001 | 发起审批并启动流程实例 | 已部署流程可用；runtime-shell 可登录；`init-form` / `start` API 可达；必要 `taskAssignee` 数据已就绪 | runtime-shell | e2e | `init-form` 成功返回起始表单，提交后 `start` 成功，流程实例启动并产生首个待办 | browser trace, start API evidence, instance/todo evidence |
| APF-TASK-001 | 验证待办任务分配 | 流程实例已启动；首个审批人身份可登录 | runtime-shell | e2e | 正确审批人在待办列表中看到对应任务，非目标用户不可误见/误领 | browser trace, todo task query evidence |
| APF-CONDITION-XOR-001 | 条件判断驱动 XOR 分流 | `awf.condition.xor` fixture 已部署；金额字段参与条件判断 | runtime-shell | e2e | `amount <= 1000` 只进入低额审批人待办；`amount > 1000` 只进入高额审批人待办；定义中不使用 `GatewayNode` | topology-and-reset.json, todo task evidence |
| APF-OR-JOIN-001 | 多分支 OR 分流并在显式合流节点汇聚 | `awf.or.join` fixture 已部署；分支汇入 `JoinNode(joinType=OR)` | runtime-shell | e2e | 两个分支审批任务均被创建；单个分支完成后流程仍等待另一分支；全部完成后流程继续至结束且无残留待办 | topology-and-reset.json, branch task evidence |
| APF-NODE-TYPES-001 | 覆盖稳定 V2 节点类型链路 | `awf.node.types` fixture 已部署；办理人、审批人、抄送候选人均可解析 | runtime-shell | e2e | `StartNode -> HandleNode -> CcNode -> ApprovalNode -> EndNode` 顺序可运行；HandleNode 使用 `complete`；CcNode 不产生人工待办但不阻断后续审批 | topology-and-reset.json, handle/approval task evidence |
| APF-SUBFLOW-001 | 覆盖子流程节点调用 | 子流程 child 先部署；parent 的 `SubflowNode.subFlowKey` 指向 child engine key | runtime-shell | e2e | parent 启动后进入 child 任务；child 审批完成后 parent 子流程调用返回并结束；历史实例保留 | topology-and-reset.json, child task evidence |
| APF-APPROVE-001 | 执行审批通过动作 | 审批人拥有待办任务；对应动作在当前节点可用 | runtime-shell | e2e | `approve` 成功，流程流转到下一节点，timeline/任务状态同步变化 | browser trace, task submit evidence, timeline evidence |
| APF-REJECT-001 | 执行审批拒绝动作 | 审批人拥有待办任务；节点允许 reject | runtime-shell | e2e | `reject` 成功，流程结束或进入拒绝分支，timeline 与实例状态可追踪 | browser trace, task submit evidence, instance state evidence |
| APF-ROLLBACK-001 | 执行退回动作 | 审批人拥有待办任务；节点允许 rollback；可选目标节点存在 | runtime-shell | e2e | `rollback` 成功，任务回到目标节点或上一步节点，timeline 和后续待办符合预期 | browser trace, task submit evidence, timeline evidence |
| APF-ADVANCED-DIALOGS-001 | 验证高级动作弹窗和 selector 来源 | 普通审批人拥有可执行 transfer/addSign/freeReject/freeApprove 的待办任务；approval-center form schema 已注册 | runtime-shell | e2e | 四个 dialog schema 可加载，selector GET 返回 200 且有可选项，confirm action 指向 canonical task API；不以脚本 direct POST 替代弹窗证据 | advanced-action-dialogs.json, selector network evidence, dialog schema evidence |
| APF-TRANSFER-001 | 执行转办动作 | 审批人拥有待办任务；transfer 目标用户解析可用 | runtime-shell | e2e | `transfer.targetUserId` 提交成功，任务转移到目标审批人，原审批人不再持有该待办 | browser trace, task submit evidence, reassignment evidence |
| APF-ADDSIGN-001 | 执行前加签 / 顺序加签 | 审批人拥有待办任务；addSign 目标用户解析可用 | runtime-shell | e2e | `addSign.targetUserIds + BEFORE_CURRENT` 生效，新审批人任务按顺序插入 | browser trace, task submit evidence, task sequence evidence |
| APF-ADDSIGN-002 | 执行后加签 / 并行或后置加签 | 审批人拥有待办任务；addSign 目标用户解析可用 | runtime-shell | e2e | `addSign.targetUserIds + AFTER_CURRENT` 生效，新审批人任务以后置/并行预期方式插入 | browser trace, task submit evidence, task creation evidence |
| APF-FREEREJECT-001 | 执行自由驳回动作 | 审批人拥有待办任务；历史节点可选 | runtime-shell | e2e | `freeReject.targetNodeId` 提交成功，流程回到指定历史节点 | browser trace, task submit evidence, timeline evidence |
| APF-FREEAPPROVE-001 | 执行自由审批动作 | 审批人拥有待办任务；允许跳转/协商用户数据已准备 | runtime-shell | e2e | `freeApprove` 成功，流程按目标语义跳转，相关审批/协商数据可追踪 | browser trace, task submit evidence, process state evidence |
| APF-COUNTERSIGN-001 | 验证会签串行模式 | 会签节点配置为串行 | runtime-shell | e2e | 审批人按顺序依次收到任务，前一个完成后下一个才激活 | browser trace, task sequence evidence |
| APF-COUNTERSIGN-002 | 验证会签并行模式 | 会签节点配置为并行 | runtime-shell | e2e | 多个审批人同时收到任务，任务分布与节点配置一致 | browser trace, parallel task evidence |
| APF-COUNTERSIGN-003 | 验证会签通过比例 | 会签节点配置通过比例（例如 60%） | runtime-shell | e2e | 达到通过比例后流程继续，未达到则保持/结束为预期状态 | browser trace, process ratio evidence |
| APF-HISTORY-001 | 验证审批历史记录 | 流程已执行多个审批动作 | runtime-shell | e2e | timeline/history 完整记录时间、人员、动作、意见和关键状态变化 | browser trace, history query evidence |
| APF-COMPLETE-001 | 验证流程完成与流程图展示 | 所有审批节点已通过 | runtime-shell | e2e | 流程状态为已完成，完成态详情和流程图/diagram 数据可查询 | browser trace, completion evidence, diagram evidence |
| APF-MULTIUSER-001 | 验证多用户协作审批 | 多个审批人参与同一流程，且 deterministic user seed 已生成当前数据库 employeeId 映射 | runtime-shell | e2e | 任务在多用户之间正确流转，用户视角差异与流程状态一致 | browser trace, multi-user evidence |
| APF-ACTION-VISIBILITY-001 | 验证审批动作按钮可见性 | 打开待办详情；节点动作配置已知 | runtime-shell | e2e | 基础动作与高级动作按钮按当前节点配置正确显示/隐藏 | browser screenshot, UI/API config evidence |
| APF-ACTION-VALIDATION-001 | 验证审批动作参数校验 | 执行需要目标用户/目标节点/附加参数的动作 | runtime-shell | e2e | 缺失或错误参数时给出明确提示；提供有效参数后可继续提交 | browser trace, validation error evidence |
| APF-COMMENT-001 | 验证审批意见提交 | 审批人执行需要意见的动作 | runtime-shell | e2e | 审批意见成功保存，并可在 timeline/history 中查看 | browser trace, comment persistence evidence |
| APF-FORMDATA-001 | 验证表单数据传递 | 提交时包含业务数据；流程实例已启动 | runtime-shell | e2e | 表单数据正确传递到流程变量/详情页，审批人可查看关键业务字段 | browser trace, process variable evidence |
| APF-DYNAMIC-FORM-001 | 验证默认动态表单与审批流运行时衔接 | 默认动态表单 `awf-expense-request` 已部署；运行时可创建并部署审批流；字段源 API 可返回表单字段 | runtime-shell | e2e | 字段列表同时提供字段权限与条件表达式上下文；默认动态表单流程可发起、待办字段权限可见；实例与表单数据写入集中绑定表，审批时表单变更写入可查询历史 | dynamic-form-workflow.json, field-source API evidence, binding/history API evidence |

## Development Platform To Runtime Manual Acceptance Cases

详细字段级测试数据、关系图和逐步操作见 [dev-to-test-manual-cases.md](dev-to-test-manual-cases.md)。本节只登记正式 case ID，避免手工验收用例脱离场景事实来源。

| Case ID | Objective | Preconditions / Input | Owner Surface | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| APF-DT-000 | 环境与账号预检 | health、admin、awf.submitter、awf.approver1/2/3 | develop-shell + runtime-shell | manual | health 成功，管理员和审批用户均可登录 | health response, login screenshots |
| APF-DT-010 | 创建业务模型 | `awf-expense-request` 业务模型字段、API、视图、workflow 配置 | develop-shell | manual | 模型保存和提交成功，可在列表与编辑页回读 | model screenshots, save/commit network evidence |
| APF-DT-011 | 验证业务字段校验 | `BM-ERR-REQUEST-NO`、`BM-ERR-AMOUNT-MIN`、`BM-ERR-CURRENCY` | develop-shell + runtime-shell | manual | 错误数据被阻止提交且不产生实例 | validation screenshots, no-instance evidence |
| APF-DT-020 | 创建审批流定义 | 流程 A/B/C/D 定义数据 | develop-shell | manual | 四条流程保存成功，节点、候选人、动作配置可回读 | definition JSON, editor screenshots |
| APF-DT-021 | 完成表单绑定与业务模型工作流绑定 | `form.formType/form.formId`、`workflow.bindings[0].*`、状态映射 | develop-shell | manual | 两类绑定均完整保存 | binding screenshots, model JSON evidence |
| APF-DT-022 | 验证静态元数据文件 | 流程 A/B/C/D 已保存 | develop-shell + app-metadata-root | manual | 四个 `.workflow.json` 文件字段正确且无运行态字段 | metadata JSON evidence |
| APF-DT-023 | 基座刷新运行时模型 | 流程 A/B/C/D 静态元数据 | mothership + runtime-shell | manual | 运行时模型已刷新但未部署，`processDefinitionId` 为空 | refresh logs, WorkflowModel evidence |
| APF-DT-024 | 部署运行时模型 | 刷新后的流程 A/B/C/D | runtime-shell | manual | 部署后产生 `processDefinitionId`，测试平台发起入口可见 | deploy response, start page screenshot |
| APF-DT-030 | 发起审批 | `BM-1001` | runtime-shell | manual | 实例启动并生成一级审批待办 | start response, instance/task IDs |
| APF-DT-031 | 验证待办可见性和权限隔离 | `BM-1001` 实例 | runtime-shell | manual | 只有当前节点审批人可见待办 | multi-user screenshots |
| APF-DT-040 | 审批通过 | `BM-1001`，`approve` | runtime-shell | manual | 状态为 `APPROVED`，历史记录通过意见 | task response, timeline evidence |
| APF-DT-041 | 审批拒绝 | `BM-1002`，`reject` | runtime-shell | manual | 状态为 `VETO` 或拒绝态，历史记录拒绝意见 | task response, timeline evidence |
| APF-DT-042 | 审批退回 | `BM-1003`，`rollback` | runtime-shell | manual | 任务退回到预期节点或记录退回结果 | task response, timeline evidence |
| APF-DT-050 | 转办 | `BM-1004`，`transfer.targetUserId` | runtime-shell | manual | 任务转移到目标审批人 | selector response, reassignment evidence |
| APF-DT-051 | 当前节点前加签 | `BM-1005`，`addSign.position=BEFORE_CURRENT` | runtime-shell | manual | 加签任务先于当前审批完成 | task sequence evidence |
| APF-DT-052 | 当前节点后加签 | `BM-1006`，`addSign.position=AFTER_CURRENT` | runtime-shell | manual | 当前审批后激活加签任务 | task sequence evidence |
| APF-DT-053 | 自由驳回 | `BM-1007`，`freeReject.targetNodeId` | runtime-shell | manual | 流程回到指定历史节点 | free-reject target evidence, timeline |
| APF-DT-054 | 自由审批 | `BM-1008`，`freeApproval.consultUserIds` | runtime-shell | manual | `freeApprove` 成功提交，协商用户可追踪 | free-approval target evidence, timeline |
| APF-DT-060 | 串行审批 | `BM-1009` | runtime-shell | manual | 一级通过后才出现二级待办 | task sequence evidence |
| APF-DT-061 | 并行会签 100% | `BM-1010` | runtime-shell | manual | 两个审批人同时收到任务，全部通过后完成 | parallel task evidence |
| APF-DT-062 | 并行会签 50% | `BM-1011` | runtime-shell | manual | 达到 50% 通过比例后流程继续或完成 | ratio evidence, timeline |
| APF-DT-070 | 表单数据和状态回写 | `BM-1012` | runtime-shell | manual | 表单数据在详情可见，业务状态正确回写 | detail screenshot, record state evidence |
| APF-DT-080 | 历史、流程图和统计 | `BM-1013` | runtime-shell | manual | timeline、diagram、统计均能查询到该流程 | timeline, diagram, statistics screenshots |
| APF-DT-090 | 下线和版本更新 | 流程 A | develop-shell + runtime-shell | manual | 下线后不可新发起，重新部署后可发起 | offline/deploy evidence, start page screenshots |
