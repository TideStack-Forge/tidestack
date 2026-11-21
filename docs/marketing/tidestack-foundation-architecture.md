# 潮汐栈企业级应用基座

潮汐栈（TideStack）为企业应用提供统一的前后端运行基座，将模型装配、页面呈现、业务执行、数据治理、身份权限和系统集成组织在同一套契约与生命周期中。团队可以用 AI 辅助开发、在 IDE 中专业编码，或通过开发平台可视化建模，交付的模型元数据、业务代码和扩展包由基座共同加载运行。

应用运行所需的数据访问、流程、权限、消息、任务和界面能力由基座承接，团队可以把开发工作集中在业务定义与专有能力上。企业可从标准基座扩展开始，也可将所需运行模块装配到已有 Spring Boot 工程中；开发平台继续承担模型编辑、应用管理与联调。

![潮汐栈企业级应用基座架构](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg)

[打开独立 SVG](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg)。图中区域表示能力分组，上下位置不表示代码依赖；各能力按应用的模块组合、配置与授权启用。

## 一套基座，承接多种开发方式

AI 辅助生成的模型与代码、开发者编写的 Java 或 TypeScript、可视化工具维护的页面与流程，都进入同一套应用资产与运行机制。页面、接口、业务动作与数据访问遵守相应契约，开发方式的选择可以与运行能力的选型分别确定。

前端基座提供微前端宿主、布局、菜单、标签页、统一请求、实时订阅、多语言、主题和 PrismUI 页面渲染。既有的组件、页面动作、公式和校验规则可以复用；专有交互通过微前端、组件库或渲染器插件接入。

## 从业务模型到可执行能力

标准业务模型围绕属性、关系、动作、事件、视图和访问权限组织业务对象。模型装配按定义与启用条件，将业务语义投影到数据、页面、接口及权限等专门职责中。逻辑流、代码和审批流按各自契约承接执行，模型之间保留投影、引用、调用和实现的区别。

数据引擎提供动态与类型化模型、关联查询和统一读写能力。字段校验、唯一约束、引用检查、审计字段、逻辑删除、归档删除、历史版本、结构迁移与声明式初始化，分别覆盖数据的约束、变更与保留过程。

业务动作运行时处理输入输出契约、授权、事务和执行审计。业务事件记录已发生的业务事实，在事务提交后向订阅投递，并提供持久化、失败重试与恢复机制。内建审批流管理人员任务和跨时段流转；企业已有审批平台可通过适配器接入。

## 企业应用所需的公共服务

组织与人员、用户资料、统一配置、字典、多语言、文件与文档、消息通知、计划与延迟任务、外部 HTTP 调用、消息队列、统计报表和看板均有对应能力模块。缓存、分布式锁、脚本、表达式、模板、异常映射、资源覆盖及应用生命周期为这些模块提供运行支撑。

身份与访问控制贯穿前后端。前端按权限组织入口与交互，后端在接口、业务动作和数据读写中检查实际授权，并控制记录范围与可访问字段。外部身份接入、OIDC 身份服务、委托凭据和业务审计分别承担认证、授权与追溯职责。

AI 运行能力包括模型调用、向量与知识检索、检索增强生成，以及外部 MCP 工具客户端接入，具体由已装配的提供方与配置决定。AI 辅助开发使用的 Skills、模型规范与工具链，和应用运行时调用 AI 服务分别维护。

## 连接企业协作生态

| 企业生态 | 当前接入范围 |
| --- | --- |
| 钉钉 | 企业登录、外部审批发起与结果接入、工作通知 |
| 飞书 | 企业登录、外部审批发起与结果接入 |
| 企业微信 | 企业登录、外部审批发起与结果接入 |
| 微信公众号 | 模板消息通知 |

接入使用相应的渠道模块、企业连接、凭据、审批模板或通知模板及回调配置。三种预置外部审批适配器采用结果接入模式，审批任务仍在外部平台处理；飞书与企业微信通知、组织目录同步和应用入口发布另有集成规划，未列入上表的当前能力。

## 通过插件机制扩展，通过环境配置部署

