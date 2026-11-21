# Ouroboros META-INF 资源约定

## 目标

本页用于统一说明 Ouroboros 中各类 `META-INF` 资源的职责分工、命名空间和放置规则，帮助维护者判断“某份声明式资源应该放在哪里”，以及什么时候应该复用现有路径、什么时候才值得新增命名空间。

## 为什么 `META-INF` 很重要

在 Ouroboros 中，很多能力并不是通过硬编码 Java 配置直接发现，而是通过 classpath 资源被自动装配、扫描或聚合。`META-INF` 因此不只是“资源目录”，而是模块对外暴露声明式契约的重要边界：

- Spring Boot 通过它发现自动配置。
- Java SPI 通过它发现实现类。
- 平台运行时通过它发现 UI、菜单、权限、字段、节点定义等元数据。
- 模板系统通过它加载可生成的 JSON / Schema 片段。

对维护者来说，放错 `META-INF` 命名空间，往往就等于把扩展点放错了地方。

## 四类主要资源

### 1. 框架装配资源

这类资源回答“框架如何发现模块能力”：

- `META-INF/spring.factories`
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `META-INF/services/<接口全名>`

其中：

- 当前 Spring Boot 3.5 的自动配置发现以 `AutoConfiguration.imports` 为入口；存量 `spring.factories` 中的 `EnableAutoConfiguration` 登记用于兼容，不能单独证明 Boot 3.5 会加载配置。`spring.factories` 中其他工厂键仍按各自消费者生效。
- `services/` 用于 Java SPI。

它们的特点是“描述 Java 入口类”，而不是承载业务元数据本身。

### 2. `META-INF/ouroboros/` 平台元数据

这类资源回答“平台运行时要发现哪些声明式对象”。当前仓库里已经形成了较稳定的命名空间族：

- `metadata/`：当前标准 typed 模型元数据，源码目录是 `src/main/metadata/`。
- `ui-model/`：legacy 页面元数据；typed 新文件使用 `metadata/**/*.ui-model.json`。
- `ui-schema/`：legacy UI Schema；typed 新文件使用 `metadata/**/*.ui-schema.json` 并带 `{ name, schema }` 包裹。
- `menu-model/`：菜单模型定义。
- `authority/`：权限声明。
- `app-modules.json`：应用模块注册。
- `configurations.json`：配置元数据。
- `metadata/**/*.dev-menu.json`：开发菜单定义。
- `metadata/**/*.field-type.json`：字段类型定义。
- `metadata/**/*.flow-node.json`：逻辑流节点定义 JSON。
- `metadata/**/*.rpc.json`、`metadata/**/*.rpc.auth.json`：RPC 模型与凭据元数据。
- `metadata/**/*.notification.json`：通知模板资源。
- `metadata/**/*.widget.json`：组件卡片定义。
- `widget-definition-generator-form-items/`：组件生成器表单定义。
- `metadata/app.metadata.json`、`metadata/modules.diff.json`、`metadata/style.css`：固定文件资源。
- `i18n/messages/`：国际化消息资源。

这些路径的共同点是：内容通常是 JSON 或其他声明式资源，且会被某个 provider、controller、runtime center 或资源扫描器按固定目录读取。

### 3. 模板资源

这类资源回答“有哪些结构化内容可以在运行时生成”：

- `META-INF/templates/`
- `META-INF/editor-schema-templates/`
- `META-INF/ouroboros/dev/fragment-schema/`

它们不直接等于最终元数据，而是模板输入。真正消费时往往会经过 `JarTemplateEngine`、业务模板工具类或开发平台的片段接口再生成最终 JSON。

### 4. 测试资源

`src/test/resources/META-INF/...` 用于构造测试时的 classpath 场景。它通常复用正式运行时的命名空间，而不是另造一套测试专用路径，例如：

- `src/test/resources/META-INF/services/...`
- `src/test/resources/META-INF/ouroboros/metadata/*.rpc.json`
- `src/test/resources/META-INF/ouroboros/ui-schema/...`

