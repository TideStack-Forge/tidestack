# 运行服务

向应用提供配置、资源、文件、消息、调度、审计、外部调用与模型接入等通用能力。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 组织结构 | 公司、部门、岗位、员工和任职关系；为身份和候选人提供组织数据，组织结构本身不替代授权规则。 |
| 配置和基础数据 | 统一配置管理可动态修改的值；数据字典为选项和状态值提供来源，不替代业务主数据。 |
| 资源与文件 | resource 组织资源模型，file-upload 处理临时/永久文件、分片、访问和存储后端，业务文档由上层模块管理。 |
| 消息与调度 | 通知统一模板和渠道，显式可靠投递入口保存逐接收人/渠道责任并区分未知结果；原发送入口不自动获得该协议；MQ 统一生产消费绑定；WebSocket 提供 STOMP 和可选 broker；Scheduler 提供 Cron/一次性调度并委托业务执行。 |
| 业务审计 | 模板中心按来源聚合规则，Filter/Extractor 抽取并脱敏，运行时存储，dev 模块管理模板。 |
| RPC | 模型化外部 HTTP/OpenAPI 调用、认证和运行时治理；开发平台与高代码共用可读路径分组，开发/运行列表与逻辑流模型选择统一采用左侧分组树、右侧模型列表，支持父子分组筛选与名称/标题搜索；不构成完整 API 网关或多协议集成平台。 |
| AI 接入 | descriptor/registry/adapter 组织同步 chat/embed；Spring AI 为 provider 内部实现，RAG/MCP 显式 opt-in。 |
| 平台缓存管理 | 开发平台和普通应用共用缓存总览、空间/条目查询与清理入口；需配置条目签名密钥并授予 `systemSetting.platformCache` 权限。仅管理当前服务实例的缓存。 |
| 应用运行环境 | App runtime 组织生命周期、缓存、分布式锁、运行时依赖锁与脚本上下文，能力聚合模块负责装配。 |

## 系统设置导航

系统设置使用三级导航：系统设置 → 分组 → 页面。分组由公共应用模块元数据定义，各能力模块通过 `parent` 声明归属。

| 分组 | 页面顺序 |
| --- | --- |
| 组织与权限 | 组织架构、用户管理、角色管理、行政级别 |
| 基础配置 | 配置管理、数据字典、国际化、接入应用 |
| 门户与界面 | 门户栏目、门户模板、动态表单、自定义界面 |
| 流程管理 | 审批流、引擎适配器目录、流程映射、外部结果跟踪 |
| 审计管理 | 业务日志、业务日志模板 |
| 运行维护 | RPC 运行管理、资源管理、平台缓存 |

页面随对应模块装配和原有权限显示，未装配或无可见页面的分组不显示。叶子菜单全名、路由和权限标识保持兼容，普通角色无需追加分组权限。平台数据源和平台组件库沿用独立顶层入口。侧栏树和浮层均复用现有组件，无地址分组用于展开导航，有地址菜单用于打开页面。

模块树与角色授权树沿用同一套 AppModule 编排，权限树保留原接口、页面、菜单与数据范围定义。授权服务器设置挂入接入应用，用户资料管理权限关联到用户管理；用户资料规则挂入配置管理，通知模板挂入基础配置。这些权限关联不会增加导航页面。应用已有 `modules.diff.json` 仍按现有机制覆盖默认编排，不会自动重写用户的模块调整。

验证入口：[实际运行时菜单分组与旧授权回归](../../ability/ouroboros-ability-all/ouroboros-ability-all-runtime/src/test/java/com/ouroboros/menu/service/impl/SystemSettingsMenuIntegrationTest.java)、[模块重排导航权限边界](../../core/ouroboros-menu-model/ouroboros-menu-model-runtime/src/test/java/com/ouroboros/menu/service/impl/MenuServiceImplModuleHierarchyTest.java)。公共能力聚合测试覆盖其实际装配的页面；审批流定义、动态表单和自定义界面分别由审批流 web 与各自 app-module 装配，不属于该聚合测试的运行时范围。

## 实现情况

上述能力按各模块已有合同工作；启用与供应商可用性依赖实际配置。AI 不承诺稳定 chat stream、通用 tool executor 或 MCP server export；WebSocket SimpleBroker 不能横向扩展，分布式模式需 Redis/RabbitMQ。RPC 只实现 HTTP/OpenAPI，更多协议仍是扩展方向。

本页依据当前源码和配置描述能力；各测试入口只覆盖其用例范围，不能据此推断目标环境已完成验收或发布。

## 启用条件

按功能装配对应 runtime/provider 和外部服务；密钥由环境或正式凭据来源提供。示例 YAML 不等于自动绑定能力。跨实例与外部 provider 行为需在目标环境验证。

## 相关代码