后端插件沿自动配置、Java SPI、模型与数据插件、逻辑流节点、字段类型及渠道适配契约接入；前端通过微前端、渲染器、组件、动作、公式与校验器扩展。企业定制能力可以随扩展包复用，继续使用基座的模型发现、权限与生命周期机制。

仓库提供 Docker 镜像与 Docker Compose 安装材料，应用元数据、扩展包、配置与持久数据分别管理。开发平台具备 Docker 和 Kubernetes 运行后端，用于管理测试应用与开发助手；Kubernetes 后端创建单副本 Deployment、Service 和可选 Ingress，并通过共享工作区与 PVC 接入持久化。

平台及正式应用部署到 Kubernetes 时，由部署方准备集群安装编排。多副本高可用、自动伸缩、完整 Helm 安装包和无停机升级需按实际交付方案单独验证。开发平台的健康检查、指标采集与实例状态日志已有对应接入；业务应用暴露哪些观测端点取决于自身装配。

## 对照手册继续了解

- [架构设计：分层、模型装配、业务执行与插件机制](../handbook/docs/concepts/architecture/index.md)
- [模型体系：六类要素与模型协作关系](../handbook/docs/concepts/modeling/index.md)
- [融合开发：标准基座扩展与内嵌基座](../handbook/docs/fusion-development/index.md)
- [认证接入：企业目录、统一身份与企业协作平台](../handbook/docs/fusion-development/authentication-integration.md)
- [部署架构：Docker、Kubernetes、入口与持久化](../handbook/docs/operations/deployment-architecture.md)

## 附录：图中能力归属

以下对照保留既有能力目录中的 28 项前端能力和 79 项后端能力。能力 ID、定义、接入条件与限制以 [能力目录](../handbook/static/capabilities/catalog.json)为准；目录标记为源码审阅，未作部署实例验收。图面使用代表性标签，表中各项能力按组归属。

### 前端基座：28 项

