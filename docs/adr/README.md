# Architecture Decision Records (ADR)

本目录存放架构决策记录，用于记录重大架构决策及其背景、理由和影响。

## ADR 流程

### 何时创建 ADR

当面临以下情况时，应创建 ADR：
- 选择技术栈或框架（如选择 Flowable vs Activiti）
- 定义系统架构模式（如分层架构、微服务架构）
- 制定跨模块的技术规范（如 SPI 机制、配置系统）
- 做出影响多个模块的设计决策（如统一异常处理）
- 引入新的横切关注点（如可观测性、安全机制）

## ADR 状态说明

- **Proposed**: 提议中，尚未最终决定
- **Accepted**: 已接受，正在实施或已实施
- **Deprecated**: 已废弃，不再推荐使用
- **Superseded**: 已被新的 ADR 取代

## 现有 ADR 列表

| 编号 | 标题 | 决定状态 | 实施情况 | 相关模块 |
|------|------|------|------|----------|
| ADR-001 | [可插拔 WebSocket Broker 架构](ADR-001-pluggable-websocket-broker.md) | Accepted | 见模块设计和配置；运行验证另取证 | ouroboros-websocket |
| ADR-002 | [Approval Workflow 分支与汇聚图模型统一](ADR-002-explicit-gateway-join-graph-model.md) | Accepted | 见定义/图模型设计 | ouroboros-approval-workflow, ouroboros-approval-workflow-designer |
| ADR-003 | [P0-3 Family-Splitting Strategy](ADR-003-p0-3-family-splitting-strategy.md) | Accepted | 部分实施 | testing/architecture-tests, ouroboros-ui-model, ouroboros-app-module-model |
| ADR-004 | [AppModule Authority Bridge Band](ADR-004-appmodule-authority-bridge-band.md) | Accepted | Bridge 建模未落地，DOC-008 | ouroboros-app-module-model, ouroboros-web-security-core |
| ADR-005 | [Business Model Behavior Ontology](ADR-005-business-model-behavior-ontology.md) | Accepted | 见业务行为设计与验收记录 | ouroboros-business-model, ouroboros-data, ouroboros-logicflow, ouroboros-approval-workflow |
| ADR-006 | [企业集成能力组合与组织关联](ADR-006-enterprise-integration-capability-composition.md) | Accepted，首页列表选择由 ADR-007 替代 | 能力范围已确认，待实现 | 企业连接、组织、安全、通知、RPC、应用运行时 |
| ADR-007 | [集成管理分区与入口迁移](ADR-007-integration-management-workspace.md) | Accepted，企业渠道基数由 ADR-008 收窄 | 命名、上下分区与元数据同步调整已确认，待实现 | AppModule、权限、菜单、配置入口、集成管理页面 |
| ADR-008 | [企业渠道首期各接一个企业](ADR-008-single-enterprise-per-channel.md) | Accepted | 渠道基数已确认，正式限制待实现 | 企业连接、通知、RPC、集成管理页面 |

## 写作辅助

[ADR 模板](template.md)只在新增决定时使用；普通局部修改不强求 ADR。全仓编号唯一，Accepted 表示决定已接受，实施进度单独说明，不以状态替代验证。

## 相关设计

- [架构设计](../design/architecture/README.md)
- [设计总览](../../DESIGN.md)
