# ouroboros-dev-project-generation 模块设计摘要

## 模块定位

`ouroboros-dev-project-generation` 是开发平台中的项目生成器：按 `MICRO_FRONTEND`、`PLATFORM_COMPONENT_LIBRARY`、`BACKEND_EXTENSION` 三个 scope 列出官方模板族，渲染模板并输出可下载的源码 ZIP。它只生成“本地开发起点”，不编译生成物、不写应用 VCS、不上传微前端包或扩展 Jar。

## 责任边界

该模块负责模板目录（catalog）的获取、校验与快照，模板参数校验与渲染，以及确定性 ZIP 打包；不负责模板源码与发布合同的治理（那部分位于仓库根 `project-templates/`），也不负责生成物后续的交付链路。它对外是单个 jar，没有子模块拆分。

## 依赖关系

pom 的 `<dependency>` 显示：框架侧依赖 `spring-web`、`spring-webmvc`、`spring-boot`、`spring-boot-autoconfigure`、`jackson-databind`；同仓依赖 `libs/common` 的 `common-web`、`common-dev`、`common-template`，core 域的 `ouroboros-data-typed-runtime`，`libs/util` 的 `ouroboros-util-zip`，以及 `commons-compress`、`maven-artifact`。

被谁依赖（`git grep` artifactId）：只有 `dev-module/ouroboros-dev-module-all`（聚合）与 `bom`（版本管理）；`platform/ouroboros-develop` 经 `ouroboros-dev-module-all` 间接引入。全仓 `*.java` 中未见本模块包名被其他模块 import，说明它当前只通过 REST 与自动配置对外生效，没有编译期消费者。

## 关键抽象

- `ProjectGenerationService`：模块对外服务契约（`list` / `generate`），实现为 `CatalogBackedProjectGenerationService` 与 fail-closed 的 `EmptyCatalogProjectGenerationService`。
- `ProjectTemplateCatalogResolver`、`ProjectTemplateCatalogSnapshotManager`、`ProjectTemplateCatalogParser`、`LastKnownGoodCatalog`：目录解析、快照与 last-known-good 状态。
- `SignedProjectTemplateCatalogVerifier`、`SignedCanonicalJsonVerifier`、`CanonicalJson`：目录签名与规范化 JSON 校验。
- `ProjectTemplateRemoteFetchPolicy`、`ProjectTemplateRemoteHttpClient`、`JavaNetProjectTemplateRemoteHttpClient`：远端目录获取与地址/CIDR 约束。
- `TemplateArtifactRepository`、`TemplateArchiveReader`、`DeterministicProjectZipWriter`：模板 artifact 缓存、归档读取与确定性 ZIP 写出。
- `ProjectTemplateRenderer`、`ProjectTemplateRenderContext`、`ProjectTemplateParameterValidator`、`ProjectGenerationAmisSchemaFactory`：渲染、参数校验与 AMIS 表单一并作为对外界面契约。

## 关键子模块

本模块没有 Maven 子模块，按包划分职责（60 个 main 文件）：

- `catalog`（35 个文件）：目录、签名、远端获取、replay state 与 LKG 的主体实现，是模块的重心。
- `render`（6 个）与 `parameter`（1 个）：模板渲染、路径/转义工具与参数校验。
- `service` + `service/impl`（5 个）：`ProjectGenerationService` 契约与两个实现，外加 `BuildMetadataProjectPlatformVersionProvider`。
- `controller`（1 个）：`ProjectGenerationController`，映射 `/api/dev/{appName}` 与 `/api`，提供 `/project-templates`、`/project-generation-form`、`/project-generations`（`StreamingResponseBody`）。
- `repository`、`zip`、`artifact`、`ui`、`model`、`config`：artifact 缓存、ZIP 写出、归档读取、AMIS schema、模型与本地目录装配。

## 扩展机制

装配入口是 `ProjectGenerationAutoConfiguration`（`@Configuration` + `@ComponentScan` + `@DataModelScan`），在 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 双注册；其中 `ProjectTemplateCatalogReplayStateStore` 依赖 `TypedDataModel<String, ProjectTemplateCatalogReplayStateModel>`，这是 `ouroboros-data-typed-runtime` 与 `@DataModelScan` 的用途。真实目录能力由 `LocalProjectGenerationCatalogConfiguration` 提供，受 `ouroboros.dev.project-generation.local-template-catalog.enabled=true` 条件控制，并以 `@Primary` 暴露 `ProjectGenerationService`。共享机制见 [../../cross-cutting/meta-inf-resource-conventions.md](../../cross-cutting/meta-inf-resource-conventions.md)。

## 深入文档索引

- `暂无独立深度设计文档`：`dev-module/ouroboros-dev-project-generation/**` 下未发现 `DESIGN.md`、`README.md` 或 `docs/*.md`。
- 相关专题：[../../cross-cutting/project-generation-platform.md](../../cross-cutting/project-generation-platform.md) 是该能力的跨模块治理页，覆盖模板族、合同与镜像打包约定。

## 演进状态

模块边界清晰：单 artifact、无子模块、无元数据资源目录，职责集中在“目录 → 渲染 → ZIP”。两点需要注意。其一，模板源码、合同与验证脚本都在模块外的 `project-templates/`，模块内 `ProjectGenerationArchitectureTest` 也只断言模板 artifact 不得内嵌进模块源码，因此“模块边界”不等于“模板生命周期边界”，改动模板发布链路时不能只看本模块。其二，`ProjectGenerationService` bean 目前只有 `local-template-catalog.enabled=true` 这一条装配路径，`EmptyCatalogProjectGenerationService` 虽已实现 fail-closed 语义，但 main 源码中没有任何 `@Bean` 注册它（仅在测试中直接 `new`），因此缺省配置下的降级行为未见明确证据。
