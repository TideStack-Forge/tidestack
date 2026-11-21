# 技术设计

设计文档解释当前机制、职责边界及已采纳的演进方向。功能现状见[功能地图](../features/README.md)，业务预期见[需求](../requirements/README.md)；已采纳但尚未实现的设计必须就地标明差距。未采纳提案保留在关联任务。

| 要理解的机制 | 正文入口 |
| --- | --- |
| 系统边界、模块依赖与部署拓扑 | [架构](../../ARCHITECTURE.md)、[系统设计](architecture/README.md) |
| 各层职责与桥接带 | [分层设计](layers/README.md) |
| 各模块的职责和深入设计 | [模块设计](modules/README.md) |
| 模型、扩展、自动配置、元数据发现及集成 | [横切机制](cross-cutting/README.md) |
| 安全机制 | [安全设计](../../security/DESIGN.md) |
| 审批动作与任务契约 | [审批流程设计](../../core/ouroboros-approval-workflow/DESIGN.md) |
| Java 编译与运行边界 | [Java 基线](java-runtime-baseline.md) |
| 已接受的重要取舍 | [ADR](../adr/README.md) |

模块内 DESIGN/docs 承载深入机制，中央摘要负责边界和导航，避免复制两份完整正文。当前已知实现差距在[技术债](../tech-debt-tracker.md)原条目维护。

## 写作辅助

- [模块设计模板](module-template.md)：创建主题时裁剪使用，不是现状说明。
- [文档治理](../rules/documentation.md)、[设计评审](../rules/design-review.md)：规定更新和替代方式。
