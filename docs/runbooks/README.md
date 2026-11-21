# 运维手册

这里维护可重复使用的部署、升级、故障处置与恢复说明。单次执行记录归关联任务，持续风险/欠缺分别在[风险](../risk-tracker.md)和[技术债](../tech-debt-tracker.md)更新。

| 场景 | 正文 |
| --- | --- |
| 逻辑流运行时覆盖、独立刷新与恢复 | [运行时逻辑流管理](logicflow-runtime-manage.md) |
| 业务 Action / Event 事务、升级、Outbox 与重驱 | [业务行为运维](business-model-behaviors.md) |
| 通用诊断、测试安全与失败处理 | [故障排查](troubleshooting-guide.md) |
| 日志、测试、健康和 metrics | [反馈循环](agent-feedback-loop.md) |
| 会话中断后恢复 | [会话恢复](agent-session-recovery.md) |
| 审批部署/运行与浏览器回归 | [审批运维](approval-workflow-runbook.md) |
| 企业集成仿真验收与实际发布核对 | [企业集成验收](enterprise-integration-acceptance.md) |
| 外部引擎发布门禁与回退 | [集成发布清单](approval-workflow-external-integration-release-checklist.md) |
| 外部引擎 unmapped-policy 迁移 | [迁移手册](approval-workflow-external-engine-migration-runbook.md) |
| 条件路由回归 | [路由回归清单](approval-workflow-condition-routing-regression-checklist.md) |

复杂操作从[运维任务 Skill](../../.agents/skills/operate-ouroboros/SKILL.md)选择对应正文；步骤和正式脚本只维护一份。

- [逻辑流在线调试](logicflow-online-debugging.md)：容器、共享缓存、多活消息与故障恢复；已完成本地发布候选验证，尚未生产部署。
