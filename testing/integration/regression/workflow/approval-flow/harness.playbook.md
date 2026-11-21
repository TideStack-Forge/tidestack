# Approval Workflow Regression Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)。
2. 运行 fixture harness `check --mode plan`，先确认 develop、mothership、frontends、app-root、approval seeds 和 evidence 目录都已挂上同一轮执行上下文。
3. 确认以下环境变量已注入并指向当前运行环境：
   - `OB_TEST_BASE_URL`
   - `OB_TEST_APP_BASE_URL`
   - `OB_TEST_FRONTEND_BASE_URL`
   - `OB_TEST_RUNTIME_FRONTEND_BASE_URL`
4. 使用 `/actuator/health` 做健康判断：
   - 返回 503 → LifecycleGuardFilter 仍在拦截，等待生命周期完成
   - 返回 200 → 允许继续执行浏览器与 API 步骤
   - 外部访问不到 `/actuator/health` → 优先检查 nginx `/actuator/` 代理是否存在
5. 确认 app-root 指向真实元数据仓，而不是过期快照。
6. 确认以下数据种子已准备：
   - `approval-workflow-org-seed`
   - `approval-workflow-roles-seed`
   - `approval-workflow-users-seed`
   - `approval-workflow-business-model-seed`
   - `approval-workflow-flows-seed`
7. 执行确定性用户种子：`approval-workflow-users-seed` 必须在本轮浏览器执行前通过 lifecycle 重建 `awf.approver1/2/3` 和员工绑定；不要猜测密码或用户名。
   - 默认测试密码：`AwfRegression@2026!`
   - 可选覆盖：`OB_TEST_AWF_PASSWORD`
   - 运行输出：`tmp/testing-evidence/workflow-approval-flow/approval-workflow-identities.json`
8. 如果需要本地前端快速调试，预先确认微前端注入机制：
   - `sessionStorage.devMicroEntryMap`
   - key 是原始 shell entry，通常为相对路径
   - value 是本地 override URL
9. 明确本轮验证范围：
   - 业务模型创建与配置
   - 审批流程定义创建/编辑/绑定/部署
   - runtime 发起审批与基础/高级动作
   - 会签、历史、完成态、多用户协作

## Recommended Environment Bootstrap

1. 首选已确认的本地 browser E2E bootstrap：
   - `scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh`
2. 若使用 docker-compose，自行记录 compose 文件、镜像 tag、端口和宿主机路径映射。
3. 若本地缺镜像，不要切换到历史 `1.0.0-ciecc-prerelease` 假设路径；应使用根 `build.sh` 的当前命令和显式版本/tag 构造本地镜像，或记录环境阻塞。
4. bootstrap 完成后，先看健康状态，再看容器日志和前端壳是否可访问。

## Execution Order

### Phase 1: 拓扑与运行时前置验证（APF-TOPO-001）

1. 记录 develop/mothership/frontend 四个入口是否可访问。
2. 记录 `/actuator/health` 响应、容器状态、bootstrap 输出和 evidence 根目录。
3. 验证 admin 身份可登录 develop-shell；执行 `approval-flow-seed-users` 并验证 approver 账号能登录 runtime-shell。
4. 若 login 页元素与预期不符，优先按真实 shell 代码核对 `/login`、`#login-username`、`#login-password`、`.login-form__button`，不要退回旧 selector 猜测。

### Phase 2: 业务模型创建与配置（APF-FORM-001）

5. 以管理员身份登录 develop-shell。
6. 打开真实业务模型管理入口 `/dev/{appName}/business-model`。
7. 使用真实新建路径 `/dev/{appName}/business-model/create` 或对应 UI 入口创建业务模型。
8. 验证至少以下创建语义：
   - `name` 必填
   - `title` 必填
   - 创建成功后可进入编辑页 `/dev/{appName}/business-model/{fullName}`
9. 根据本场景需要补齐最小业务模型配置（字段/视图/API/工作流特性等），避免把步骤降格为“表单设计器”。
10. 验证“保存”和“提交”语义：
    - 保存：`PUT /api/dev/${appName}/business-model/${fullName}`
    - 提交：`POST /api/dev/${appName}/business-model/${fullName}/commit`，且 `commitMsg` 必填

### Phase 3: 审批流程定义创建与编辑（APF-FLOW-001 / APF-FLOW-002）

