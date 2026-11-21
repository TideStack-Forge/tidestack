# ouroboros-rpc-model 模块设计摘要

## 模块定位

`ouroboros-rpc-model` 是能力层中的外部服务调用模型模块，负责把远程调用定义、认证凭据、元数据来源和逻辑流节点组织成统一 RPC 能力——让平台把外部接口当作模型化资源消费，而不是在各业务模块零散拼接 HTTP 调用。

## 责任边界

负责 RPC 静态定义、认证抽象、元数据装配、统一调用执行、运行时治理（启停/日志/指标/可用性）与逻辑流调用接入；不负责实现完整 API 网关、异步集成平台或全套外部接口治理产品。当前唯一落地的协议是 OpenAPI/HTTP，唯一治理入口是 `/api/sys/rpc-runtime`，两者都不构成未来多协议/多租户的既成契约。

## 依赖关系

父 pom 聚合 `core`、`runtime`、`dev`、`logicflow-node` 四个子模块（`runtime` 另依赖 `common-*`、`ouroboros-data-typed-runtime`、`ouroboros-configuration-core` 等）。反向：`ouroboros-ability-all-core` 引入 `core`，`ouroboros-ability-all-runtime` 引入 `runtime` 与 `logicflow-node`，`ouroboros-dev-module-all` 引入 `dev`，`dev-helper` 直接使用 `core` 的元数据类型与 metadata provider SPI；构件版本由 `bom/pom.xml` 统一管理。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-rpc-model/DESIGN.md](../../../../ability/ouroboros-rpc-model/DESIGN.md)（模块权威设计：对外契约、HMAC 签名链路、错误语义、运行时治理、扩展点与 12 项已知限制）
- `已实现设计`：[RPC 模型测试](../../../../ability/ouroboros-rpc-model/docs/model-testing.md)（工作区快照、调用级配置与认证、保存/测试按钮、调试界面及验收条件）
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

当前实现限制包括：本地与远端错误未区分且错误码只有 3000；可用性是"一次失败即不可用"，无熔断阈值/恢复窗口/限流降级；`AuditInterceptor`/`ExceptionMappingInterceptor`、`RPCModelDefinition` 的 `healthCheck`/`defaultTimeoutMs`/`defaultRetry`、`HmacSignaturePreset` SPI 化均未落地；凭据构建失败会静默降级（中心记 error 后丢弃凭据，调用期才报"认证凭据不存在"）。
