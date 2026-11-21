# 应用交付层

## 定位

应用交付层是把下层能力组织成可面向维护者、开发者或最终运行环境交付的顶层模块群，负责从“可复用能力”走向“可落地产品与入口”。

## 模块范围

应用交付层显式覆盖 `app-module`、`dev-module`、`platform` 和 `frontends` 四类顶层交付单元：`app-module` 面向业务应用模块，`dev-module` 面向开发支撑与低代码管理模块，`platform` 面向可运行平台应用，`frontends` 面向前端基座与微前端交付。

### 模块清单

| 模块 | 职责 |
|------|------|
| `app-module/document-management` | 文档管理应用模块 |
| `dev-module/ouroboros-dev-runtime` | 开发平台运行时 |
| `dev-module/ouroboros-dev-container` | 容器管理 |
| `dev-module/ouroboros-dev-model-manage-webapi-scaffold` | 模型管理 WebAPI 脚手架 |
| `dev-module/ouroboros-dev-app-management` | 应用管理 |
| `dev-module/ouroboros-dev-project-generation` | 项目源码 ZIP 生成与模板目录消费 |
| `dev-module/ouroboros-dev-datasource` | 数据源管理 |
| `dev-module/ouroboros-dev-environment` | 环境管理 |
| `dev-module/ouroboros-dev-version-control-system` | 版本控制 |
| `dev-module/ouroboros-dev-helper` | 开发辅助工具 |
| `dev-module/ouroboros-dev-platform-component-lib` | 平台组件库（开发侧） |
| `platform/ouroboros-develop` | 开发平台启动入口 |
| `platform/ouroboros-mothership` | 统一运行时基座启动入口 |
| `platform/ouroboros-base` | 基础镜像资产目录，不是 Maven 模块 |
| `frontends/web` | 前端基座（微前端壳层） |

## 责任边界

应用交付层负责把 `core`、`security`、`ability` 提供的稳定能力装配成具体产品形态、运行入口与前端体验，是交付编排层而不是新的公共能力层。它可以定义面向场景的组合关系，但不应反向塑造下层公共抽象。

## 典型协作关系

`app-module` 和 `dev-module` 组合下层能力形成业务应用与开发管理能力，`platform` 再承载统一启动、部署与运行入口，`frontends` 提供用户可见的前端壳层与微前端装配。应用交付层与下层的典型关系是消费能力，而不是复制实现 `core` 引擎、`security` 规则或通用业务能力。

## 契约与边界

### 对外契约（交付单元的装配入口）

| 交付单元 | 自动配置类（模块） | 对外主入口 |
|----------|--------------------|------------|
| `app-module/document-management` | `DocumentManagementAutoConfiguration:35`（`document-management-autoconfigure`，`.imports:1`）、`DocumentManagementPersistenceAutoConfiguration`、`DocumentStorageAutoConfiguration` | `DocumentLibraryManagementFacade:40`、`DocumentLibraryContentFacade:44`、`DocumentLibraryAdminFacade:41`；核心契约 `DocumentLibraryService:21`、`ACLService:13`、`StorageService:13` |
| `app-module/dynamic-form` | `DynamicFormAutoConfiguration:9`（artifact `dynamic-form-runtime`，`.imports:1`） | `DynamicFormDataService:8`、`DynamicFormDefineService:10` |
| `app-module/ui-schema-management` | `DbUiSchemaAutoConfiguration:13`（artifact `ui-schema-in-db-management-runtime`，`.imports:1`） | `CustomUISchemaService:14` |
| `dev-module/ouroboros-dev-app-management` | `DevAppRuntimeLockAutoConfiguration`、`DevAppAutoConfiguration:38` | `AppManagementService:14` |
| `dev-module/ouroboros-dev-datasource` | `DevDatasourceAutoConfiguration:19` | `AppDataSourceService:16`、`PlatformDataSourceService:16` |
| `dev-module/ouroboros-dev-project-generation` | `ProjectGenerationAutoConfiguration:26` | `ProjectGenerationService:8` |
| `platform/ouroboros-develop` | `DevelopActuatorHealthAccessConfiguration`（`.imports:1`，白名单 `GET /actuator/health`，`:11`、`:15`） | `OuroborosDevelopApplication:10`（`@SpringBootApplication:9`） |
| `platform/ouroboros-mothership` | 无自有 `.imports` | `OuroborosApplication:10`（`@SpringBootApplication:9`） |

两个平台入口的装配分工（据 `pom.xml` 依赖）：`ouroboros-develop` 装配**开发栈**——`ouroboros-ability-all-runtime:108`、`ouroboros-approval-workflow-runtime:114`、`ouroboros-approval-workflow-business-model-form:120`、`ouroboros-approval-workflow-dev:126`、`ouroboros-dev-module-all:132`、`ouroboros-app-module-model-dev:138`、`ouroboros-dev-app-management:143`；`ouroboros-mothership` 装配**统一运行时基座栈**——`ouroboros-ability-all-runtime:79`、`ouroboros-ai-starter-spring-ai:83`、`ouroboros-approval-workflow-runtime:89`、`ui-schema-in-db-management-runtime:103`、`document-management-autoconfigure:107`，且**不**包含 `ouroboros-dev-module-all`。上层应通过引入对应 runtime/autoconfigure 模块来获得能力，而不是直接依赖某个 `@Configuration` 类。

### 无自有契约的交付单元（仅装配）

