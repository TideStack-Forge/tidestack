# 接口契约

平台 WebAPI 的 OpenAPI 3.1 输出由已注册的 WebAPIModel 形成；安全控制器与 SAS 标准协议端点的契约源分别如下，不能假定一份 WebAPI 导出完整覆盖全部安全端点。导出范围依赖实际应用装配、权限和已注册模型；仓库没有一份已固定到发布版本并经本次验证的全平台机器契约。

## 契约入口

| 接口范围 | 唯一入口与版本 | 关联机制 |
| --- | --- | --- |
| 逻辑流在线调试 | [调试控制器](../../core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/controller/LogicFlowDebugController.java)及正式请求/事件类型；沿统一 WebAPI 导出 | [在线调试设计](../../core/ouroboros-logicflow/design/online-debugging/overview.md#http-与权限)、[运行手册](../runbooks/logicflow-online-debugging.md)；缓存历史非持久化 |
| 当前应用注册的高代码与低代码 WebAPI | `GET /api/sys/webapi/openapi.json`，输出 `openapi: 3.1.0`；可按 scope、handlerType、tag、pathPrefix、api 过滤 | [导出与建模指南](../../core/ouroboros-webapi-model/docs/openapi-guide.md) |
| 业务 Action 调用、可用性与契约目录 | [动作控制器](../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/action/BusinessActionDescriptionController.java)、[目录控制器](../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/behavior/BusinessBehaviorCatalogController.java)及正式 Input/Invocation 类型 | [调用与输入指南](../guides/business-model-behaviors.md)、[输入设计](../../core/ouroboros-business-model/docs/business-action-input-design.md)；不手写另一份动态 Action Schema |
| 业务事实诊断与交付重驱 | [运维控制器](../../core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/event/store/BusinessEventOperationsController.java)与当前消费方权限 | [业务行为运维](../runbooks/business-model-behaviors.md)；事实、交付与一次尝试分开 |
| 平台账号、资料、联系方式和外部认证 | [RBAC 控制器](../../security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/controller/)、[资料/联系方式控制器](../../security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/profile/)、[认证控制器](../../security/ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/controller/)及其正式 DTO/注解 | [安全设计](../../security/DESIGN.md)、[账号资料](../../security/user-profile-design.md)；错误族以实际异常映射为准 |
| OAuth2 客户端管理与确认 | [管理控制器](../../ability/ouroboros-oidc-server/src/main/java/com/ouroboros/ability/auth/oidc/management/AuthClientManagementController.java)、[确认控制器](../../ability/ouroboros-oidc-server/src/main/java/com/ouroboros/ability/auth/oidc/OidcConsentController.java)与原配置策略 | [管理写契约](../../ability/ouroboros-oidc-server/configuration-management-design.md)、[确认协作](../../ability/ouroboros-oidc-server/consent-flow-design.md) |
| OAuth2/OIDC 标准端点 | [SAS 装配](../../ability/ouroboros-oidc-server/src/main/java/com/ouroboros/ability/auth/oidc/OidcServerAutoConfiguration.java)和同一发行者的 discovery/JWKS；代码优先配置 | [协议端点及信任边界](../../ability/ouroboros-oidc-server/DESIGN.md#5-协议端点与信任契约)；SAS 过滤器端点不等于 MVC WebAPI 模型 |
| 业务行为（非 HTTP） | [BusinessActionRuntime](../../core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/action/BusinessActionRuntime.java)、[BusinessEventPort](../../core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/event/BusinessEventPort.java)及所属正式类型 | [业务行为设计](../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)；Java/TypeScript 生成类型是派生产物 |
| 身份、决策与字段/行参数（非 HTTP） | [安全核心正式类型](../../security/ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/) | [统一权限语义](../../security/delegated-authorization-design.md)，不从设计伪代码生成另一套接口 |
| 开发期 RPC 工作区测试 | [RpcModelTestController](../../ability/ouroboros-rpc-model/ouroboros-rpc-model-dev/src/main/java/com/ouroboros/rpc/dev/controller/RpcModelTestController.java) 与 `dev/service/testing` 正式 DTO | [测试接口与错误合同](../../ability/ouroboros-rpc-model/docs/model-testing.md#6-接口合同)；初始化和执行均要求应用编辑权限，不接受临时 URL 或模型正文 |
| RPC 分组与模型选择 | [开发元数据控制器](../../dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/controller/DevAppMetadataController.java)；`GET /api/dev/{appName}/metadata/rpc-model` 的 `mode=raw` / `groups` | [分组声明与浏览](../../ability/ouroboros-rpc-model/DESIGN.md#31-分组声明与浏览)；由原元数据提供者派生，当前选择弹窗消费平面 `raw` 数据，提交值为 `name` |
| 后端代码提示上下文 | `POST /api/dev/{appName}/code-intelligence/context`；[正式 DTO](../../libs/common/common-dev/src/main/java/com/ouroboros/dev/codeintelligence/CodeContextRequest.java)与[控制器](../../dev-module/ouroboros-dev-code-intelligence/src/main/java/com/ouroboros/dev/codeintelligence/CodeContextController.java) | [机制与接入](../design/cross-cutting/script-expression-intelligence.md)，原始草稿只用于静态分析 |
| 元数据 JSON 结构（不属于 HTTP OpenAPI） | [metadata-schema](../../metadata-schema/README.md)，JSON Schema Draft 2020-12 | [模型系统](../design/cross-cutting/model-system.md) |
| SDK、DSL 和公开扩展 API 使用说明 | [产品 API 手册](../handbook/docs/api/index.md) | 由对应公开代码和合同定义，不替代 HTTP 导出 |

## 权威输入与生成方向

高代码路由由 Spring MVC 的 RequestMapping 注册过程经 RequestMappingIntegrationCenter 抽取到 WebAPIModel；低代码 API 直接注册模型。OpenAPIDocumentService 消费统一模型，OpenAPIDocumentController 输出 JSON。维护源注解/模型后通过原导出链更新派生合同，不增加二次扫描器，也不手工维护另一份独立 OpenAPI。

导出请求的 `version` 参数只是文档信息覆盖，不能证明对应发布版本。选择应用与过滤参数时需记录基线；本轮未启动服务或请求接口。

## 漂移与兼容性验证

新增/修改 API 应补全操作描述、参数/请求体 Schema、成功和错误响应、鉴权要求与公开稳定标识，沿用[模块指南](../../core/ouroboros-webapi-model/docs/openapi-guide.md)的源定义方式。评审应比较同一装配和过滤条件下的前后合同，并用所属模块的行为测试验证状态码、数据和权限边界。

[契约生命周期](contract-lifecycle.md)说明提取、校验和交付要求。目前已有 exporter/controller 测试，但没有全平台版本化导出、破坏性变更比较及消费者兼容门控；安全方案组件和全局异常汇总也有覆盖限制。工程缺口统一见 [API-001](../tech-debt-tracker.md#api-001-版本化接口契约与漂移校验)，不能把新增索引当成契约一致性已通过。
