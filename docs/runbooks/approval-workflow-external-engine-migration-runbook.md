# 审批流外部流程引擎迁移 — `unmapped-policy=reject` 生产切换手册

> 适用范围：审批流外部流程引擎对接（`ability/ouroboros-approval-workflow-external-integration`）。
> 本手册只用于共同设计正文的兼容与切换策略"全量迁移"的最后一步：所有目标流程均已有 ACTIVE 外部映射后，
> 把未映射流程的发起策略从 `internal`（委托内置引擎）切换为 `reject`（拒绝发起）。
> 对应代码：`RoutedFlowInstanceService`（`UNMAPPED_POLICY_INTERNAL`/`UNMAPPED_POLICY_REJECT` 常量、
> `ouroboros.workflow.approval.external.unmapped-policy` 配置键、`WORKFLOW_ROUTE_NOT_CONFIGURED` 错误码）。

## 1. 适用场景与前提

- 仅适用于"已全量迁移"的切换：目标环境内每个需要走外部的流程都有 ACTIVE 且验证通过的映射（见共同设计的兼容与切换策略）。
- 灰度/部分迁移阶段**必须保持** `unmapped-policy=internal`（未映射流程继续由内置引擎执行，行为与迁移前一致）。
- 切换前必须完成第 2 节检查清单；不满足任一项**禁止切换**。

## 2. 切换前检查清单

- [ ] M21-T1 批量验证全绿：`POST /api/approval-workflow/engine-mappings/batch-validate` 返回 `failedCount=0`（权限点 `workflow.external.mapping.validate`）。
- [ ] 每个目标 flowKey 均有 ACTIVE 映射：`GET /api/approval-workflow/engine-mappings?status=ACTIVE` 核对（权限点 `workflow.external.mapping.view`）。
- [ ] 回滚预案已确认（见第 5 节），负责人已知晓回滚触发条件。
- [ ] 运行中实例处理决策已定：默认"旧实例继续由内置引擎处理直至自然完成"（见共同设计的运行中实例规则）；如需提前迁移使用
      `POST /api/approval-workflow/engine-mappings/{mappingId}/migrate-running`（显式运维操作，见附录 A）。

## 3. 切换步骤

1. 修改配置键 `ouroboros.workflow.approval.external.unmapped-policy`：值 `internal` → `reject`。
   - 经 ouroboros-configuration 配置界面或部署配置修改；生效方式（重启或热刷新）以平台配置变更机制为准
     （先确认当前部署的配置刷新行为，热刷新不支持时按部署窗口重启）。
2. 按第 4 节执行切换后验证；任一验证项失败 → 立即按第 5 节回滚。

## 4. 切换后验证

1. **未映射流程被拒**：对一个未配置映射的 flowKey 发起 → 发起失败，错误码 `WORKFLOW_ROUTE_NOT_CONFIGURED`，
   消息包含"流程未配置映射，且 unmapped-policy=reject 禁止发起"（与 `RoutedFlowInstanceServiceTest` 的
   `newRouted(UNMAPPED_POLICY_REJECT)` 用例断言一致）。
2. **已映射流程正常**：对已配置 ACTIVE 映射的 flowKey 发起 → 正常走外部引擎（发起成功并产生外部实例）。
3. **外部失败不回退**：外部引擎不可用/发起失败时 → 发起失败且**不会回退到内置引擎**（共同设计要求外部失败不回退；
   恢复依赖幂等查询、重试、对账与人工决策，而不是第二次启动内置流程，共同设计的失败处理规则）。

## 5. 回滚步骤

1. 修改配置键 `ouroboros.workflow.approval.external.unmapped-policy`：值 `reject` → `internal`（生效方式同第 3 节）。
2. 验证：对未配置映射的 flowKey 发起 → 恢复由内置引擎执行（发起成功）。
3. 回滚后排查切换验证失败原因（映射缺失/校验失败/连接问题），修复后重新按本手册第 2-4 节执行。

## 6. 运行中实例说明

- 切换/回滚只影响**新实例**的发起路由；运行中的实例继续使用其发起时绑定的映射版本或内置引擎，
  直至实例终止或完成对账（见共同设计）。
- `authority=OUROBOROS` 仅表示由内置引擎执行；外部待办镜像链路已删除，不再同步外部待办中心。

## 7. 已知边界

- `RoutedFlowTaskService`（任务动作）**无 unmapped-policy 概念**：任务动作按任务是否有外部绑定路由，
  未命中绑定直接委托内置引擎。`reject` 只作用于**实例发起**（`RoutedFlowInstanceService.startInternal`），
  禁止声称任务侧也被 reject。
- 批量验证、状态漂移对账（`ExternalStateReconcileJob`）、映射版本清理（`MappingVersionCleanupJob`）为
  配套治理机制，不随本手册切换而启停。调度作业默认不注册，必须显式设置
  `ouroboros.workflow.approval.external.scheduling.enabled=true` 后才启用；切换前应确认该开关与运维窗口、补偿策略一致。
- 迁移 API（`migrate-running`）不携带原实例的业务变量/表单数据（平台无按实例读变量 API），仅适用于
  无业务记录关联或允许外部重新发起的流程；有业务记录关联的流程迁移由运维在外部引擎重新发起。

---

## 附录 A：运行中实例迁移（显式运维操作）

- 端点：`POST /api/approval-workflow/engine-mappings/{mappingId}/migrate-running`（权限点 `workflow.external.mapping.activate`）。
- 行为：枚举映射 flowKey 的本地 RUNNING 实例（请求体可传 `instanceIds` 限定；不传则全量，上限 1000/flowKey），
  逐实例"外部重发起 → 写外部实例绑定（localInstanceId=原内置实例 ID）→ 取消内置实例"；
  单实例失败不中断整体，返回 `InstanceMigrationReportVO`（total/migratedCount/skippedAlreadyBoundCount/failedCount + 明细）。
- 原子性：外部发起成功并写入绑定后才取消内置实例；外部发起失败不动内置实例（共同设计的失败处理规则）。
- 默认生产节奏**不迁移运行中实例**（共同设计约定：新实例走外部，旧实例由内置引擎跑完）；本 API 供运维在业务窗口内显式执行。
- 回滚：迁移回滚 = 在外部引擎取消已发起实例，并由运维在内置引擎重新发起（绑定已存在时先清理绑定，人工确认）。
