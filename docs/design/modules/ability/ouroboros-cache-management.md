# ouroboros-cache-management 模块设计摘要

## 模块定位

`ouroboros-cache-management` 是公共能力层中的平台缓存管理入口，面向开发平台和普通应用运行时：它把 `libs/common` 的 `common-cache` 抽象暴露为维护者可见的 REST 接口与菜单/UI 元数据，提供缓存总览、region 与 entry 查询、按 entry 删除、按 region 清理和整体清空能力。

## 责任边界

该模块负责缓存管理的 HTTP 契约、响应包装和平台元数据声明；不负责缓存的存储实现，也不负责缓存条目 token 的编解码与 inspector 能力——这两者都由 `com.ouroboros.cache` 的契约定义并需要在运行期由外部提供。它是薄适配层，不是缓存框架本体。

## 依赖关系

pom 的 `<dependency>` 显示：框架侧依赖 `spring-web`、`spring-webmvc`、`spring-boot`（测试用 `spring-boot-starter-test`）；同仓依赖 `libs/common` 的 `common-web`（`WebApiResult`、`ItemsWebApiResult`）与 `common-cache`（`CacheAdministration`、`CacheEntryPage`、`CacheRegionDescriptor`、`ClearAllResult`），以及 security 域的 `ouroboros-web-security-core`。

`ouroboros-ability-all-runtime` 聚合本模块，因此 `ouroboros-develop` 和 `ouroboros-mothership` 都能加载同一套缓存管理接口及元数据；开发模块聚合不再单独提供该能力。`ouroboros-app-runtime` 的 `PlatformCacheAdministrationConfiguration` 在公共 `PlatformCacheService` 位于类路径时提供 `CacheAdministration`、inspector 和条目 token 编解码器。

缓存操作仍走原有 `common-cache` 扩展点与权限链。菜单标识 `systemSetting.platformCache`、页面 `/system-setting/platform-cache` 和接口 `/api/platform-cache` 保持不变。普通应用需向角色授予该权限后才显示菜单；公共功能不表示匿名或任意登录用户均可访问。

运行环境必须设置 `ouroboros.platform.cache.token.secret`（环境变量 `OUROBOROS_PLATFORM_CACHE_TOKEN_SECRET`），缺失时装配失败；不提供运行基座通用默认密钥。管理范围是当前服务实例的缓存管理器，不跨应用实例聚合缓存。

## 关键抽象

- `PlatformCacheService`：模块服务契约，覆盖 summary、listRegions、listEntries、deleteEntry、clearRegion、clearAll。
- `PlatformCacheServiceImpl`：唯一实现，构造注入 `CacheAdministration`，全部方法以 `WebApiResult` / `ItemsWebApiResult` 包装并按 `ex.getMessage()` 返回错误。
- `PlatformCacheController`：`@RestController`，映射 `/api/platform-cache`（`GET /summary`、`GET /regions`、`GET /entries`、`DELETE /entries`、`DELETE /regions`、`DELETE`）。
- `CacheManagementAutoConfiguration`：模块装配入口。
- `com.ouroboros.cache.CacheAdministration`、`CacheRegionDescriptor`、`CacheEntryPage`、`CacheEntryQuery`、`ClearAllResult`：由 `common-cache` 提供、本模块消费的核心契约。

## 关键子模块

本模块没有 Maven 子模块，自身只有 4 个 main java 文件，按包划分为三层：

- `com.ouroboros.cache.management`：`CacheManagementAutoConfiguration`（`@Configuration` + `@ComponentScan`）。
- `com.ouroboros.cache.management.controller`：`PlatformCacheController`，模块唯一的对外 HTTP 面。
- `com.ouroboros.cache.management.service` 与 `...service.impl`：`PlatformCacheService` 契约与 `PlatformCacheServiceImpl`。
- 元数据侧另有 5 份声明式资源：`src/main/metadata/app-modules.json`、`platformCache.authority.json`、`systemSetting/platformCache.menu.json`、`system-setting/platform-cache.ui-model.json`、`system-setting/platform-cache.ui-schema.json`。

## 扩展机制

模块的装配入口在 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中双注册，`@ComponentScan` 负责发现 controller 与 service 实现。真正的扩展点在 `common-cache` 侧：替换 `CacheAdministration`、`CacheEntryInspector`、`CacheEntryTokenCodec` 的实现即可改变管理面行为，本模块不需要改动。`src/main/metadata` 由根 pom 的 `copy-metadata-sources` 统一复制到 `META-INF/ouroboros/metadata`，声明式资源的放置规则见 [../../cross-cutting/meta-inf-resource-conventions.md](../../cross-cutting/meta-inf-resource-conventions.md)。

## 深入文档索引

- 元数据与自动配置约定见 [元数据资源规范](../../cross-cutting/meta-inf-resource-conventions.md) 与 [Spring 自动配置](../../cross-cutting/spring-autoconfiguration.md)。
- 缓存存储与 inspector 契约见 [common-cache](../libs/common-cache.md)。

## 验证入口

- 模块内 controller、service 和装配测试随代码迁入 `ability/ouroboros-cache-management/src/test/java/com/ouroboros/cache/management/`。
- `ouroboros-ability-all-runtime` 的 `PlatformCacheRuntimeIntegrationTest` 验证公共运行时经 JAR 元数据和正式菜单编排生成授权菜单、隐藏未授权菜单。
- 同模块 `PlatformCacheRuntimeConfigurationTest` 验证公共运行时装配、HTTP 查询与缺失签名密钥时的失败行为。
