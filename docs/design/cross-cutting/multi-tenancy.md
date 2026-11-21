# 多租户能力边界

平台尚未实现统一的租户上下文、租户路由或自动数据隔离。现有数据模型插件、元数据装饰器和缓存适配器是通用扩展点，不能据此认定系统已经具备多租户隔离能力。

## 现有机制与使用限制

- 数据访问沿 `ouroboros-data` 的正式模型与 QueryDSL API；`DataModelPlugin`、`DataModelMetaDecorator` 提供模型扩展位置。
- 缓存沿现有缓存管理和适配器入口；配置沿配置服务及其生命周期读取。
- `appName`、应用运行环境和业务组织标识各有既定用途，不能自动等同于安全租户边界。
- 尚无 `common-tenant`、`ouroboros-tenant-*`、`TenantIsolationDataModelPlugin`、`TenantCacheResolver` 或 `ConfigOwnership` 的生产实现与注册；不对共享表、分库或跨租户缓存提供隔离承诺。

## 设计约束

未来确有多租户需求时，应在上述正式入口统一处理数据、配置、缓存及异步上下文，明确鉴权与隔离边界。不得由各业务模块自行增加隐式租户过滤器、旁路仓库或第二套缓存机制。具体方案须经采纳后更新本主题；此前的提案不作为现行接口合同。

相关机制见[模型体系](model-system.md)、[扩展机制](extension-mechanisms.md)和[配置体系](configuration-system.md)。
