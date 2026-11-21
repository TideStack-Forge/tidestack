# 审批中心「可发起流程」接口契约与发起入口规格

> 状态：Accepted（2026-09-12 依据实现补写）
> 适用范围：审批中心发起页（`/approval-center/start`）的流程选择列表与发起跳转；接口 `GET /api/approval-workflow/instance/available` 的对外契约（一次返回全部分组、`total` 口径、`page`/`perPage` 保留但不截断）。
> 相关设计：[ouroboros-approval-workflow.md](../../design/modules/core/ouroboros-approval-workflow.md)；用户手册：[approval-center/index.md](../../handbook/docs/guide/module/approval-workflow/approval-center/index.md)；关联规格：[外部流程引擎对接规格](external-engine.md)、[菜单扩展规格](../frontend/menu-extension.md)

## ① 背景与问题

1. 发起页的产品语义是"一次给全、前端按分类渲染"，不是分页列表；此前服务端先分页再按分类聚合，而取数不按分类排序，同一分类会被拆到相邻两页，表现为**卡片重复或翻页丢失**（`ApprovalWorkflowInstanceController.java:96-99`）。
2. 同一时期 `total` 用"本页聚合出的分类数"充当，与 `items`（分类分组）口径不一致，调用方无法判断含义（同上 `:100-101`）。
3. 需要把这条契约写成产品规格：接口是"服务型接口"，前端不做分页 UI，调用方不得用 `total` 推断分组数。

## ② 目标

1. 一次请求返回取数上限内的全部分类分组（当前最多 1000 条流程），与传入的 `page` / `perPage` 无关。
2. `total` 明确为**可发起流程总数**（流程条数），`items` 为分类分组数组，两者口径分离且都在响应里可见。
3. 发起页按分类渲染卡片，卡片显示流程名称与描述，点击进入该流程的发起表单。
4. 保留 `page` / `perPage` 作为兼容入参（前端仍在传），但不参与截断。
5. 取数规模有明确上限，防止定义数量异常时把整表读进内存。

## ③ 非目标

- 不实现后端按分类聚合后的真分页（当前前后端契约均不支持）。
- 不实现关键字搜索、分类筛选、排序控件（接口虽有 `keyword` / `category` 形参，控制器固定传 `null`）。
- 不在本页做发起前置校验（发起需求、自选审批人在发起表单页处理）。
- 不定义空分组占位、空列表插图等空态视觉。

## ④ 用户故事

1. 作为审批发起人，我希望在一页里看到全部可发起流程并按分类浏览，以便不用翻页找流程。
2. 作为审批发起人，我希望每个分类显示流程数量，以便判断该分类下有多少可选项。
3. 作为审批发起人，我希望点卡片直接进入该流程的发起表单，以便少一次选择。
4. 作为二次开发者，我希望接口一次返回全量分组且 `total` 含义明确，以便直接按分组渲染而不必猜测分页语义。

## ⑤ 功能规格

**接口契约（`GET /api/approval-workflow/instance/available`）**

- 路径前缀 `/api/approval-workflow/instance`（`ApprovalWorkflowInstanceController.java:56`），方法 `getAvailable`（`:115-129`）。
- 入参 `page` / `perPage` 均为可选 `Integer`，**保留为兼容入参、不参与截断**：传任意值都返回全部分组（`:117-118`、`:104-105`）。
- 服务端固定以 `searchAvailableByPage(null, null, 1, ALL_AVAILABLE)` 取数，`ALL_AVAILABLE = 1000`（`:64`、`:119`）。
- 可用性判定：仅 `status = 'deployed'` 的流程模型；排序 `saveAt desc`（`WorkflowModelServiceImpl.java:180-209`）。
- 响应为 `PagedWebApiResult<ModelCardResult>` = `{code, msg, data: {total, items}}`（`PagedWebApiResult.java:47-49`）：
  - `data.total` = 可发起流程**总条数**（`m.count(where)`），**不得**用它推断分组数；
  - `data.items` = 分类分组数组，元素 `{title, cards}`，`title` 为分类名（`ModelCardResult`）；
  - `cards` 元素 `{id, category, name, description, icon, processDefinitionId}`（`CardItemResult`）。
