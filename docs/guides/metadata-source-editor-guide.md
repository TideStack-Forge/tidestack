# Metadata Source Editor Guide

## 目的

作者态元数据文件放在模块的 `src/main/metadata` 目录中。它们仍然是 JSON 声明式元数据源码，构建后进入 `META-INF/ouroboros/metadata/**`，运行时继续通过现有 Provider + Center 链路发现。普通模型保留 legacy loader 时会先读旧目录再读新 suffix；已经破坏性升级的类型只读新 `metadata` 短后缀文件。

## 目录与后缀

模型声明使用 JSON 文件，通常采用 `.<短类型>.json` 后缀；固定集合文件和原始 `style.css` 资源按下表约定维护。完整 IDE 注册以 [公共类型资源](../../ide-plugins/common/metadata-editor-schema/metadata-types/) 为准。

| 类型 | 作者态路径 | 打包态路径 |
|---|---|---|
| 权限 | `src/main/metadata/**/*.authority.json` | `META-INF/ouroboros/metadata/**/*.authority.json` |
| 菜单 | `src/main/metadata/**/*.menu.json` | `META-INF/ouroboros/metadata/**/*.menu.json` |
| 开发菜单 | `src/main/metadata/**/*.dev-menu.json` | `META-INF/ouroboros/metadata/**/*.dev-menu.json` |
| UI 模型 | `src/main/metadata/**/*.ui-model.json` | `META-INF/ouroboros/metadata/**/*.ui-model.json` |
| UI Schema | `src/main/metadata/**/*.ui-schema.json` | `META-INF/ouroboros/metadata/**/*.ui-schema.json` |
| 业务模型 | `src/main/metadata/**/*.business.json` | `META-INF/ouroboros/metadata/**/*.business.json` |
| 逻辑流模型 | `src/main/metadata/**/*.logicflow.json` | `META-INF/ouroboros/metadata/**/*.logicflow.json` |
| 字段类型 | `src/main/metadata/**/*.field-type.json` | `META-INF/ouroboros/metadata/**/*.field-type.json` |
| RPC 模型 | `src/main/metadata/**/*.rpc.json` | `META-INF/ouroboros/metadata/**/*.rpc.json` |
| RPC 认证 | `src/main/metadata/**/*.rpc.auth.json` | `META-INF/ouroboros/metadata/**/*.rpc.auth.json` |
| WebAPI 模型 | `src/main/metadata/**/*.webapi.json` | `META-INF/ouroboros/metadata/**/*.webapi.json` |
| 审批流 | `src/main/metadata/**/*.workflow.json` | `META-INF/ouroboros/metadata/**/*.workflow.json` |
| 通知模板 | `src/main/metadata/**/*.notification.json` | `META-INF/ouroboros/metadata/**/*.notification.json` |
| 数据字典 | `src/main/metadata/**/*.dict.json` | `META-INF/ouroboros/metadata/**/*.dict.json` |
| 多语言 | `src/main/metadata/**/*.i18n.json` | `META-INF/ouroboros/metadata/**/*.i18n.json` |
| 消息队列 | `src/main/metadata/**/*.mq.json` | `META-INF/ouroboros/metadata/**/*.mq.json` |
| 调度任务 | `src/main/metadata/**/*.schedule.json` | `META-INF/ouroboros/metadata/**/*.schedule.json` |
| 业务日志模板 | `src/main/metadata/**/*.business-log.json` | `META-INF/ouroboros/metadata/**/*.business-log.json` |
| 错误码 | `src/main/metadata/error-codes.error.json` | `META-INF/ouroboros/metadata/error-codes.error.json` |
| 数据初始化 | `src/main/metadata/**/*.data-init.json` | `META-INF/ouroboros/metadata/**/*.data-init.json` |
| 组件定义 | `src/main/metadata/**/*.widget.json` | `META-INF/ouroboros/metadata/**/*.widget.json` |
| 逻辑流节点 | `src/main/metadata/**/*.flow-node.json` | `META-INF/ouroboros/metadata/**/*.flow-node.json` |
| 微前端 | `src/main/metadata/**/*.micro-web.json` | `META-INF/ouroboros/metadata/**/*.micro-web.json` |
| 岗位定义 | `src/main/metadata/**/*.position.json` | `META-INF/ouroboros/metadata/**/*.position.json` |
| 应用元数据 | `src/main/metadata/app.metadata.json` | `META-INF/ouroboros/metadata/app.metadata.json` |
| 模块树 diff | `src/main/metadata/modules.diff.json` | `META-INF/ouroboros/metadata/modules.diff.json` |
| 自定义样式 | `src/main/metadata/style.css` | `META-INF/ouroboros/metadata/style.css` |
| 应用模块 | `src/main/metadata/app-modules.json` | `META-INF/ouroboros/metadata/app-modules.json` |
| 配置项（JAR 集合） | `src/main/metadata/configurations.json` | `META-INF/ouroboros/metadata/configurations.json` |
| 配置分组（JAR 集合） | `src/main/metadata/configuration-groups.json` | `META-INF/ouroboros/metadata/configuration-groups.json` |

