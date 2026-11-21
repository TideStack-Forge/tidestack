# 企业开放 API 能力包

本模块沿原 RPC 元数据、凭据 SPI 和执行器提供企业 API，不维护另一套 HTTP 客户端或接口注册表。已提供企业应用令牌凭据、三家通讯录读取与应用消息包，目录和通知适配沿同一执行器调用；真实企业联调尚未完成。

| 构件后缀 | 提供内容 |
| --- | --- |
| runtime | `EnterpriseAccessToken` 凭据、调用资源装配、固定企业目标、强制凭据脱敏及领域调用适配 |
| dingtalk / wecom / feishu | 各渠道唯一应用令牌认证定义，依赖对应连接基础模块 |
| dingtalk-contact | 部门详情、直属子部门、分页成员和成员详情 |
| wecom-contact | 部门列表、部门成员详情和成员详情 |
| feishu-contact | 部门详情、分页子部门、分页部门成员和成员详情 |
| dingtalk-message / wecom-message / feishu-message | 各平台自建应用的文本消息发送，沿原通知模板和渠道调用 |

引入能力组 JAR 后，接口和认证通过原 `JarRPCModelMetadataProvider`、`JarRPCAuthCredentialMetadataProvider` 发现。接口名称为 `{provider}.contact.*`，共用 `{provider}.application-token`；不在包里保存企业 ID、secret 或 token。自建应用使用共享 `EnterpriseApplicationTokens`，原第三方审批连接不被当成此次自建应用 RPC 支持。供应商侧 scope 和可见范围仍需单独配置及真实企业验证。

正式调用、逻辑流和开发测试通过原 `RpcInvocationExecutor` 装配依赖，业务参数不能覆盖企业连接。要求独立的 `systemSetting.integrationManagement.connections.rpc.invoke` 权限；拥有模型编辑或连接配置权限不自动取得该权限。执行时解析渠道唯一连接，固定其企业/应用身份，并在注入令牌前复核当前版本。目标必须符合供应商 HTTPS origin、路径及令牌位置合同；不接受用户 OAuth 令牌或任意位置复制令牌。

本模块向集成管理上方贡献“企业开放 API”说明页，跳转到原 `/system-setting/rpc-runtime-management` 查看接口和测试。说明页权限为 `systemSetting.integrationManagement.connections.rpc.catalog`，不授予原 RPC 管理或调用权限；加载说明页不会执行接口，也不增加另一套接口编辑及测试机制。

目录、通知等受托管领域使用 `EnterpriseRpcClient` 创建带明确连接的调用 session：领域入口或持久投递计划先执行原领域授权，session 只接受该渠道，调用前复核连接且不跟随当前默认值切换企业。这个 Java 适配不暴露为 HTTP、脚本参数、`internalCall` 或 options 开关。它仍通过同一个 RPC 执行器、模型和运行策略，不另建业务请求定义。

`RpcExecutionResourcesFactory` 只负责为原执行入口装配托管依赖；通用 RPC 层不依赖企业类型。凭据 Builder 通过原 SPI 同时贡献类型说明，原编辑器动态读取已安装类型；原子 Schema 随插件贡献。加载定义和打开编辑器不会获取远程令牌。日志和测试证据接收实际使用的秘密值并强制脱敏，管理调用结果中的秘密回显也被清理。

通讯录合同、权限及验证边界见[企业目录 API 合同](../../docs/references/enterprise-directory-rpc-contract.md)。本地 HTTP mock、真实元数据装载和领域读取测试不能替代真实企业联调。菜单/权限、插件缺失和装配已有定向测试，扩大回归及真实发布环境验收分别记录。

重试沿原 OpenAPI 执行器执行：接口定义必须静态声明 `retrySafe: true`，才允许在 `retry/retryOverride` 次数及剩余预算内重放。通讯录包声明只读合同，三家消息包不允许自动重放；超时、断连或模糊错误由原通知计划按结果确定性处置。普通参数、HTTP 方法和运行策略不能授予重放权限。