| 代码与职责入口 | 验证资产与边界 |
| --- | --- |
| [core/ouroboros-resource](../../core/ouroboros-resource) · [设计/指南](../design/modules/core/ouroboros-resource.md) | [测试入口](../../core/ouroboros-resource/ouroboros-resource-core/src/test/java/com/ouroboros/resource/model/ResourceDescriptorTest.java) |
| [ability/ouroboros-cache-management](../../ability/ouroboros-cache-management) · [设计/指南](../design/modules/ability/ouroboros-cache-management.md) | [公共运行时菜单测试](../../ability/ouroboros-ability-all/ouroboros-ability-all-runtime/src/test/java/com/ouroboros/menu/service/impl/PlatformCacheRuntimeIntegrationTest.java) |
| [ability/ouroboros-configuration](../../ability/ouroboros-configuration) · [设计/指南](../design/modules/ability/ouroboros-configuration.md) | [测试入口](../../ability/ouroboros-configuration/ouroboros-configuration-core/src/test/java/com/ouroboros/configuration/ConfigurationServiceTest.java) |
| [ability/ouroboros-data-dict](../../ability/ouroboros-data-dict) · [设计/指南](../design/modules/ability/ouroboros-data-dict.md) | [测试入口](../../ability/ouroboros-data-dict/ouroboros-data-dict-dev/src/test/java/com/ouroboros/data/dict/dev/DataDictDevAutoConfigurationContractTest.java) |
| [ability/ouroboros-file-upload](../../ability/ouroboros-file-upload) · [设计/指南](../design/modules/ability/ouroboros-file-upload.md) | [测试入口](../../ability/ouroboros-file-upload/ouroboros-file-upload-core/src/test/java/com/ouroboros/upload/generator/TemplateUtilsTest.java) |
| [ability/ouroboros-notification](../../ability/ouroboros-notification) · [设计/指南](../design/modules/ability/ouroboros-notification.md) | [持久投递与恢复行为测试](../../ability/ouroboros-notification/ouroboros-notification-runtime/src/test/java/com/ouroboros/notification/service/impl/ReliableNotificationDispatchIntegrationTest.java) |
| [ability/ouroboros-message-queue](../../ability/ouroboros-message-queue) · [设计/指南](../design/modules/ability/ouroboros-message-queue.md) | [测试入口](../../ability/ouroboros-message-queue/ouroboros-message-queue-runtime/src/test/java/com/ouroboros/mq/AbstractProducerCreatorTest.java) |
| [ability/ouroboros-websocket](../../ability/ouroboros-websocket) · [设计/指南](../design/modules/ability/ouroboros-websocket.md) | [测试入口](../../ability/ouroboros-websocket/ouroboros-websocket-core/src/test/java/com/ouroboros/websocket/InitReplyFrameTest.java) |
| [ability/ouroboros-scheduler](../../ability/ouroboros-scheduler) · [设计/指南](../design/modules/ability/ouroboros-scheduler.md) | [测试入口](../../ability/ouroboros-scheduler/ouroboros-scheduler-quartz/src/test/java/com/ouroboros/scheduler/quartz/LogicFlowJobTest.java) |
| [ability/ouroboros-business-log](../../ability/ouroboros-business-log) · [设计/指南](../design/modules/ability/ouroboros-business-log.md) | [测试入口](../../ability/ouroboros-business-log/ouroboros-business-log-dev/src/test/java/com/ouroboros/businesslog/dev/service/BusinessOperationTemplateDevServiceTest.java) |
| [ability/ouroboros-rpc-model](../../ability/ouroboros-rpc-model) · [设计/指南](../design/modules/ability/ouroboros-rpc-model.md) | [测试入口](../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/test/java/com/ouroboros/rpc/dev/RpcDevAutoConfigurationContractTest.java) |
| [ability/ouroboros-ai](../../ability/ouroboros-ai) · [设计/指南](../design/modules/ability/ouroboros-ai.md) | [测试入口](../../ability/ouroboros-ai/ouroboros-ai-anthropic-runtime/src/test/java/com/ouroboros/ai/anthropic/AnthropicAdapterFactoryTest.java) |
| [ability/ouroboros-app-runtime](../../ability/ouroboros-app-runtime) · [设计/指南](../design/modules/ability/ouroboros-app-runtime.md) | [测试入口](../../ability/ouroboros-app-runtime/src/test/java/com/ouroboros/app/AppMetadataConfigurationProviderSpiContractTest.java) |
| [ability/ouroboros-ability-all](../../ability/ouroboros-ability-all) | [测试启动样例（非行为用例）](../../ability/ouroboros-ability-all/ouroboros-ability-all-runtime/src/test/java/com/ouroboros/TestOuroborosApplication.java) |
| [ability/ouroboros-organization-structure](../../ability/ouroboros-organization-structure) · [设计/指南](../design/modules/ability/ouroboros-organization-structure.md) | [组织树行为测试](../../ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/test/java/com/ouroboros/organizationstructure/service/impl/OrganizationQueryFacadeImplTreeNavigationTest.java) |

## 关联文档

- [ability/ouroboros-ai/README.md](../../ability/ouroboros-ai/README.md)
- [ability/ouroboros-rpc-model/DESIGN.md](../../ability/ouroboros-rpc-model/DESIGN.md)
- [ability/ouroboros-websocket/docs/configuration-guide.md](../../ability/ouroboros-websocket/docs/configuration-guide.md)
