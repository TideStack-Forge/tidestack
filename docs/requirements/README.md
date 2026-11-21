# 需求与验收

这里保存已有确认依据的稳定业务预期。功能是否已实现、启用或发布分别查[功能地图](../features/README.md)和实际交付证据，不能由 Accepted 状态推定验收通过。

| 需求域 | 正文 | 当前边界 |
| --- | --- | --- |
| 安全 | [多权限接口](security/multi-authority-api.md) | 已确认的接口归属语义；委托数据参数另受目标兼容约束 |
| 业务行为 | [业务行为契约](../../core/ouroboros-business-model/docs/business-model-behavior-contracts.md) | 输入/版本、默认事实、工作流与高级功能的稳定约束及验收 ID；沿模块唯一正文维护，不复制执行日志 |
| 前端扩展 | [菜单扩展](frontend/menu-extension.md) | 菜单徽标及用户菜单注册；保留原验收清单 |
| 审批 | [总体行为](workflow/overview.md)、[外部引擎](workflow/external-engine.md)、[可发起流程](workflow/available-workflows.md) | 流程生命周期、渠道能力、适配器能力和真实企业验证分别维护 |
| 逻辑编排 | [LogicFlow](logicflow.md) | 系统自动执行逻辑、正式交付、诊断和依赖边界 |
| 业务模型诊断 | [校验问题展示](modeling/business-model-design-validation-diagnostics.md) | 列表与编辑页消费已有设计时诊断 |
| 明细查询 | [综合查询](analytics/comprehensive-query.md) | 筛选、结果、允许的导出及后续处理入口 |
| 统计分析 | [总体行为](analytics/overview.md)、[复合业务视图](analytics/composite-view.md) | 数据集、图表、仪表板及组合交付，以正文状态为准 |
| 开发运行时 | [运行时管理](development/runtime-management.md) | 应用开发与联调阶段的运行环境管理 |
| 数据库兼容 | [显式数据库兼容](data/database-compatibility.md) | 显式选择、驱动分发和真实环境验证边界 |

旧的按日期命名设计施工稿，其有效机制已归入对应设计；已被吸收或取代的稿件通过原 Git 提交和任务附件追溯，不把旧计划重新列为当前需求。尚无独立已确认需求正文的功能，只在功能/设计中描述可查事实，不据源码补造业务承诺。待办中心尚无已采纳的独立规格或生产模块，不能计为已实现能力。

新需求应写明目标、场景、范围、非目标、行为约束和可观察的验收条件，并关联当前功能和设计。验收结果保存到实施任务，未执行的检查保持未执行。