- `dev-module/ouroboros-dev-module-all`：`<packaging>pom</packaging>`（`pom.xml:14`），目录内只有 `pom.xml` 与 `target/`，无 `src/main/java`——**该模块无自有契约，仅做装配聚合**（依赖清单 `pom.xml:20-58`）。
- `dev-module/ouroboros-dev-runtime`、`ouroboros-dev-container`、`ouroboros-dev-helper`：均为聚合 POM（分别含 5 / 2 / 4 个子模块），无 `src/main/java`。
- `platform/ouroboros-base` 提供基础镜像资产；可启动的 Maven 平台应用以 `platform/pom.xml` 中的 `ouroboros-develop` 与 `ouroboros-mothership` 为准。
- `app-module` 有 3 个模块（`app-module/pom.xml:17-19`）；`app-module/document-management` 自身是聚合 POM（含 7 个子模块，`document-management/pom.xml:19-25`），真正的代码在 `-core`/`-service`/`-persistence`/`-storage`/`-autoconfigure` 等子模块。

### 错误语义

- 应用层把自有异常挂在平台体系下：`CircularReferenceException extends BusinessException`（`document-management-core/.../exception/CircularReferenceException.java:22`，错误码 `-4005`，触发：移动目录形成循环引用）、`ResourceNotFoundException extends PlatformException`（`:17`，错误码 `-6001`，触发：按 ID 查不到文档库/目录/文档）、`StorageException extends InternalException`（`StorageException.java:8`）、`QuotaExceededException extends BusinessException`（`:17`，抛点 `DocumentLibraryContentFacadeImpl.java:401`"档案库配额已满，无法上传文件"）。
- 开发侧：`VCSException extends PlatformException`（`dev-version-control-system/.../vcs/exception/VCSException.java:8`）及其子类 `FileExistException`、`FileLockedException`、`NotFileException`、`VCSSecurityException`、`VCSConfigurationException`；`PlatformComponentLibException extends InternalException`（`dev-platform-component-lib/.../exception/PlatformComponentLibException.java:8`）。
- 直接抛共享类型的实例：`BlobDocumentStorageService` 的内容 IO 异常；`DynamicFormDefineServiceImpl.java:288` `BusinessException("动态表单模型名冲突: …")`；`CustomUISchemaController.java:62` `BusinessException(404,"自定义界面 Schema 不存在")`；`DevAppStompCompensationRegistrar.java:78` `ValidationException("缺少 websocket principal")`。
- **fail-closed / 默认装配语义**：`@ConditionalOnMissingBean` 提供默认实现（`DocumentManagementServiceAutoConfiguration.java:57`、`:64`、`:71`、`:78`、`:85`、`:92`、`:99`；`DocumentManagementPersistenceAutoConfiguration.java:32`、`:38`、`:44`；`DocumentStorageAutoConfiguration.java:41`；`ProjectGenerationAutoConfiguration.java:36`、`:44`；`DebugRuntimeBrokerAutoConfiguration.java:26`、`:32`）——缺失协作方时**用平台默认实现补齐**，而不是启动失败。
- 开关默认开启：`@ConditionalOnProperty(prefix="ouroboros.document-management", name="enabled", havingValue="true", matchIfMissing=true)`（`DocumentManagementAutoConfiguration.java:27`）；存储后端缺省为 `filesystem`（`DocumentStorageAutoConfiguration.java:31-36`）——引入依赖即生效，需显式关闭。
- 模型必填字段在构造期强制：`Objects.requireNonNull(name, "name 不能为 null")`（`DocumentNodeImpl.java:289-293`，同型 `AclEntryImpl.java:168-173`、`DirectoryNodeImpl.java:263-265`、`DocumentLibraryImpl.java:332-333`）。app/dev/platform main 源码中**没有**使用 Spring `Assert.*`。

### 边界条件

- **配置前提**：`ouroboros-develop` 的 `spring.application.name=ouroboros-develop`（`platform/ouroboros-develop/src/main/resources/application.yml:3-4`）；其默认 `application.yml` **不含 `server.port`**，端口只在 profile 文件里（`application-debug.yml:1-2` 为 `8888`）；部署基座口端口为 `8080`（`platform/ouroboros-mothership/docker/root/app/base/service/application-base.yml:2`，`spring.application.name: ouroboros-mothership` `:9`）。mothership 打包内的 `application.yml` 被有意清空（`:1`"把所有配置移到 application-base.yml 中"）。
- **前置条件**：document-management 默认启用，因此要求持久化与存储后端可用，配置项见 `DocumentManagementProperties.java:11-18`；dev app management 需要可写工作区根目录 `@Value("${ouroboros.dev.workspaceRoot:${ouroboros-dev.workspace-root:./workspace}}")`（`DevAppAutoConfiguration.java:40`，另有 `./debug-support:43`、`./develop-support:46`）。
- **前端交付前提**：`frontends/web` 是 pnpm workspace（`package.json:6` 固定 `pnpm@11.9.0`），基座应用 `ouroboros-web`（`main-app/package.json:2`），构建 `node ./scripts/webpack-build.js`（`:7`）、开发 `node ./scripts/webpack-serve.js`（`:26`）；文档入口由 `npm run validate:agent-entrypoints` 校验（`frontends/web/AGENTS.md:25`）。
- **明确不做**：不提供通用工具底座，不定义核心引擎协议，不定义统一安全边界，不沉淀可跨场景复用的中间层业务能力；不反向塑造下层公共抽象。
- **前端开发端口**：`main-app/webpack.runtime.js` 默认端口为 `8080`，可用 `--port` 覆盖；`webpack.config.js` 使用计算后的 `runtime.devServerPort`。

## 不负责什么

应用交付层不负责提供通用工具底座，不负责定义核心引擎协议，不负责统一安全边界，也不负责沉淀可跨场景复用的中间层业务能力。它解决的是交付与装配问题，而不是替代 `libs`、`core`、`security`、`ability` 的职责。