| 能力 ID | 能力名称 | 图中归属 | 接入条件 |
| --- | --- | --- | --- |
| `fe.micro-app` | 微前端与布局生命周期 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.navigation` | 标签页与导航 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.dialogs` | 弹窗、抽屉与反馈 | [前端应用基座 / 交互与通信](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-communication) | 由前端宿主提供 |
| `fe.request` | 统一请求 | [前端应用基座 / 交互与通信](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-communication) | 由前端宿主提供 |
| `fe.business-actions` | 业务动作调用 | [前端应用基座 / 交互与通信](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-communication) | 由前端宿主提供 |
| `fe.events` | 页面事件与会话回放 | [前端应用基座 / 交互与通信](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-communication) | 由前端宿主提供 |
| `fe.stomp` | 实时订阅与初始快照 | [前端应用基座 / 交互与通信](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-communication) | 由前端宿主提供 |
| `fe.auth` | 登录、身份与权限显示 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 由前端宿主提供 |
| `fe.external-login` | 多认证方式登录 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 由前端宿主提供 |
| `fe.menus` | 菜单树与徽标 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.user-menu` | 用户菜单扩展 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.theme` | 主题切换 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.i18n` | 前端多语言 | [前端应用基座 / 应用宿主](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-host) | 由前端宿主提供 |
| `fe.schema-render` | PrismUI 页面渲染 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.schema-editor` | 页面描述编辑与预览 | [前端应用基座 / 规则与编辑扩展](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rules) | 由前端宿主提供 |
| `fe.micro-component` | 微组件与嵌入页面 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.organization-picker` | 组织与人员选择 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.dict-picker` | 字典选择器 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.tree-editor` | 树编辑与穿梭选择 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.config-editors` | 脚本、JSON 与图标配置 | [前端应用基座 / 规则与编辑扩展](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rules) | 由前端宿主提供 |
| `fe.workflow-timeline` | 审批时间线 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.report-runtime` | 报表视图运行组件 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.dashboard` | 看板、小组件与图表 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.excel-template` | Excel 模板下载 | [前端应用基座 / 渲染与组件](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rendering) | 由前端宿主提供 |
| `fe.schema-actions` | 页面动作 | [前端应用基座 / 规则与编辑扩展](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rules) | 由前端宿主提供 |
| `fe.schema-functions` | 公式、过滤器与规则 | [前端应用基座 / 规则与编辑扩展](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#frontend-rules) | 由前端宿主提供 |
| `fe.component-extension` | 组件、动作与公式扩展 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 由前端宿主提供 |
| `fe.editor-extension` | 设计器插件与动作面板 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 按需接入 |

### 后端基座：79 项

| 能力 ID | 能力名称 | 图中归属 | 接入条件 |
| --- | --- | --- | --- |
| `be.data.dynamic` | 动态数据模型与查询 | [模型与执行 / 数据引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-engine) | 按模块装配 |
| `be.data.typed` | 类型化数据模型 | [模型与执行 / 数据引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-engine) | 按模块装配 |
| `be.data.fields` | 字段类型与校验 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.relations` | 关联与数据展开 | [模型与执行 / 数据引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-engine) | 按模块装配 |
| `be.data.uniqueness` | 唯一约束与重复检查 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.audit` | 创建与修改审计字段 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.logical-delete` | 逻辑删除 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.archive-delete` | 归档删除 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.history` | 数据历史版本 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.reference-check` | 引用完整性检查 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.derived-update` | 关联变化联动更新 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.primary-key` | 主键与业务编码 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.schema-migration` | 模型驱动结构演进 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.data.initialization` | 声明式数据初始化 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.business.model` | 业务模型标准能力 | [模型与执行 / 业务模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按模块装配 |
| `be.business.actions` | 业务动作契约与执行 | [模型与执行 / 业务行为运行时](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-behaviors) | 按模块装配 |
| `be.business.events` | 业务事件与订阅 | [模型与执行 / 业务行为运行时](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-behaviors) | 按模块装配 |
| `be.business.event-recovery` | 业务事件持久化与恢复 | [模型与执行 / 业务行为运行时](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-behaviors) | 按模块装配 |
| `be.execution.logicflow` | 逻辑流引擎 | [模型与执行 / 逻辑流引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#logicflow-engine) | 按模块装配 |
| `be.execution.expression` | 表达式引擎 | [模型与执行 / 逻辑流引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#logicflow-engine) | 按模块装配 |
| `be.execution.script` | 脚本引擎 | [模型与执行 / 逻辑流引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#logicflow-engine) | 按模块装配 |
| `be.execution.template` | 模板引擎 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.execution.approval` | 内建审批流 | [模型与执行 / 审批引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#approval-engine) | 按模块装配 |
| `be.execution.external-approval` | 外部审批流引擎适配 | [模型与执行 / 审批引擎](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#approval-engine)；[企业生态接入](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#enterprise-ecosystem) | 按需接入 |
| `be.page.ui` | 页面入口模型与前端路由 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按模块装配 |
| `be.page.schema` | 页面描述资源 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按模块装配 |
| `be.page.schema-management` | 页面描述实时管理 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按需接入 |
| `be.page.menu` | 菜单与导航 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按模块装配 |
| `be.page.microfrontend` | 微前端资源服务 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按模块装配 |
| `be.page.dynamic-form` | 动态表单 | [模型与执行 / 页面入口、菜单与资源模型](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#business-model) | 按需接入 |
| `be.integration.webapi` | 模型化 HTTP 接口 | [企业通用服务 / 接口与集成](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#integration-services) | 按模块装配 |
| `be.integration.openapi` | OpenAPI 接口文档 | [企业通用服务 / 接口与集成](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#integration-services) | 按模块装配 |
| `be.integration.rpc` | 外部接口调用 | [企业通用服务 / 接口与集成](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#integration-services) | 按模块装配 |
| `be.integration.mq` | 消息队列 | [企业通用服务 / 消息与任务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#messaging-services) | 按模块装配 |
| `be.integration.cron` | 计划任务 | [企业通用服务 / 消息与任务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#messaging-services) | 按模块装配 |
| `be.integration.delayed` | 延迟与一次性任务 | [企业通用服务 / 消息与任务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#messaging-services) | 按模块装配 |
| `be.integration.websocket` | WebSocket / STOMP | [企业通用服务 / 消息与任务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#messaging-services) | 按模块装配 |
| `be.integration.notification` | 消息通知与模板 | [企业通用服务 / 消息与任务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#messaging-services)；[企业生态接入](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#enterprise-ecosystem) | 按模块装配 |
| `be.identity.authentication` | 登录与会话认证 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.identity.external` | 外部身份接入 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance)；[企业生态接入](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#enterprise-ecosystem) | 按需接入 |
| `be.identity.oidc-provider` | OIDC 身份服务 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按需接入 |
| `be.identity.rbac` | 角色与权限项 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.identity.data-control` | 行级与字段访问控制 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.identity.subject` | 自定义权限主体 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.identity.scoped-context` | 后台执行身份与上下文 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.organization.structure` | 组织架构与任职 | [企业通用服务 / 组织与个人化](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#organization-services) | 按模块装配 |
| `be.organization.profile` | 用户与人员资料 | [企业通用服务 / 组织与个人化](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#organization-services) | 按模块装配 |
| `be.organization.preferences` | 用户偏好设置 | [企业通用服务 / 组织与个人化](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#organization-services) | 按模块装配 |
| `be.file.upload` | 文件上传与存储适配 | [企业通用服务 / 文件与文档](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#file-services) | 按模块装配 |
| `be.file.document-library` | 文档库与访问控制 | [企业通用服务 / 文件与文档](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#file-services) | 按需接入 |
| `be.file.excel` | Excel 读写 | [企业通用服务 / 文件与文档](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#file-services) | 按需接入 |
| `be.file.zip` | ZIP 打包与受限解压 | [企业通用服务 / 文件与文档](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#file-services) | 按需接入 |
| `be.analytics.dataset` | 统计数据集与查询视图 | [企业通用服务 / 统计与看板](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#analytics-services) | 按模块装配 |
| `be.analytics.report` | 报表视图与统计看板 | [企业通用服务 / 统计与看板](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#analytics-services) | 按模块装配 |
| `be.analytics.widget` | 看板与小组件 | [企业通用服务 / 统计与看板](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#analytics-services) | 按模块装配 |
| `be.ai.invocation` | AI 对话与向量调用 | [企业通用服务 / AI 服务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#ai-services) | 按需接入 |
| `be.ai.retrieval` | 知识检索与检索增强生成接入 | [企业通用服务 / AI 服务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#ai-services) | 按需接入 |
| `be.ai.mcp` | MCP 工具接入 | [企业通用服务 / AI 服务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#ai-services) | 按需接入 |
| `be.foundation.configuration` | 统一配置 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.dictionary` | 数据字典 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.i18n` | 后端多语言资源 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.cache` | 统一缓存 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.lock` | 自适应分布式锁 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.business-log` | 业务日志与操作追溯 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.foundation.errors` | 异常与响应映射 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.error-definitions` | 错误码定义资源 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.metadata` | 元数据发现与目录 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.resources` | 应用资源与运行覆盖 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.foundation.lifecycle` | 应用装配与生命周期 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.extension.data-plugin` | 数据模型插件 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 按模块装配 |
| `be.extension.business-plugin` | 业务模型扩展 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 按模块装配 |
| `be.extension.app-module` | 应用模块与高代码装配 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 按模块装配 |
| `be.extension.component-assets` | 平台组件库资源 | [插件机制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#plugin-mechanisms) | 按模块装配 |
| `be.foundation.app-runtime` | 应用运行装配 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.data.access-plugin` | 数据访问控制插件 | [身份与访问控制](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#identity-and-governance) | 按模块装配 |
| `be.data.organization-defaults` | 组织与人员自动赋值 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.foundation.model-service` | 通用模型服务契约 | [模型与执行 / 数据治理](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#data-governance) | 按模块装配 |
| `be.foundation.utilities` | JSON、时间与 SPI 工具 | [公共技术与运行](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#runtime-support) | 按模块装配 |
| `be.ai.tools-policy` | AI 工具定义与调用策略 | [企业通用服务 / AI 服务](../handbook/docs/concepts/architecture/.assets/tidestack-foundation-architecture.svg#ai-services) | 按需接入 |

企业生态接入的范围另见[外部审批引擎规格](../requirements/workflow/external-engine.md)、[企业协作集成设计](../design/cross-cutting/enterprise-collaboration-integration.md)和[外部认证设计](../../security/external-auth-design.md)。云原生部署与运行观测的具体条件以[部署架构手册](../handbook/docs/operations/deployment-architecture.md)为准。
