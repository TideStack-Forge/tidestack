# Ouroboros Resource Management

Ouroboros 资源模块提供应用级文件资源管理能力。

当前实现已经统一为：

- 资源范围：`FRONTEND` / `BACKEND`
- 覆盖层级：`APP_DEFAULT` / `RUNTIME_OVERRIDE`
- 生效顺序：`RUNTIME_OVERRIDE > APP_DEFAULT`

平台只负责文件内容、元数据、范围和覆盖关系的管理，不解释资源的业务语义。

## 模块结构

```text
ouroboros-resource/
├── ouroboros-resource-core
├── ouroboros-resource-runtime
├── ouroboros-resource-dev
└── ouroboros-resource-runtime-manage
```

- `ouroboros-resource-core`
  统一资源模型、读取结果、解析上下文、Provider 契约及路径规则
- `ouroboros-resource-runtime`
  运行时读取链路、默认资源聚合、运行时覆盖解析
- `ouroboros-resource-dev`
  开发平台中的应用默认资源管理
- `ouroboros-resource-runtime-manage`
  运行时覆盖资源的系统设置入口

## 核心概念

### 资源范围

- `FRONTEND`
- `BACKEND`

范围用于区分资源消费域，并映射到正式目录结构：

```text
frontend/<key>
backend/<key>
```

### 覆盖层级

- `APP_DEFAULT`
- `RUNTIME_OVERRIDE`

`APP_DEFAULT` 由两类来源聚合得到：

- 应用工作区中的默认资源文件
- classpath 中的内置资源

`RUNTIME_OVERRIDE` 是运行时文件覆盖层，命中后会直接覆盖 `APP_DEFAULT`。

### 资源 Key

`key` 是 scope 下的相对路径，例如：

- `images/logo.svg`
- `templates/invoice.docx`
- `sql/init.sql`

`appName` 只作为上下文使用，不进入资源核心模型。

### Sidecar 元数据

首版主线是“真实文件 + sidecar 元数据”：

- 文件本体：`<resource file>`
- 元数据：`<resource file>.resource.json`

sidecar 当前用于保存：

- `contentType`
- `size`
- `checksum`
- `versionTag`
- `displayName`
- `description`
- `metadata`
- `lastModifiedBy`

`scope` 和 `key` 由目录结构推导，不要求重复写入 sidecar。

### `index.json`

`index.json` 仍然支持，但只是补充元数据来源，不是首版主数据入口。

当前实现会：

- 先扫描真实文件
- 再读取 `index.json`
- 合并两者结果

对文件系统资源还会优先读取 sidecar 元数据。

## 存储结构

### 应用默认资源

应用工作区默认目录：

```text
resources/
  frontend/
  backend/
```

示例：

```text
resources/frontend/templates/invoice.docx
resources/frontend/templates/invoice.docx.resource.json
resources/backend/sql/init.sql
resources/backend/sql/init.sql.resource.json
```

### 运行时覆盖资源

运行时默认目录：

```text
data/resources/
  frontend/
  backend/
```

示例：

```text
data/resources/frontend/images/logo.svg
data/resources/frontend/images/logo.svg.resource.json
data/resources/backend/templates/report.docx
data/resources/backend/templates/report.docx.resource.json
```

### Classpath 默认资源

高代码默认资源位于：

```text
META-INF/ouroboros-resources/
  frontend/
  backend/
```

## 配置

```yaml
ouroboros:
  resource:
    appDefaultBaseDirectory: resources
    runtimeOverrideBaseDirectory: data/resources
```

说明：

- `appDefaultBaseDirectory` 指向应用默认资源目录；相对路径由进程工作目录解析，部署需显式绑定目标应用目录
- `runtimeOverrideBaseDirectory` 指向运行时覆盖目录

## 运行时读取

### 统一读取

```java
@Autowired
private ResourceService resourceService;

ResourceReadResult result = resourceService.read(
    new ResourceResolveContext(ResourceScope.FRONTEND, "images/logo.svg")
);

if (result.getEffectiveResource() != null) {
  InputStream content = result.getEffectiveResource().getContent();
  ResourceLayer layer = result.getEffectiveLayer();
}
```

### 直接读取资源内容