数据初始化文件的 JSON 合同、幂等语义、触发时机和排障方式见 [数据初始化模块使用指南](data-initialization-guide.md)。

配置项与配置分组的 JAR Provider 读取上表的固定数组文件。IDE 另支持 `.config.json` / `.config-group.json` 单对象编辑，这两类文件的运行时落点是应用 home 的 `configuration/` / `configuration-group/`；IDE 能打开或创建某个后缀不证明 JAR Provider 会加载它。完整格式和兼容路径见[配置定义约定](../design/cross-cutting/configuration-definition-conventions.md)。

短后缀不机械追加 `-model` / `-template` / `-definition`。例如业务模型使用 `.business.json`，通知模板使用 `.notification.json`，组件定义使用 `.widget.json`。
一期 schema 对齐现有运行时模型合同：权限、菜单和开发菜单均以 `fullName` 作为唯一名称字段；菜单使用 `url/placement/order`，开发菜单额外使用 `stage`。应用模块、配置项和配置分组的旧规范是一个 JSON 数组，编辑器使用 AMIS `combo` 编辑条目，不把数组错误包装成对象。

UI Schema 在新 `metadata` 目录中推荐使用统一包裹格式：`name` 表示运行时 schemaId / path，`title` 和 `description` 供编辑器展示，`schema` 承载真正的 AMIS schema。`name` 与低代码 UI Schema Provider 保持一致，默认使用点分格式，例如 `Runtime.Page`，运行时会映射为 `/runtime/page`。旧目录 `META-INF/ouroboros/ui-schema/**/*.json` 仍保持裸 AMIS schema；可视化编辑器会识别包裹格式，顶部编辑 `name/title/description`，下方用 AMIS Editor 编辑内部 `schema`。

IDE 可视化编辑器按文件后缀注册，不要求文件必须位于 `src/main/metadata` 才能激活；`*.business.json`、`*.webapi.json`、`*.position.json`、`*.config.json` 和 `*.config-group.json` 等后缀文件在任意目录打开时都会匹配对应编辑器。业务模型编辑器使用从 `DefaultBusinessModelEditorSchemaGenerator` 导出的默认业务模型 AMIS form，覆盖基础信息、字段与关联、流程绑定、归档、历史模型和功能开关等默认业务模型配置。