- 分组顺序 = 分类在 `saveAt desc` 结果中**首次出现的顺序**（`LinkedHashMap`，`:120-127`），不是字典序。
- 分类为空/未设置时 `title` 为 `null`，前端渲染为 `未分类`。
- `items` 为空数组时表示当前无可发起流程；接口仍返回成功（`code` 正常），不返回错误。

**发起页用户可见行为（`start.ui-schema.json`）**

- 页面 `/approval-center/start` 以 `service` 组件请求上述接口，固定带 `page=1&perPage=1000`（`start.ui-schema.json:9-16`）。
- 右上角「刷新」按钮 `actionType: reload`、`target: service`，重新拉取并重渲染列表（`:24-32`）。
- 以 `each name="items"` 遍历分组，每组渲染一个 `cards`，组标题为 `<分类名> — <卡片数>`，`source: ${cards}`（`:35-40`）。
- 卡片标题为 `名称` + `name` 标签；正文为 `描述：${description}`；左侧为图标位（`:47-56`）。
- 点击卡片（`itemAction`）以 `link` 跳转 `/approval-center/start/init-form?id=${id}`（`:42-46`），即进入发起表单页。
- 卡片工具条「查看」打开「流程信息」弹窗，地址 `/approval-center/start/process-detail`（`:57-77`）。
- 前端**不做分页 UI、不做前端分页**：没有页码控件，也没有 `total` 的展示位。

**后续发起链路的拒绝语义（相邻接口，便于判断边界）**

- 发起表单：`GET /{flowKey}/init-form`（`ApprovalWorkflowInstanceController.java:139-141`）。
- 发起提交：`POST /{flowKey}/start`；失败时返回 `WebApiResult.error(<原因>)`，即业务失败以错误消息而非异常页面呈现（`:306-334`）。

## ⑥ 验收口径

- [ ] 传 `page=2&perPage=1` 与传 `page=1&perPage=1000`，`data.items` 内容与分组数完全一致（`ApprovalWorkflowInstanceControllerTest.java:350-375`）。
- [ ] `data.total` 等于全部 `cards` 条数之和（在可发起流程 ≤ 1000 时）；不等于分组数。
- [ ] 同一分类的流程不会跨组出现：每个分类在 `items` 中只出现一次。
- [ ] 发起页对同一分类只渲染一个分组标题，标题形如 `<分类名> — <n>`；分类缺失时显示 `未分类`。
- [ ] 点击卡片跳转到 `/approval-center/start/init-form?id=<流程 id>`；「刷新」按钮重新请求接口。
- [ ] 无流程时页面不报错，仅显示刷新按钮而无卡片。
- [ ] `bash scripts/docs/check-links.sh` 输出 `No broken links found`。

## ⑦ 已知限制与未实施项

- 取数上限 1000：`total` 来自不带分页的 `count`，而 `items` 只包含最多 1000 条流程；当可发起流程超过 1000 时 **`total` 大于 `cards` 条数之和**，前端无法感知截断。
- 分组顺序依赖 `saveAt desc` 的首次出现顺序，同一分类的位置会随最近保存时间漂移。
- 接口层没有关键字/分类筛选入口（控制器固定传 `null`），发起页也没有搜索框与分页控件。
- 空列表、空分组没有专门占位文案。
- 未核实：真实环境下可发起流程的规模分布，以及超过 1000 条时的产品预期。

> 与实现不一致：`getAvailable` 的 `@Operation` 描述为"当前用户可发起的流程卡片"（`:109`、`:92-94`），但实现调用 `searchAvailableByPage(null, null, 1, 1000)`，服务实现仅以 `status='deployed'` 过滤（`WorkflowModelServiceImpl.java:180-209`），调用链中**未见按当前用户的权限过滤**；是否由数据层行级权限兜底未核实。

## ⑧ 相关文档

- 模块设计：[ouroboros-approval-workflow.md](../../design/modules/core/ouroboros-approval-workflow.md)
- 用户手册：[approval-center/index.md](../../handbook/docs/guide/module/approval-workflow/approval-center/index.md)
- 关键实现：[ApprovalWorkflowInstanceController.java](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-web/src/main/java/com/ouroboros/workflow/approval/web/controller/ApprovalWorkflowInstanceController.java)、[WorkflowModelServiceImpl.java](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-manage/src/main/java/com/ouroboros/workflow/approval/manage/service/impl/WorkflowModelServiceImpl.java)、[start.ui-schema.json](../../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-web/src/main/metadata/approval-center/start.ui-schema.json)
