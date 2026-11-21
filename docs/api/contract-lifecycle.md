# HTTP 契约生命周期

本流程复用 WebAPIModel 正式注册和导出链。操作级字段的准确行为与示例见[OpenAPI 指南](../../core/ouroboros-webapi-model/docs/openapi-guide.md)，本页负责跨模块维护与交付边界。

## 修改源定义

高代码使用 Spring MVC 路由及 Swagger/Jakarta Validation 注解表达合同；低代码在 WebAPIModel 源模型表达。显式 Swagger 信息优先于自动推断；校验注解补充 Schema 约束。新增路由应给出稳定 operationId、summary、参数位置/必填/类型、请求体及成功/失败响应，敏感示例使用非真实数据。

修改字段类型、必填性、错误响应或鉴权要求时，同时评估 SDK/前端/扩展包和其他消费者。移除或收紧已有行为前，记录受影响消费者、迁移方式和版本边界；不能仅凭单元测试或自动推断认定兼容。

## 按实际装配导出

在已授权且可访问的目标应用中请求 `GET /api/sys/webapi/openapi.json`，记录应用/源 HEAD、包版本、环境、过滤参数及认证身份的权限范围。完整参数由模块指南维护。产物保存在对应任务或 CI 制品，使用固定版本或哈希标识，避免把临时导出覆盖为新的权威输入。

## 比较与验证

1. 使用相同装配和过滤条件比较前后路径、方法、operationId、参数、Schema、响应和鉴权；区分装配差异与合同变更。
2. 执行相关 exporter、Controller 和实际消费者的行为测试，结果关联当前 HEAD；合同结构存在不证明鉴权生效或业务正确。
3. 发布前确认破坏性变化的迁移安排、合同地址及可访问期限，记录未覆盖范围。历史文件的 schema 合法不代表与新版本实现一致。

当前缺少统一的版本导出/比较命令和 CI 强制门控，自动化建设见 [API-001](../tech-debt-tracker.md#api-001-版本化接口契约与漂移校验)。在该任务完成前，应在每次 API 交付中明确人工比较范围与遗漏，不补造绿色报告。

## 已知导出边界

当前没有自动汇总 ControllerAdvice 全局异常，也未覆盖 WebFlux/RouterFunction；components 中的 parameters、responses、headers、links、securitySchemes 尚不完整。operation 的 security 字段不等于完整可执行的安全方案合同。WebSocket、消息队列和外部厂商回调的非 HTTP 语义仍以各自设计为准。
