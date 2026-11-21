---
name: operate-ouroboros
description: 对 Ouroboros 目标环境进行健康、日志、metrics、部署排障和恢复，复用现有脚本与运维手册；平台操作或运行故障时使用。
---

# 平台运维与诊断

## 前提

明确 checkout、目标 URL/环境、权限和资源归属。先检查 Git 状态，不从示例推定真实生产地址。日志/健康查询可按任务范围执行；部署、重启、发布和破坏性动作须有相应任务授权。

## 动作

- [ ] 按[运维索引](../../../docs/runbooks/README.md)选择目标场景；部署/升级复用[部署指南](../../../docs/deployment-guide.md)，不另建执行路径。
- [ ] 从根目录查看 `scripts/agent-feedback/query-logs.sh --help`、`check-health.sh --help`、`query-metrics.sh --help`，传入实际 URL/日志路径；正式步骤与结果位置由[反馈循环](../../../docs/runbooks/agent-feedback-loop.md)维护。
- [ ] 审批与外部引擎按对应发布/迁移手册核对配置、事实状态和回退条件；不把其他环境的健康结果套用到目标。
- [ ] 保存源/部署版本、环境、命令、响应、日志及实际影响；完成后释放本任务创建的进程/端口/临时资源，保留所需证据。

## 成功确认与失败处理

启动成功按目标手册的端口、health 状态和延迟判定，还需验证用户关键路径；health=UP 不证明业务验收。指标不存在时核对 Actuator 暴露和权限，不默认为零。

失败保留首个异常与现场，按[故障排查](../../../docs/runbooks/troubleshooting-guide.md)继续。只回退本任务负责的版本/资源；不要重置共享库、覆盖已发布产物或清理他人进程。会话中断按[恢复说明](../../../docs/runbooks/agent-session-recovery.md)核对基线再继续。