```java
Optional<Resource> resource = resourceService.getResource(
    ResourceScope.BACKEND,
    "sql/init.sql"
);
```

### 当前默认 Provider 组合

运行时自动配置当前显式装配以下 Provider：

- `AppDefaultResourceProvider`
- `RuntimeOverrideResourceProvider`

其中：

- `AppDefaultResourceProvider` 聚合工作区文件和 classpath 资源
- `RuntimeOverrideResourceProvider` 通过自有 BlobStore 负责运行时覆盖层
- 同属 `APP_DEFAULT` 时工作区文件优先于 classpath，文件系统元数据优先 sidecar、再 fallback 到 index.json

当前没有启用通用的 `ResourceProvider` SPI 自动发现。通过同名 Spring Bean 替换默认装配：`appDefaultResourceProvider` 暴露 core 的 `ResourceProvider`；`runtimeOverrideResourceProvider` 暴露 core 的 `ResourceOverrideProvider`（继承读取能力，增加 writeOverride / deleteOverride）。管理服务与读取服务注入同一覆盖层 Bean。替代实现无需继承默认 Provider。

`runtime-manage` 与 `resource-dev` 的生产 Maven 和 Java 依赖指向 resource-core；runtime-manage 仅在 test scope 引入 runtime 验证真实实现。装配入口位于 resource-runtime，允许引用具体实现。公共路径规则已移至 `com.ouroboros.resource.util.ResourcePathUtils`，仓库外使用原 runtime.util 包的扩展需要更新 import 并重新编译。

## 开发期资源管理

开发期资源管理面向开发平台，管理对象是 `APP_DEFAULT`。

当前实现特点：

- 元数据文件保存在应用工作区的 `resources/{scope}/.../*.resource.json`
- 文件内容与元数据分离管理
- 复用开发平台的工作区和 VCS 能力
- 开发期不区分系统管理员与普通开发者

### 页面入口

- `/dev/{appName}/resource`

### 接口说明

资源元数据 CRUD 复用开发模块标准接口：

- `/api/dev/{appName}/resource`

文件内容接口单独提供：

- `POST /api/dev/{appName}/resource/content`
- `GET /api/dev/{appName}/resource/content`
- `DELETE /api/dev/{appName}/resource/content`

内容接口使用以下参数：

- `scope`
- `key`
- `file`（上传时）

上传内容前要求对应的资源元数据已经存在。

## 运行时覆盖管理

运行时覆盖管理面向系统管理员，管理对象是 `RUNTIME_OVERRIDE`。

### 页面入口

- `/system-setting/resource-management`

### 接口说明

- `GET /api/sys/resource/runtime`
  列出资源，可按 `scope`、`directory`、`keyword` 过滤
- `GET /api/sys/resource/runtime/directories`
  列出目录
- `GET /api/sys/resource/runtime/content`
  下载内容
- `GET /api/sys/resource/runtime/effective`
  获取指定资源的当前生效结果
- `POST /api/sys/resource/runtime/content`
  上传或替换 override 文件
- `DELETE /api/sys/resource/runtime/content`
  删除 override 文件
- `POST /api/sys/resource/runtime/restore`
  删除 override 并恢复默认资源

运行时接口使用以下参数：

- `scope`
- `key`
- `file`（上传时）

返回对象 `RuntimeResourceItem` 至少包含：

- `scope`
- `key`
- `effectiveLayer`
- `hasOverride`
- `defaultExists`
- `contentType`
- `size`
- `checksum`

## 非目标与边界

- 不提供平台级共享资源
- 不在首版绑定统一租户架构
- 不把资源模块做成模板引擎、配置解释器或内容语义中心
- 不限制资源文件类型；文本和二进制都支持

## 未来扩展口

`ResourceResolveContext` 已保留 `application` 和 `qualifiers` 字段，可用于未来扩展：

- 多租户运行时覆盖
- 环境维度覆盖
- 灰度或地域维度覆盖

当前实现只保留扩展口，不引入具体租户架构。

运行时覆盖后端支持独立配置 filesystem / s3，内容与 sidecar 的多对象操作由原资源管理协调层负责。详见[公共存储设计](../../docs/design/cross-cutting/content-storage.md)。