开发平台 appHome 侧新建文件也使用同一组短后缀。业务模型一直使用 `business-model/**/*.business.json`。普通模型当前保留 legacy read 扩展：`logic-flow/**/*.logicflow.json`、`ui-model/**/*.ui-model.json`、`ui-schema/**/*.ui-schema.json` 是新标准，旧 `*.json` 仍可读；微前端固定目录使用 `micro-web/*.micro-web.json`，旧 `micro-web/*.json` 仍可读。保存更新已有 legacy 文件时保留原后缀，避免同名双文件。破坏性升级类型不再读 legacy：字段类型使用 `field-types/**/*.field-type.json`，错误码使用根目录固定集合文件 `error-codes.error.json`，审批流使用 `workflow/**/*.workflow.json`。

固定文件按来源优先级合并：`app.metadata.json` 进行对象合并，`modules.diff.json` 合并所有 diff 条目，`style.css` 直接拼接样式文本。微前端元数据暂时仍按固定目录/入口消费，不做全目录广义扫描。

## IDE 新建模板

IDE 插件为已支持的元数据类型内置文件模板，模板内容与本页的新规范路径和后缀保持一致。

- VS Code：使用命令面板或资源管理器上下文菜单中的 `TideStack Metadata: New File`。
- VS Code：也可以在资源管理器中右键目录，直接选择某一类 TideStack metadata 模板创建文件。
- IntelliJ IDEA：在 Project 视图中使用 `New > 创建 TideStack 模型`。
- VS Code 先询问文件路径，再询问类型字段；IntelliJ IDEA 在同一表单填写文件路径和 `name/fullName/path`、`title`、`description` 等模板字段。
- 后缀类元数据默认创建到 `src/main/metadata`，并按 `.authority.json`、`.menu.json`、`.dev-menu.json`、`.ui-model.json`、`.ui-schema.json`、`.business.json`、`.logicflow.json`、`.field-type.json`、`.rpc.json`、`.rpc.auth.json`、`.webapi.json`、`.workflow.json`、`.notification.json`、`.business-log.json`、`.error.json`、`.data-init.json`、`.widget.json`、`.flow-node.json`、`.micro-web.json`、`.position.json`、`.config.json`、`.config-group.json` 识别类型。
- 应用模块、配置项、配置分组、应用元数据、模块树 diff 和自定义样式在 JAR 中使用固定集合/固定文件；其中配置使用 `configurations.json` / `configuration-groups.json`，其余使用 `src/main/metadata/app-modules.json`、`app.metadata.json`、`modules.diff.json`、`style.css`。

## IDE 编辑器边界

- 公共编辑器拆为 `ide-plugins/common/metadata-editor-schema` 与 `ide-plugins/common/metadata-editor-runtime`：schema 包负责类型/编辑器注册/i18n 等纯资源，runtime 包负责类型识别、AMIS renderer schema 消费、JSON 解析、稳定序列化、Logical Host 和 IDE 文件桥合同。
- VS Code 宿主在 `ide-plugins/vscode-plugin/`，只负责 Custom Text Editor、Webview、Document edit、diagnostics 和 message bridge。
- JetBrains 宿主在 `ide-plugins/intellij-plugin/src/main/kotlin/com/ouroboros/idea/metadata`，通过 `FileEditorProvider` 接入 JCEF；JCEF 不可用时回退到普通 JSON 文本编辑，并保留 schema diagnostics。
- Web runtime 不直接调用 VS Code API 或 JetBrains OpenAPI；IDE 能力只能通过读写桥注入。

## 构建边界

Maven 默认只把 `src/main/resources` 当作 main resources；`src/main/metadata` 不会自动进入 jar。根 POM 通过标准 `maven-resources-plugin` 增加继承执行，把每个模块的 `src/main/metadata/**` 复制到 `target/classes/META-INF/ouroboros/metadata/**`，再随模块 jar 一起打包。不要新增运行时 loader 直接读取 `src/main/metadata`，也不要新增绕过 Provider + Center 的元数据来源。

代表验证命令：

```bash
mvn -pl core/ouroboros-resource/ouroboros-resource-dev -am process-resources
```

完成后应能在 `target/classes/META-INF/ouroboros/**` 看到对应 metadata fixture。
