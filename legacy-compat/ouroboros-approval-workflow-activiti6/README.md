# ouroboros-approval-workflow-activiti6（已移出主线）

## 状态

本模块于 2026-09-11 从 `core/ouroboros-approval-workflow/` **移出主线**，保留在这里归档，不参与任何反应堆构建。

以下是移出时的历史状态；原审查过程可通过 Git 中迁移前的 `docs/exec-plans/completed/2026-09-11-approval-workflow-full-audit-and-remediation-plan.md` 版本追溯，不是当前有效文档入口：

- **不在反应堆里**：`core/ouroboros-approval-workflow/pom.xml` 的 `<modules>` 早已不含它，`bom/pom.xml` 只剩一条无人引用的 dependencyManagement 条目（已删除）。也就是说 CI 从不编译它，它与主线的语义漂移无人发现。
- **无法激活**：`qb-spring-boot-starter` 只依赖 flowable；唯一入口 `ouroboros.workflow.approval.engine=activiti6` 缺少必需的 SPI 实现（`TrustedFlowTaskActionService` 只有 Flowable 实现），切过去会在启动期失败。
- **语义已经分叉**，其中三处会写错数据：
  1. `withdraw` 无覆写 → 走 default 的"直接 cancel"，既绕过"仅发起人"校验，又把审批单写成 terminated 而不是 withdrawn；
  2. 任务完成事件缺少 `eventFactId`，下游按引擎拿到不同事件体；
  3. 加签任务完成事件在 activiti6 侧会派发、flowable 侧不派发（后者的动态加签任务确实没有 `taskDefinitionKey`）。
- 代码里仍有 `javax.el`/`javax.sql` 残留，与仓库的 Jakarta 基线不一致。

## 决策

按"该模块尚未上线、必须保证代码干净"的前提，决定**移出主线而不是补齐语义**：维护两套平行审批语义（`SingleApprovalProcessor` 归一化同构度仅 27%、`FlowInstanceQueryService` 仅 8%）的代价高于收益。

## 若将来仍需要这条路径

```bash
# 1) 迁回主线
git mv legacy-compat/ouroboros-approval-workflow-activiti6 \
       core/ouroboros-approval-workflow/ouroboros-approval-workflow-activiti6
# 2) 恢复 core/ouroboros-approval-workflow/pom.xml 的 <module> 与 bom/pom.xml 的 dependencyManagement 条目
# 3) 修复 javax.* → jakarta.* 残留（3 个文件）
# 4) 补齐 TrustedFlowTaskActionService 实现与上面三处语义分叉
```

在此之前，`ouroboros.workflow.approval.engine` 只接受 `flowable`；取其他值会在启动期以 `WORKFLOW_ENGINE_NOT_FOUND` 失败（由 `ApprovalWorkflowEngineSelectionVerifier` 校验），不会静默地"没有引擎"。