这样测试才能真正验证“运行时扫描到的就是这份资源”。

## 命名空间选择规则

新增资源时，建议按下面顺序判断：

1. 这是框架入口类声明，还是平台元数据，还是模板？
2. 如果是平台元数据，现有 `META-INF/ouroboros/` 下是否已有同语义命名空间？
3. 如果只是某个已有命名空间里的新条目，应新增文件，不应新造顶级目录。
4. 只有当消费方语义明显不同、且已有目录无法准确承载时，才新增新的资源目录。

换句话说，优先复用“按消费方语义划分”的目录，而不是每来一个模块就发明一个新桶。

## 命名与层级建议

当前仓库已经体现出几条比较稳定的命名规则：

- 目录名优先表达资源族语义，例如 `ui-model`、`authority`、`logic-flow-node-define`，而不是表达临时任务名。
- 文件名优先使用稳定对象标识，例如模块名、节点名、页面名，而不是描述一次性用途。
- 面向开发平台的资源会进一步落到 `dev/{appName}/...` 层次中，让“运行时资源”和“开发态资源”分离。
- 同类资源应尽量保持 JSON 结构与路径粒度一致，避免一部分目录按对象拆文件、另一部分又全部堆进单个大文件。

## 什么时候该新增目录

只有在满足以下条件时，才建议新增新的 `META-INF/ouroboros/<namespace>/`：

- 已有命名空间无法准确表达该资源的消费语义。
- 该资源会被多个模块稳定复用，而不只是某个模块私有细节。
- 维护者能够明确说清“谁来读它、什么时候读、与邻近目录有什么边界”。

否则，更合适的做法通常是：

- 在现有命名空间下新增文件。
- 把模块私有模板放在现有模板目录内。
- 或者干脆让它留在模块私有资源路径，不进入共享命名空间。

## 与代码扫描器的关系

`META-INF` 资源约定必须和消费代码一起看待。例如：

- `JarUIModelProvider` 读取 `META-INF/ouroboros/ui-model/` legacy 资源和 `META-INF/ouroboros/metadata/**/*.ui-model.json` typed 资源。
- `JarUISchemaProvider` 读取 `META-INF/ouroboros/ui-schema/` legacy 资源和 `META-INF/ouroboros/metadata/**/*.ui-schema.json` typed 资源。
- `JarAppModuleProvider` 读取 `META-INF/ouroboros/app-modules.json`
- `JarLogicFlowNodeDefineProvider` 读取 `META-INF/ouroboros/metadata/**/*.flow-node.json`

因此，新增资源目录时，应该先确认是否已经有明确的读取者；如果没有，说明这还不是一个成熟的共享约定。

普通 typed 模型元数据的发现逻辑应优先复用 `common-metadata` 模块：由 provider 声明 `TypedMetadataSpec`（类型后缀、legacy/typed 资源计划、解析链、key 提取和合并策略），由 `TypedMetadataCatalog` 统一执行候选资源读取、解析、空 key 诊断、重复 key 合并和 trace 记录。classpath/JAR 资源使用 `ClasspathMetadataSource`，appHome/文件系统资源使用 `FileSystemMetadataSource`；具体 provider 只保留领域日志和对外接口适配，不再各自手写一套 typed suffix 扫描和覆盖规则。

## 维护约束

维护 `META-INF` 资源时，应坚持以下约束：

- 不要让同一语义在多个目录重复声明。
- 不要把测试资源路径当作正式命名空间设计来源。
- 不要因为单个模块的便捷性，就破坏全局命名空间的一致性。
- 当资源目录成为跨模块约定时，应把规则提炼回 `docs/design`，而不是只留在某个 README 或实现类里。

## 深入阅读入口

- [extension-mechanisms.md](extension-mechanisms.md)
- [spi-governance.md](spi-governance.md)
- [template-and-script-runtime.md](template-and-script-runtime.md)
- [../../../libs/common/common-template/GUIDE.md](../../../libs/common/common-template/GUIDE.md)
- [../../guides/development-module-guide.md](../../guides/development-module-guide.md)