11. 打开 approval workflow definition 管理页面或其真实入口。
12. 通过真实流程定义 UI/API 创建单签流程定义。
13. 保存后验证应用元数据目录生成 `workflow/{name}.workflow.json`。
14. 再编辑该流程，补齐会签（串行/并行）和高级动作配置。
15. 保存更新时，确认静态文件 `version` 增加，且文件内 `name` 与文件名一致。
    - 保存只写静态资产，不触发运行时刷新或 Flowable 部署。
    - 保存完成后必须单独提交应用元数据版本，并记录提交证据；基座只能消费该已提交版本。

### Phase 4: 定义绑定与业务模型工作流绑定（APF-BIND-001）

16. 在审批流程定义管理面验证 definition → form binding：
    - 通过 `base-info` 相关能力选择 `form.formType` / `form.formId`
17. 在业务模型编辑面验证 workflow config binding：
    - `workflow.bindings[0].processId`
    - `workflow.bindings[0].statusField`
    - `workflow.bindings[0].statusMap.*`
18. 若 `workflow.isEnabled=true`，确认 `statusField` 和关键 statusMap 字段不为空。
19. 保存并记录两类绑定证据，明确它们是不同层面的配置。

### Phase 5: 静态元数据与运行时刷新验证（APF-METADATA-001 / APF-ASSET-* / APF-RUNTIME-* / APF-RUNTIME-LAYER-*）

20. 检查 `workflow/{name}.workflow.json`：只允许静态定义字段，不允许 `source/adopted/status/processDefinitionId/definitionId/id`。
21. 触发或等待 mothership 运行/刷新应用元数据，确认日志扫描 `workflow/*.workflow.json`。
22. 查询运行时 `WorkflowModel`，确认 `source=metadata-layer`、`adopted=false`、来源版本正确、未部署时 `processDefinitionId` 为空。
23. 若刷新失败，先看 metadata path、文件名与 JSON `name` 一致性、`formatVersion/version/form` 校验，不要直接跳到部署排查。
24. 验证未部署 metadata-layer 不在可发起列表中；只有显式 deploy 后才允许发起。
25. 热刷新验证必须复用同一条发现、校验、refresh 主链；不得使用运行时 definition API 作为开发到测试生命周期验收 setup。

### Phase 6: 流程部署与部署后验证（APF-DEPLOY-001）

26. 通过运行时 approval workflow definition deploy 动作执行：
    - `POST /api/approval-workflow/definition/{id}/deploy`
`{id}` 使用运行时 WorkflowModel 的主键，不用静态 definition 的 name 或 Flowable processDefinitionId。

27. 记录 definition 状态变化、processDefinitionId 或引擎部署成功证据。
28. 如需验证下线语义，单独使用 `POST /api/approval-workflow/definition/{id}/offline`，不要与 deploy 结果混淆。
29. 若部署失败，先看后端日志和 Flowable 部署链路，不要把失败归因到 develop save 或元数据层刷新。

进入发起审批前，补齐 metadata-layer 生命周期验收：

- APF-METADATA-LAYER-UPDATE-001：开发平台保存更高版本静态资产并单独提交应用元数据，热刷新后运行时模型应从 `deployed` 变为 `updated`，重新显式 deploy 后才生成新 `processDefinitionId`。
- APF-METADATA-LAYER-ADOPT-001：运行时接管后的 metadata-layer 再遇到同名高版本静态资产时，不得覆盖实例定制内容。
- APF-METADATA-LAYER-REMOVE-001：删除未接管静态 metadata-layer 后，应通过下线机制阻止新发起，但保留 Flowable 历史部署、历史实例和审计数据。
- APF-RUNTIME-REFRESH-001：以上更新/删除优先通过热刷新验证，确认不重启时仍复用启动时的发现、校验、刷新主链。

### Phase 7: 发起审批与任务分配（APF-SUBMIT-001 / APF-TASK-001）

30. 以 runtime-shell 用户身份进入审批发起页面。
31. 调用或间接触发 `GET /api/approval-workflow/instance/{flowKey}/init-form`，确认表单初始化成功。
32. 准备 `start` 必要数据：`formData` 与 `taskAssignee`（若当前流程定义需要）。
33. 提交发起请求：`POST /api/approval-workflow/instance/{flowKey}/start`。
34. 验证流程实例启动，首个审批人收到待办。

### Phase 8: 基础审批动作验证（APF-APPROVE-001 / APF-REJECT-001 / APF-ROLLBACK-001）

