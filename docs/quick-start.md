# 工程开发快速入口

本页面向开发者与 Agent，提供当前规则和验证入口。项目能力与开发环境分别见 [README](../README.md) 和[开发指南](development-guide.md)。

## 开始一个变更

1. 阅读 [AGENTS](../AGENTS.md)，确认工作区、现有改动、任务范围和适用局部规则。
2. 从[功能地图](features/README.md)、[需求](requirements/README.md)、[架构](../ARCHITECTURE.md)与[设计入口](design/README.md)找到当前正文和正式实现路径。
3. 按[编码规则](rules/coding.md)修改，按[测试规则](rules/testing.md)选择能够验证行为的定向测试。
4. 检查 diff，同步受影响文档；按[版本规则](rules/version-control.md)与[CI/CD 规则](rules/ci-cd.md)准备 MR。

## 选择检查入口

- 模块测试与场景测试：先读[测试指南](rules/testing.md)，或运行 `bash scripts/agent-feedback/run-tests.sh --help` 查看支持的参数。
- 架构与静态规则：见[架构约束指南](architecture-constraint-guide.md)，区分当前可执行检查与尚未启用的门禁。
- 文档检查：见[文档治理规则](rules/documentation.md)，链接检查为 `bash scripts/docs/check-links.sh`，扫描范围以脚本为准。
- 日志、健康与恢复：见[Agent 反馈循环](runbooks/agent-feedback-loop.md)与[会话恢复](runbooks/agent-session-recovery.md)。

执行计划、评审与单次验证归关联任务；长期风险和债务分别维护在[风险跟踪](risk-tracker.md)与[技术债跟踪](tech-debt-tracker.md)。
