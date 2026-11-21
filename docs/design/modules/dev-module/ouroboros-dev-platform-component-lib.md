# ouroboros-dev-platform-component-lib 模块设计摘要

## 模块定位

`ouroboros-dev-platform-component-lib` 是开发平台中的平台组件库管理模块，负责管理低代码组件库包的安装、校验、卸载，以及应用对平台组件库的导入关系。它把“组件库作为平台资产”与“组件库被某个应用引用”这两层关系都纳入开发平台治理范围。

## 责任边界

该模块负责组件库 ZIP 包检查、清单解析、冲突校验、微前端资源部署、应用侧引用配置和开发平台管理入口资源，不负责组件渲染器本身的执行逻辑，也不负责页面设计器、前端运行时框架或通用文件托管。它治理的是组件库资产生命周期，而不是组件库内部实现。

## 依赖关系

从依赖看，这个模块同时站在几个边界点上：它依赖 `ouroboros-util-zip` 读取 ZIP 包，依赖 `ouroboros-ui-model-microweb` 部署微前端资源，依赖 `ouroboros-dev-version-control-system` 维护应用内导入配置，依赖 `ouroboros-dev-app-management` 查询受影响应用。也正因为如此，它天然是开发平台资产治理与前端运行时接缝之间的桥接模块。

## 关键抽象

- `PlatformComponentLibDTO`：平台组件库的主数据对象，承载名称、版本、`publicPath` 和各类组件定义列表。
- `ComponentDefineDTO`：单个组件定义的统一视图，用于做冲突检查和汇总。
- `PlatformComponentLibService`：平台侧组件库目录、检查、安装、卸载与关联应用查询入口。
- `DevAppPlatformComponentLibService`：应用侧导入配置的读取、导入、移除入口。
- `components.manifest.json`：组件库包的根清单文件，是安装和校验流程的事实入口。
- `DevPlatformComponentLibAppInfoModifier`：通过 `WebAppInfoModifier` SPI 把平台组件库注入运行时 `webAppInfo`。

## 关键子模块

- `controller/PlatformComponentLibController`：平台组件库管理 API。
- `controller/DevAppPlatformComponentLibController`：应用级导入配置 API。
- `service/impl/PlatformComponentLibServiceImpl`：组件库检查、冲突校验、安装、卸载与关联应用更新核心逻辑。
- `service/impl/DevAppPlatformComponentLibServiceImpl`：通过 VCS 文件维护应用内 `platform-component-lib.json`。
- `webappinfo/`：把平台组件库注入 `webAppInfo` 的 SPI 适配层。
- `META-INF/ouroboros/`：菜单、权限、UI Model 和 UI Schema 资源，承接开发平台管理页面入口。

## 扩展机制

该模块同时使用了多种共享扩展机制：模块激活走 [../../cross-cutting/spring-autoconfiguration.md](../../cross-cutting/spring-autoconfiguration.md) 中的 imports 与 factories 双注册，`WebAppInfoModifier` 通过 Java SPI 注册，具体治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md)，管理面资源遵循 [../../cross-cutting/meta-inf-resource-conventions.md](../../cross-cutting/meta-inf-resource-conventions.md)。如果以后要扩展新的组件库清单字段或部署策略，也应优先保持这些共享接缝稳定。

## 深入文档索引

- `推荐阅读`：[dev-module/ouroboros-dev-platform-component-lib/README.md](../../../../dev-module/ouroboros-dev-platform-component-lib/README.md)
- `补充深度资料`：[SPI 治理](../../cross-cutting/spi-governance.md)
- `补充深度资料`：[集成边界](../../cross-cutting/integration-boundaries.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `ouroboros-dev-platform-component-lib` 自身的 `DESIGN.md`。

## 演进状态

`ouroboros-dev-platform-component-lib` 仍处于持续演进模块，但主干设计已经明确：平台统一治理组件库资产，应用通过 VCS 管理自己的导入声明，运行时再通过 `webAppInfo` 暴露组件库。后续维护最需要守住的是这三个边界，不要把前端渲染细节、应用业务逻辑和资产治理逻辑重新混回一个模块。
