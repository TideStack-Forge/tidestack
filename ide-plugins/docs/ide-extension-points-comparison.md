# IntelliJ 与 VS Code 插件扩展点对比

> 适用读者：TideStack Develop 插件维护者。本文用于在新增插件能力前判断哪些内容应沉淀到 `common` 契约，哪些内容应留在 IDE adapter 中。

## 核心结论

IntelliJ Platform 与 VS Code 的插件体系不是一一同构关系：

- IntelliJ 更像“平台级 extension point + action + service”体系，适合深度接入项目模型、编辑器、检查、运行配置和原生设置页。
- VS Code 更像“`package.json` contribution point + activation event + runtime API”体系，适合轻量声明入口，再通过 Webview、Custom Editor、LSP、Debug Adapter、Task 等协议化能力实现深度功能。
- TideStack Develop 的公共层应定义业务契约、编辑器契约、文件匹配契约、创建模板契约和平台交互契约；IDE 插件只负责把这些契约挂到各自平台扩展点上。

## 扩展点对比表

| 扩展方向 | IntelliJ / JetBrains Platform | VS Code | TideStack 设计判断 |
|---|---|---|---|
| 插件声明入口 | `plugin.xml` 声明插件信息、依赖、extensions、actions、listeners、services 等。 | `package.json` 声明 `contributes`、`activationEvents`、`main`、commands、menus 等。 | 插件 ID、显示名、菜单入口属于 IDE adapter；业务契约不要写死在这些声明里。 |
| 平台扩展机制 | 通过 `<extensions>` 消费平台 EP；插件也可以通过 `<extensionPoints>` 暴露自己的 EP。 | 没有完全等价的 EP 注册体系；通常用 contribution points、commands、context keys、extension exports 协作。 | 若未来做“插件的插件”，IntelliJ 可以用 EP；VS Code 更适合命令或共享包协议。 |
| 生命周期 / 激活 | 由 EP、Action、Service、Startup Activity、Listener 等接入 IDE 生命周期。 | 由 activation events 激活，例如 `onCommand`、`onLanguage`、`onCustomEditor`、`onView` 等。 | VS Code 要避免过早激活；IntelliJ 要避免在 Action 中放业务逻辑，优先下沉到 service。 |
| 命令 / 动作 | `AnAction`、`ActionGroup`，可挂到菜单、工具栏、右键菜单、快捷键体系。 | `contributes.commands` + `vscode.commands.registerCommand`；菜单由 `contributes.menus` 和 `when` 控制。 | 两边入口可以不同，但最终应调用同一套 common 语义或等价 adapter。 |
| 菜单 / 右键入口 | `add-to-group` 挂到 `ToolsMenu`、`ProjectViewPopupMenu`、`NewGroup`、工具栏等。 | `menus` 挂到 command palette、explorer/context、editor/title、editor/context 等。 | 复杂路径规则不要塞进 VS Code `when`；点击后用 common contract 计算和提示。 |
| 设置页 / 配置入口 | `applicationConfigurable` / `projectConfigurable` 加入 Settings。 | `contributes.configuration` 暴露设置；复杂项目配置通常用 command + Webview/QuickPick/文件编辑。 | TideStack Develop Config 是项目级配置：IntelliJ Settings 是权威页，Tools/右键是快捷入口；VS Code 命令入口读写同一组文件。 |
| 后台服务 / 状态管理 | Application / Project / Module Service，适合封装项目级能力和运行态状态。 | extension singleton、workspace/global state、SecretStorage、subscriptions。 | 公共纯语义放 common；IDE 侧 service 只封装项目根、文件系统、通知、构建和调试。 |
| 工具窗口 / 侧边视图 | Tool Window EP，可提供停靠面板。 | `views`、`viewsContainers`、TreeView、WebviewView。 | 平台面板类能力可以共享 Web runtime，但入口和生命周期由 IDE adapter 决定。 |
| 自定义编辑器 | `FileEditorProvider`，可实现 Swing/JCEF/native editor，能与 IDE 编辑器模型深度融合。 | `customEditors` + `CustomTextEditorProvider` / `CustomReadonlyEditorProvider`，通常基于 Webview。 | 元数据可视化编辑应共享 Web 壳和 Host；IntelliJ 用 JCEF，VS Code 用 Webview。 |
| Web UI 承载 | JCEF、Swing、Compose UI 等；JCEF 可用性、焦点、拖拽、主题需要额外适配。 | Webview 是一等能力；Custom Editor、Webview Panel、Webview View 都围绕它。 | AMIS/AMIS Editor 在 VS Code 中更自然；IntelliJ 必须保留 JCEF 降级和主题/交互适配。 |
| 文件类型 / 语言支持 | FileType、Language、PSI、Parser、Annotator、Inspection、Completion、Reference 等。 | `languages`、TextMate grammar、semantic tokens、diagnostics、completion、hover、definition、code action；复杂语言通常走 LSP。 | 如果只是 JSON metadata，用文件匹配 + schema 校验即可；不要为一期过早引入完整语言插件。 |
| 诊断 / 检查 / 快速修复 | Inspections、Annotators、Intentions、QuickFix、Problems View 集成。 | DiagnosticCollection、CodeActionProvider、Problem Matchers。 | Schema 校验规则应在 common；IDE adapter 发布对应 IDE 的 diagnostics。 |
| 运行 / 调试 | Run Configuration、Configuration Producer、Program Runner、Debugger 集成。 | `debuggers` contribution、Debug Adapter Protocol、tasks、TaskProvider。 | Deploy & Debug 的平台 API 契约应共享；IntelliJ 和 VS Code 的构建/attach 方式分别实现。 |
| 构建 / 任务 | Maven/Gradle 集成、External System、Run Configuration、Tool Window。 | `tasks`、`taskDefinitions`、TaskProvider、Terminal API。 | Java/Maven/Gradle 项目中 IntelliJ 可拿更丰富项目模型；VS Code 更依赖命令、tasks 和工作区约定。 |
| 项目模板 / 向导 | 可使用项目模板/向导相关 EP。 | 没有完全等价的原生 New Project Wizard；通常用 commands、QuickPick、Webview、workspace edits。 | 模板描述放 common；IntelliJ 使用原生向导，VS Code 使用命令式 scaffold。 |
| 通知 / 状态栏 / 进度 | NotificationGroup、ProgressManager、StatusBarWidget、ToolWindow。 | `window.showInformationMessage`、Progress API、StatusBarItem。 | 进度和通知文案从 common i18n 取；具体展示和 action callback 留给 IDE adapter。 |
| 文件系统 / 工作区模型 | VirtualFile、Project/Module Model、PSI、Index。 | `workspace.fs`、WorkspaceFolder、FileSystemProvider。 | 目录定位、模块根解析是 IDE adapter；元数据路径规范和创建计划是 common contract。 |
| 发布与兼容声明 | `plugin.xml` 声明 id/name/dependencies/since-build 等，发布到 JetBrains Marketplace。 | `package.json` 声明 publisher、engines.vscode、contributions，打包 VSIX/Marketplace。 | 发布后的插件 ID 不可随意更改；公共契约版本要独立于 Marketplace 包装信息演进。 |