35. 以对应审批人身份登录 runtime-shell。
36. 打开待办列表并进入任务详情。
37. 验证详情页可查询 timeline / task-form-schema。
38. 分别验证：
    - approve
    - reject
    - rollback
39. 每个动作都应记录提交前后任务、timeline、实例状态证据。

### Phase 9: 高级审批动作验证（APF-TRANSFER-001 / APF-ADDSIGN-* / APF-FREEREJECT-001 / APF-FREEAPPROVE-001）

40. 每个高级动作优先用独立流程实例验证，避免状态互相污染。
41. `transfer`：验证 `targetUserId` 目标用户是否正确接收任务。
42. `addSign`：区分 `BEFORE_CURRENT` 与 `AFTER_CURRENT` 的任务插入语义。
43. `freeReject`：验证 `targetNodeId` 回退到指定历史节点。
44. `freeApprove`：验证目标跳转语义及相关 consult/附加数据。
45. 如果高级动作 UI selector 或弹窗缺失，立即记录为产品/UI 未实现或契约漂移，不得编造按钮或表单。

### Phase 10: 会签、历史、完成、多用户（APF-COUNTERSIGN-* / APF-HISTORY-001 / APF-COMPLETE-001 / APF-MULTIUSER-001 / APF-ACTION-* / APF-COMMENT-001 / APF-FORMDATA-001）

46. 为串行会签准备独立实例，验证任务按顺序激活。
47. 为并行会签准备独立实例，验证多个审批人同时持有待办。
48. 验证通过比例配置下的继续/终止语义。
49. 验证审批历史 timeline、diagram、完成态页面与数据可追踪。
50. 验证动作按钮可见性、参数校验、审批意见持久化和表单数据传递。
51. 验证多用户切换时，同一流程实例在不同用户视角下的任务分布正确。

## Observation Points

- fixture harness 是否先确认 topology、health、identity mapping
- business-model 创建/保存/提交 API 是否成功
- approval workflow definition 创建/更新/绑定静态元数据是否成功
- 应用元数据刷新后是否刷新运行时 `WorkflowModel`
- 运行时 deploy 后 definition 状态和引擎部署证据是否一致
- `init-form` / `start` 后实例是否真实启动
- 基础动作与高级动作是否按 payload 语义生效
- 会签是否按串行/并行/通过比例配置执行
- timeline/history/diagram/complete 证据是否彼此一致
- 浏览器 console/pageerror 是否暴露前端壳问题

## Diagnosis Branches

### A. 一直返回 503

- 先看 `/actuator/health`
- 若持续 503，说明生命周期未完成或卡死
- 查看 Docker 容器日志、Spring Boot 日志、bootstrap 输出

### B. `/actuator/health` 外部不可访问

- 检查 nginx 是否配置 `/actuator/` 反代
- 不要改用登录 API 冒充健康检查

### C. 登录或页面打不开

- 确认 `/login` 路由存在
- 核对真实登录字段与按钮
- 检查 console/pageerror 与前端构建日志

### D. 微前端页面行为异常

- 检查 `devMicroEntryMap` key 是否使用原始 shell entry
- 确认本地 override URL 是否可达
- 刷新页面后重新验证，而不是直接重建整套镜像

### E. 后端行为异常

- 看容器日志、应用日志、nginx 日志
- 若需要快速验证修复，优先使用 jar 替换 + 容器重启：
  - `/app/base/service/third-party-lib`
  - `/app/base/service/ouroboros-lib`
  - `/app/metadata/extend`

### F. 测试脚本与真实产品不一致

- 若 route/selector/action form 与仓库真相不符，归类为契约漂移或测试脚本过期
- 记录未解项并停止继续猜测执行

## Cleanup

1. 清理本轮拉起的不必要后台进程。
2. 清理浏览器会话、token、临时 override 配置。
3. 清理本轮专门构造的测试实例、临时业务模型或流程定义（如果清理不会破坏证据）。
4. 归档证据并记录：通过项、失败项、环境问题、契约漂移、以及下一轮应先执行的命令。

## Notes

- 高级动作建议独立实例验证，避免状态串扰。
- 多用户用例必须先确认 login-capable 身份映射。
- 若某一 UI 表单/selector 在仓库中未落地，应把它视为 discovery/blocker，而不是补一个想象中的交互。
- 健康检查永远以 `/actuator/health` 为准；登录 API 只可作为附加诊断信号。
