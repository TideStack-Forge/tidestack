# ${artifactId}

这是一个基于 Ouroboros 模块 Maven archetype 创建的业务模块项目。项目默认复用 Ouroboros 基座能力，通过 Java 代码、Spring 自动配置、SPI 和 `src/main/metadata/` 元数据扩展业务能力。

## 项目定位

- **Group ID**：`${groupId}`
- **Artifact ID**：`${artifactId}`
- **Version**：`${version}`
- **运行基座**：由 `pom.xml` 中的 `ouroboros.version` 指定，与目标实例配套
- **技术栈**：Java 21 bytecode、JDK 21 toolchain、Spring Boot 3.5.11、Maven

## 目录说明

| 路径 | 用途 |
|------|------|
| `pom.xml` | 模块构建、依赖和插件配置 |
| `src/main/java/` | Java 源码和 Spring 自动配置入口 |
| `src/main/metadata/` | 作者态 JSON 元数据源码，构建时打包到 `META-INF/ouroboros/metadata/` |
| `src/main/resources/` | Spring 配置、SPI 注册文件和普通 classpath 资源 |
| `.agents/skills/` | 面向 Agent 的 Ouroboros 资源定义与本地运行辅助 skills |
| `AGENTS.md` | Agent 编码约束、工作流和项目协作说明 |

## 开发工具

推荐安装 **TideStack Develop** 插件以获得更好的开发体验：使用 IntelliJ IDEA 时，可在 JetBrains Marketplace 搜索并安装 `TideStack Develop`；使用 Visual Studio Code 时，可在 VS Code Marketplace 搜索并安装同名插件。插件提供模型元数据的可视化编辑、项目开发配置和本地调试相关能力，适合与本模板生成的项目配合使用。

## 常用命令

```bash
mvn -DskipTests package
```

本模块可以装配进自有启动程序，也可以作为扩展交给标准基座加载。仅标准基座扩展模式使用依赖过滤；内嵌基座模式的启动模块必须携带完整运行依赖。

交付标准基座扩展时，传入与目标基座配套的运行依赖清单：

```bash
mvn -DskipTests package -Douroboros.extension.manifestUrl=<manifest-url>
```

交付本项目 JAR 和 `target/extension-lib/`，核对过滤报告；不要再交付完整 `target/lib/`。内嵌基座工程从本模板创建业务模块后，应移除扩展依赖过滤 profile 和不使用的依赖复制执行。

## 类型化数据模型编译

Java `@Model` 类需要生成查询字段时，执行：

```bash
mvn clean verify -Ptyped-data-model
```

模板通过 `maven-compiler-plugin` 的 `annotationProcessorPaths` 接入 `ouroboros-data-typed-meta-processor`，生成同包的 `模型类名Meta` 与类型化字段，输出在 `target/generated-sources/annotations/`。此能力适用于两种工程模式，与扩展依赖过滤独立。

处理器与 typed-core 使用配套的平台版本，处理器不进入运行依赖。已有 Lombok 等处理器时保留其路径；引用生成类的模块需在 IDE 和 CI 中一致启用 profile，或把配置移入常规构建。生成类不代替运行期的 `@DataModelScan`、typed runtime 和权限检查。

## 资源定义

作者态元数据源码统一放在 `src/main/metadata/` 下，并在构建时进入 `META-INF/ouroboros/metadata/`。如果项目还保留旧目录格式，可使用 `.agents/skills/ouroboros-metadata-migration/` 迁移。

| 资源 | 默认路径 |
|------|----------|
| 应用模块 | `src/main/metadata/app-modules.json` |
| 菜单 | `src/main/metadata/<namespace>/<capability>.menu.json` |
| 权限 | `src/main/metadata/<namespace>/<capability>.authority.json` |
| UI Model | `src/main/metadata/<namespace>/<capability>.ui-model.json` |
| UI Schema | `src/main/metadata/<namespace>/<capability>.ui-schema.json`，内容为 `{ "name": "<namespace>/<capability>", "schema": { ... } }` |
| 配置元数据 | `src/main/metadata/configurations.json`、`src/main/metadata/configuration-groups.json` |

编辑资源定义时优先使用 `.agents/skills/` 中对应的 skill，并保持跨资源引用一致。

## 开发约定

- 优先复用 Ouroboros 基座机制，不创建平行注册表、权限体系或资源加载路径。
- Spring 自动配置和 SPI 必须放入约定的 `META-INF/` 路径；Ouroboros 元数据使用 `src/main/metadata/`。
- 运行期可调整的配置通过 Ouroboros configuration 资源声明；系统级静态配置走 Spring 配置。
- 新增或修改代码后至少执行 `mvn -DskipTests package`。

## 下一步

1. 按业务需要补充模块说明、架构说明和开发指南。
2. 根据业务能力新增对应的 `src/main/metadata/` 元数据。
3. 用 `.agents/skills/` 中的校验脚本或说明检查资源 JSON 结构。
