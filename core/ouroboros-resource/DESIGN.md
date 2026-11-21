# Ouroboros Resource 模块概览

## 当前实现

资源模块已经收敛为一套应用级文件资源方案：

- 资源范围：`FRONTEND` / `BACKEND`
- 覆盖层级：`APP_DEFAULT` / `RUNTIME_OVERRIDE`
- 生效顺序：`RUNTIME_OVERRIDE > APP_DEFAULT`
- 默认资源来源：工作区文件 + classpath 资源
- 运行时覆盖来源：独立 BlobStore，默认文件系统 `data/resources`，可配置 S3 后端

资源本身被视为文件，平台只负责内容与元数据管理，不负责解释用途。

## 模块职责

### `ouroboros-resource-core`

核心模型与边界：

- `ResourceScope`
- `ResourceLayer`
- `ResourceDescriptor`
- `ResourceMetadata`
- `ResourceResolveContext`
- `ResourceReadResult`
- `ResourceService`
- `ResourceProvider` / `ResourceOverrideProvider`
- `ResourcePathUtils`（com.ouroboros.resource.util）

runtime-manage 与 resource-dev 的生产依赖只面向核心契约和路径规则；具体 Provider 的读取和写入 Bean 由 runtime 的原自动配置装配。管理层与读取层使用同一个覆盖实例，定制实现可通过核心接口替换同名 Bean。

### `ouroboros-resource-runtime`

运行时读取链路：

- `AppDefaultResourceProvider`
- `ClasspathResourceProvider`
- `RuntimeOverrideResourceProvider`
- `CompositeResourceProvider`
- `DefaultResourceService`
- `ResourceAutoConfiguration`
- `ResourceProperties`

其中 `AppDefaultResourceProvider` 会聚合：

- `resources/{scope}/...`
- `META-INF/ouroboros-resources/{scope}/...`

### `ouroboros-resource-dev`

开发平台默认资源管理：

- `ResourceDevService`
- `ResourceContentController`
- `ResourceInspector`
- 开发平台菜单、UI Model、UI Schema 元数据

资源元数据存放在应用工作区：

- `resources/{scope}/.../*.resource.json`

### `ouroboros-resource-runtime-manage`

运行时覆盖管理：

- `RuntimeResourceManageService`
- `RuntimeResourceController`
- `RuntimeResourceItem`
- `ResourceRuntimeManageAutoConfiguration`
- 系统设置菜单、权限、UI Model、UI Schema 元数据

## 主要接口

### 开发期内容接口

- `POST /api/dev/{appName}/resource/content`
- `GET /api/dev/{appName}/resource/content`
- `DELETE /api/dev/{appName}/resource/content`

说明：

- 元数据 CRUD 复用开发模块标准 `/api/dev/{appName}/resource`
- 内容接口使用 `scope + key` 标识资源

### 运行时覆盖接口

- `GET /api/sys/resource/runtime`
- `GET /api/sys/resource/runtime/effective`
- `POST /api/sys/resource/runtime/content`
- `DELETE /api/sys/resource/runtime/content`
- `POST /api/sys/resource/runtime/restore`

## 元数据格式

首版主线采用 sidecar 元数据：

- 文件：`<resource file>`
- 元数据：`<resource file>.resource.json`

`index.json` 仍可提供补充元数据，但不再是资源工作的前提条件。

## 权限边界

- 开发期资源管理：跟随开发平台应用编辑能力
- 运行时覆盖管理：面向系统管理员
- 普通业务用户：没有资源管理入口

## 多租户扩展

当前没有统一租户架构。

实现层已在 `ResourceResolveContext.qualifiers` 预留扩展口，未来可以在 `RUNTIME_OVERRIDE` 层叠加租户或环境维度，而不破坏现有模型。

## 测试入口

仓库包含以下资源模块测试。列表说明测试归属，不代表当前 HEAD 已运行通过；运行结论须附具体命令、环境与基线：

- `RuntimeResourceManageServiceTest`
- `RuntimeResourceControllerTest`
- `RuntimeResourceUiMetadataTest`
- `DefaultResourceServiceTest`
- `ClasspathResourceProviderTest`
- `ResourcePathUtilsTest`
- `ResourceDevServiceTest`
- `ResourceContentControllerTest`
- `ResourceUiMetadataTest`

## 依赖与装配

- `ouroboros-resource-core` 提供模型、Provider / Service 契约与路径规则；`runtime` 依赖 `core` 和公共 `storage-runtime`，承担具体 Provider 装配。`dev` 的资源生产依赖只面向 `core`，另依赖既有 VCS 与开发脚手架；`runtime-manage` 依赖 `core` 契约、`common-lock`、`common-web` 与安全机制，`resource-runtime` 和 `webapi-runtime` 仅为测试依赖。
- `ability/ouroboros-app-runtime` 引入资源读取链路；`ouroboros-ability-all-runtime` 引入 `ouroboros-resource-runtime-manage`，向普通应用提供运行时覆盖管理。`ouroboros-dev-module-all` 引入 `ouroboros-resource-dev`，提供开发态默认资源编辑。运行时管理沿用 `systemSetting.resourceManagement` 权限，须向管理员角色授予该权限。
- `core` 还对外提供 `ResourceProvider` 提供者契约；`ouroboros-resource-dev` 除开发平台默认资源管理能力外还包括 `ResourceDirectoryController` 与 `ResourceViewController`。

## 扩展装配

- 运行时读取链路由 `ResourceAutoConfiguration` 显式装配：`AppDefaultResourceProvider` 聚合应用工作区默认资源、classpath 资源与 `META-INF/ouroboros-resources/`，`RuntimeOverrideResourceProvider` 负责 `data/resources` 覆盖层，`DefaultResourceService` 接收这两个 provider，分层读取时先查覆盖再查默认；其字符串路径接口使用内部 `CompositeResourceProvider`。自动配置另提供一个同来源组合 Bean；整个链路以 `@ConditionalOnMissingBean` 开放替换。
- 模块当前未启用通用 `ResourceProvider` SPI 自动发现，因此扩展提供者应通过自定义 Spring Bean 覆盖默认装配，而不是依赖 SPI 注册。
- 边界补充：模块不提供平台级共享资源，也不把资源模块做成模板引擎或内容语义中心。

## 待收敛迹象

- `ResourceService` 位于 `spi` 包并保留了返回 `source = "legacy-resource-provider"` 的默认实现。
- `ResourceIndex` 与 `ResourceIndexLoader` 仍服务于 `ClasspathResourceProvider` 的 `index.json` 兼容路径，说明“sidecar 为准、`index.json` 为辅”的过渡尚未完全结束。

运行时覆盖后端支持独立配置 filesystem / s3，内容与 sidecar 的多对象操作由原资源管理协调层负责。详见[公共存储设计](../../docs/design/cross-cutting/content-storage.md)。
