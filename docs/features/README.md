# 功能地图

按使用者要完成的任务组织当前能力。这里说明源码支持的范围；业务预期归需求，机制归设计，实际启用/验收/发布归目标环境证据。TideStack 是产品名，Ouroboros 运行时标识保持兼容。

| 功能域 | 当前能力与边界 | 正文 |
| --- | --- | --- |
| 模型与应用 | 数据/字段/业务模型、关系画布与切面、业务 Action/Event、应用模块和动态表单 | [模型与应用](modeling.md) |
| 逻辑编排 | 节点定义、执行、编辑与调试 | [逻辑编排](logicflow.md) |
| 审批 | Flowable 内置主线、审批设计器、外部引擎接入；待办中心为提案 | [审批](approval.md) |
| 统计分析 | SQL 数据集、图表、仪表板与复合视图 | [统计分析](analytics.md) |
| 页面与接口 | UI/菜单、微前端、SDK、WebAPI、Widget 与偏好 | [页面与接口](presentation.md) |
| 安全 | 账号资料、平台/外部登录、OAuth2/OIDC 与有界业务委托 | [账号、认证与访问控制](security.md) |
| 运行支撑 | 配置、资源、文件、消息、调度、审计、RPC 与 AI 接入 | [运行服务](runtime-services.md) |
| 开发平台 | 应用/VCS、能力看板与功能矩阵、模型画布实时协同、运行环境、组件库治理和源码生成 | [开发平台](development.md) |
| 开发者工具 | IDE、Schema、模板、打包插件、通用库和交付资产 | [开发者工具](developer-tools.md) |

各页关联实际后端、前端及跨模块入口；无独立行为测试的对象明确说明，不能从文件数量推断覆盖率。安全功能和机制分别由功能专题与安全设计维护；组织结构能力见[运行服务](runtime-services.md)。Activiti6 等 legacy-compat 内容与 Spreadsheet 微服务有独立历史边界，不算当前默认主线能力。

[需求](../requirements/README.md)、[技术设计](../design/README.md)、[接口契约](../api/README.md)、[风险](../risk-tracker.md)及[技术债](../tech-debt-tracker.md)分别记录预期、机制和差距。