## TideStack Develop 分层落点

| 能力 | 公共层应沉淀 | IntelliJ adapter | VS Code adapter |
|---|---|---|---|
| 元数据类型与文件匹配 | metadata type contribution、editor registration、metadata creation contract。 | 读取 manifest，注册多个 `FileEditorProvider` / `AnAction`。 | 读取 manifest，生成 `customEditors` / commands / menus。 |
| 可视化编辑器 | Web runtime、AMIS schema、Host 协议、序列化和诊断语义。 | JCEF 承载；JCEF 不可用时保留文本/诊断回退。 | Webview custom editor 承载。 |
| 新建元数据文件 | 默认路径、后缀补全、固定集合文件规则、模板字段默认值和 JSON 文本。 | `NewGroup` / Project View 入口负责选目录、弹表单、写 VirtualFile。 | Explorer context / Command Palette 负责选目录、QuickPick/InputBox、写 workspace.fs。 |
| TideStack Develop Config | config contract、字段顺序、legacy alias、shared/local 文件语义。 | Settings Configurable + Tools/右键快捷入口 + 缺配置通知 action。 | Command Palette / Open Config Files / 后续 Webview 或 QuickPick 配置入口。 |
| Deploy & Debug | 平台 API DTO、配置读取语义、目标解析语义、错误文案。 | Maven/Gradle build、上传进度、Remote JVM Debug attach。 | Task/Terminal build、Debug Adapter 或 attach configuration。 |
| 项目模板 | 模板描述、默认目录、示例 metadata、构建片段。 | New Project Wizard 或等价原生入口。 | 命令式 scaffold + workspace edits。 |

## 设计原则

- 公共层定义“是什么”和“如何校验”，IDE adapter 定义“在哪里展示”和“如何接入 IDE 生命周期”。
- 不把 metadata 类型、文件后缀、固定文件名、配置字段、平台 DTO 分别写进两个插件实现中。
- 不为了追求入口一致而牺牲 IDE 习惯：IntelliJ 可以用 Settings/Tools/NewGroup；VS Code 可以用 Command Palette/Explorer/custom editor。
- 入口可以不同，但点击后的业务行为必须一致：同一份 contract、同一份 schema、同一份 i18n、同一份序列化规则。
- 对需要静态声明的地方，优先从公共 manifest 生成或复制最小静态片段；不要把静态声明当成业务事实源。

## 官方参考

- JetBrains Plugin Configuration File: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html
- JetBrains Extensions: https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html
- JetBrains Action System: https://plugins.jetbrains.com/docs/intellij/action-system.html
- JetBrains Services: https://plugins.jetbrains.com/docs/intellij/plugin-services.html
- VS Code Contribution Points: https://code.visualstudio.com/api/references/contribution-points
- VS Code Activation Events: https://code.visualstudio.com/api/references/activation-events
- VS Code Custom Editors: https://code.visualstudio.com/api/extension-guides/custom-editors
- VS Code Webview UX Guidelines: https://code.visualstudio.com/api/ux-guidelines/webviews
