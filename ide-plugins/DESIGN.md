# IDE 插件功能设计

## 目标

为 Ouroboros 提供统一的 IDE 插件能力：IDEA 与 VS Code 共享元数据编辑器 schema、AMIS 表单运行时和未来平台绑定合同，同时保留各 IDE 的原生编辑器、调试、命令和配置体验。

## 非目标

- 不新增第二套运行时元数据加载通道；源代码目录下的元数据仍通过构建打包到 `META-INF/ouroboros/**`。
- 不把 profile ID、container ID 或 devKey 写入可提交文件；profile/container 也不作为手工配置项保存。
- 不在 IDEA 与 VS Code 中分别维护一套元数据 schema。
- 普通 JSON 元数据使用基础 AMIS form；`business` 使用默认业务模型编辑器 schema 的离线抽取结果；`ui-schema` 是页面 schema，本期直接使用官方 `amis-editor` 图形化编辑器。
- 本期不实现与开发服务器的元数据同步、常驻缓存、线上资源浏览或远程管理；但插件架构必须为这些能力预留公共 module 和 IDE adapter seam。

## 功能设计

### 元数据编辑器

一期支持 JSON 格式元数据文件。普通元数据打开后以基础 AMIS 表单方式编辑；`business` 打开后使用默认业务模型编辑器 schema 组装出的完整 AMIS form；`ui-schema` 打开后使用完整 AMIS Editor。

完整类型与编辑器注册以 [metadata-types](common/metadata-editor-schema/metadata-types/) 和 [metadata-editors](common/metadata-editor-schema/metadata-editors/) 为准。下表是代表类型，新增类型在共同资源包维护。

| 类型 | 新规范文件 | 旧规范路径 | 表单标题 |
|---|---|---|---|
| `authority` | `*.authority.json` | `resources/META-INF/ouroboros/authority/**/*.json` | `权限项模型` |
| `menu` | `*.menu.json` | `resources/META-INF/ouroboros/menu-model/**/*.json` | `菜单模型` |
| `dev-menu` | `*.dev-menu.json` | `resources/META-INF/ouroboros/dev-menu-model/**/*.json` | `开发菜单模型` |
| `ui-model` | `*.ui-model.json` | `resources/META-INF/ouroboros/ui-model/**/*.json` | `UI 模型` |
| `ui-schema` | `*.ui-schema.json` | `resources/META-INF/ouroboros/ui-schema/**/*.json` | `UI Schema` |
| `business` | `*.business.json` | `metadata/**/*.business.json` | `业务模型` |
| `app-module` | `app-modules.json` | `resources/META-INF/ouroboros/app-modules.json` | `应用模块` |
| `configuration` | `configurations.json` | `resources/META-INF/ouroboros/configurations.json` | `配置项` |
| `configuration-group` | `configuration-groups.json` | `resources/META-INF/ouroboros/configuration-groups.json` | `配置分组` |
| `webapi` | `*.webapi.json` | `metadata/**/*.webapi.json` | `WebAPI 模型` |
| `position` | `*.position.json` | `metadata/**/*.position.json` | `岗位定义` |
| `configuration-item` | `*.config.json` | `configuration/**/*.config.json` | `配置项` |
| `configuration-group-item` | `*.config-group.json` | `configuration-group/**/*.config-group.json` | `配置分组` |

行为：

- 不同 metadata 类型注册为不同 IDE 编辑器，而不是进入一个大编辑器后再内部路由。
- 编辑器只显示表单主体；不显示左侧 JSON 视图、顶部面包屑路径、文件路径标题或额外模型名。
- AMIS form title 使用中文模型名。
- 表单不显示提交按钮；字段变更后通过 host bridge 自动写回文件。
- JSON 解析失败或 schema 校验失败时展示诊断，并保留原始文本，不用空对象或 fallback 值覆盖文件。
- revision 冲突时加载最新文档，将本地表单变更重放到新 revision 后自动重试，避免编辑器停留在无法继续保存的状态。
- 新规范源文件建议放在 `src/main/metadata/**`，构建时通过 Maven resources 打包到 `META-INF/ouroboros/**`。
- 旧规范 `resources/META-INF/ouroboros/**` 继续可编辑，保证迁移期兼容。
- `app-module`、`configuration` 和 `configuration-group` 的旧规范文件是 JSON 数组，表单通过 AMIS `combo` 编辑数组条目并自动保存。
- `configuration-item` 和 `configuration-group-item` 对应工作区文件 provider 的单文件单对象格式，分别匹配 `.config.json` 与 `.config-group.json`。
- `business` 的 IDE 编辑器 schema 从默认业务模型编辑器生成器导出，作为 `common/metadata-editor-schema/editor-schemas/business-default.json` 静态资源进入 manifest，避免 IDE 离线编辑依赖开发服务器 `/api/amis-schema` 或运行时 SPI。
- 新规范 `ui-schema` 使用统一包裹格式，`name` 默认按低代码约定使用点分格式，例如 `Runtime.Page`；可视化编辑器顶部以可收缩区域编辑包裹字段 `name/title/description`，下方使用 AMIS Editor 编辑内部 `schema`。旧规范裸 UI Schema 仍直接进入 AMIS Editor。

### 元数据文件模板

IDE 插件提供内置元数据文件模板，用于从 IDE 原生入口创建新规范 JSON 文件。

- 公共 schema 包在 `common/metadata-editor-schema/metadata-types/*.json` 中声明每类元数据的 `fileTemplate.defaultFileName`、创建时需要填写的关键字段，以及初始 JSON 文档；在 `metadata-editors/*.json` 中声明编辑器名、文件匹配模式、schemaName 和各 IDE 注册 ID。
- 公共层通过 creation module 统一生成默认路径、校验结果、模板字段默认值和最终 JSON 文本；IDEA 与 VS Code 只是不同入口 adapter。
- IDEA 在 `New > 创建 TideStack 模型` 下提供已注册元数据入口，使用单个表单同时填写文件路径和模板字段，创建后直接打开对应文件。
- VS Code 在命令面板、`New File...` 和资源管理器目录右键菜单提供创建入口；`New File...` 与目录右键入口收纳到 `创建 TideStack 模型` 子菜单，二级菜单只展示具体元数据类型入口。
- 创建流程先确认文件路径，再按类型询问 `name/fullName/path`、`title`、`description` 等模板字段；字段值通过公共 serializer 写入 JSON 模板。
- 后缀类型（包括下列示例）在命令面板无目录上下文时默认写入 `src/main/metadata/**/*.authority.json` / `.menu.json` / `.dev-menu.json` / `.ui-model.json` / `.ui-schema.json` / `.business.json` / `.webapi.json` / `.position.json` / `.config.json` / `.config-group.json`；从 IDE 目录右键触发时默认使用选中的目录。固定集合类型只创建 `src/main/metadata/app-modules.json`、`configurations.json`、`configuration-groups.json`。
- 模板只负责作者态文件创建；运行时发现仍通过 Maven resources 打包后的 Provider + Center 链路完成。

### TideStack Develop Config

插件提供统一的项目开发配置入口，当前服务于部署与远程调试，后续也承载高低开融合、项目模板、平台同步等能力。

配置分两层：

| 层次 | 文件 | 是否提交 | 字段 |
|---|---|---|---|
| 项目共享配置 | `.tidestack/develop.json` | 是 | `schemaVersion`、`developmentServerUrl`、`appName`，可选 `editorServiceUrl` |
| 本地私有配置 | `.tidestack/develop.local.json` | 否 | `schemaVersion`、`devKey`，可选 `editorServiceUrl`、`editorAccessToken` |

行为：

- IDEA 在项目设置页提供 `TideStack Develop Config`，读写上述两份项目文件。
- VS Code 在命令面板提供 `TideStack Develop Config: Configure Project` 与 `Open Config Files`，读写同一组文件。
- `.tidestack/develop.local.json` 加入 `.gitignore`，保存本机 devKey 和可选的编辑服务地址、凭据覆盖。
- `profileId` 和 `containerId` 不手填、不落盘；IDE 在运行时用 devKey 调用平台解析当前 profile，再按 `appName + profile` 解析容器。
- 历史导入文件如仍包含 `platformUrl` 或 `debugKey`，插件导入时分别迁移到 `developmentServerUrl` 与 `devKey`。
- 历史本地文件如仍包含 `profileId` 或 `containerId`，插件规范化保存时会移除这些派生字段。
- 后端授权 API 使用 `POST /api/dev/{appName}/authorize` 与请求字段 `devKey`；插件不再适配 profile 前缀的历史接口。

### Metadata Workspace（规划能力）

Metadata Workspace 是后续支撑开发服务器元数据同步、线上资源浏览和表单数据源合并的公共 module。它不替代当前文件编辑器，而是在本地文件和开发服务器之间提供一个统一的元数据查询与合并视图。

规划行为：

- 本地元数据、开发服务器元数据和后续派生来源都以 metadata source adapter 进入 Workspace。
- Workspace 维护常驻内存索引或缓存，按 metadata type、name、path、引用关系和 source priority 查询。
- 表单中的数据源、引用选择器和校验逻辑后续只消费 Workspace 提供的 merged view，不直接枚举某个目录或直接调用开发服务器。
- 本地与线上元数据的冲突、优先级和 freshness 状态由 Workspace 给出；编辑器 UI 只展示状态和执行用户操作。
- 开发服务器上的模型和资源浏览优先尝试与 IDE 原生文件视图融合；如果 IDE 不支持稳定融合，则用 VS Code Tree View、IntelliJ Tool Window 等边栏/工具窗口实现。
- 远程管理能力独立于浏览能力，通过平台 capability 开启；未声明支持时只读展示，不提供创建、删除或发布类操作。

这个规划只定义 future seam，不要求当前实现缓存或同步。后续落地时应先补公共 source/priority/catalog 合同和 case corpus，再分别实现 VS Code 与 IntelliJ 的 UI adapter。

### 编辑器同步与后端能力接入

#### 当前编辑器来源

IDE 编辑器沿用平台源码与注册机制，两款 IDE 消费同一套公共资产。

| 编辑器或资源 | 正式来源 | 本地使用边界 |
|---|---|---|
| UI Schema 可视化编辑器 | `main-app/src/components/amis/editorRuntimePreset`，由 IDE 的 `vite.amis-editor.config.ts` 构建 | 共用平台 renderer、编辑插件和脚本语言适配器；平台路由、菜单等由现有 IDE shim 隔离 |
| 逻辑流与审批流画布 | `ouroboros-logicflow-editor`、`ouroboros-approval-workflow-designer` | `build-editor-bundles.mjs` 从源码构建；节点定义和候选人数据另行提供 |
| 默认业务模型表单 | `DefaultBusinessModelEditorSchemaGenerator` 和 `META-INF/editor-schema-templates/**` | 生成后写入 `editor-schemas/business-default.json`；schema build 负责字段类型 URL 等 IDE 适配 |
| 内置逻辑流节点、业务字段类型 | 后端 `src/main/metadata/**/*.flow-node.json`、`*.field-type.json` | schema build 扫描生成离线快照；不能代表某个应用实际安装的扩展 |
| 其他元数据表单 | `metadata-types/*.json` | 当前为 IDE 维护的基础表单；不等同于完整平台管理页面 |

AMIS 6.13 的 `FormPlugin.patchSchema` 会依据 `api/initApi` 推断表单用途并补写 `feat`。Ouroboros 通过正式插件注册替换原生 Form 插件，在 `OuroborosBaseFormPlugin` 中移除这次自动推断的字段，平台的 `OuroborosFormPlugin` 继承同一修复。原生和平台表单均保留已有显式值、父类的按钮处理及各自的编辑面板。修复同时进入平台和 IDE 的共用编辑器，不在文件保存层全局删除 `feat`，也不批量迁移历史文件。

Page 属性面板的 `asideResizor` 和 `pullRefresh.disabled` 也有会写回 schema 的控件初值。`OuroborosPagePlugin` 通过正式插件注册替换上游 Page 插件，把控件初值改为只影响显示的 `pipeIn`，并关闭 `pullRefresh` combo 的 `syncDefaultValue`，避免生成空对象。显式属性继续保留，实际编辑仍按上游机制保存。

AMIS 的旧式 Action 弹窗入口未处理 `onDefinitionsChange`，确认子编辑器时会将包含内联弹窗的临时目录整体写入根 `definitions`，序列化并重新打开后继续追加副本。`OuroborosActionPlugin` 通过正式插件注册替换 Action 插件，让工具栏和旧式 `dialog/drawer/feedback` 属性控件使用原有 `store.updateModal/mergeDefinitions` 生命周期，只合并实际引用的定义，并保留已有弹窗身份及共享 `$ref`。主动复用仍会生成共享定义；历史定义和无引用的自定义定义保持原样，避免误删合法内容。

同步的验收包括真实浏览器加载产物、表单和页面仅打开时无有效文档变更、正常编辑仍可保存，以及两款 IDE 的资产校验。仅通过 Vite 构建不足以证明可运行：平台源码和 IDE AMIS 依赖必须共用 React、MobX 等实例，避免 observer 继承时发生重复运行时冲突。

#### 服务连接现状

两款 IDE 已通过宿主 bridge 接通在线编辑请求。开发平台默认使用 MQ；独立本地编辑服务使用 socket，两者复用同一套 dev-helper 命令和代码智能服务。

integration fetcher、AMIS HTTP shim 和 UI Schema 编辑器的 `amisEnv` 已接入同一宿主 bridge。配置在线目标时，编辑 API 使用真实请求和错误；没有在线目标时使用随包内置快照。鉴权失败、服务不可达和不支持的请求不会转成空成功结果。

现有可复用服务如下：

| 能力 | 已有接口或扩展点 | 必须保留的语义 |
|---|---|---|
| 脚本与表达式上下文 | `POST /api/dev/{appName}/code-intelligence/context`，`CodeContextProvider` | `codeSite`、资源类型/名称、节点名或 JSON pointer、完整未保存草稿 |
| 语言服务会话 | `/api/dev/{appName}/code-intelligence/sessions` 的创建、notifications、messages、context、DELETE | `documentId/documentVersion/draftRevision`、会话所有者、关闭和过期响应丢弃 |
| 应用元数据快照 | `ApplicationMetadataSnapshotProvider` | 当前/过期/不可用状态、版本与运行实例；同一应用不能同时选中多个权威快照来源 |
| 应用语言环境 | `CodeEnvironmentResolver`、dev-helper `codeEnvironment` 命令 | 实际应用的 Java 版本、classpath、制品校验和运行实例，不用平台自己的依赖冒充 |
| 逻辑流节点 | `GET /api/dev/{appName}/logicflow/node-define`，编辑器 `nodeDefineApi` | 节点定义、属性 schema 与编辑能力来自同一目标环境 |
| 业务字段类型 | `/api/app/{appName}/field-types/**` | 主键/普通类型与参数编辑 schema 使用同一目录版本 |
| 审批流引用 | `/api/dev/{appName}/approval-workflow/candidate/**` | 候选人来源、候选值、分类和表单字段均需应用上下文 |

语言会话用 HTTP session ID 识别所有者，每个编辑器宿主客户端保持独立 Cookie 并在关闭时释放会话。配置 devKey 后，宿主自动发送 `X-TideStack-Dev-Key`，无需第二份平台登录令牌。平台的 `DevKeyEditorAuthenticationFilterSupplier` 通过已有 `AuthenticationFilterSupplier` 扩展验证应用绑定、有效期和真实账号，再继续原授权链。该凭据仅允许编辑 API，不开放部署或任意业务 API；每次请求重新校验，密钥撤销或账号禁用立即生效。Cookie 只维持语言会话所有权，不缓存开发授权。显式 `editorAccessToken` 作为可选 Bearer 覆盖，优先使用且不与 devKey 同时发送。

#### 统一服务目标

公共 runtime 定义编辑服务连接和允许的请求合同，由 IDE adapter 提供网络传输、凭据和生命周期。编辑器使用统一接口，服务目标由宿主解析。

| 开发方式 | 目标的选择方式 | 后端能力来自哪里 |
|---|---|---|
| 开发服务器管理应用 | 使用 Develop Config 的 `developmentServerUrl/appName` 和已有私有 `devKey`；部署的 profile/container 沿原流程解析 | 开发平台的编辑服务，应用快照与 classpath 经现有 dev-helper 链路获取 |
| 本地 Spring Boot 嵌入基座 | 配置本地 `editorServiceUrl` 和 helper socket 地址；服务共享密钥与 devKey 一致，或显式配置 Bearer 覆盖 | 编辑服务复用 dev-helper 命令和 collector，通过 socket 连接应用 |
| 从测试代码启动应用 | 与本地 Spring Boot 使用同一合同；按实际运行进程连接，不根据 `src/test` 推断能力 | 与 main 启动相同的 helper 能力；无需再写测试专用 collector |
| 本地容器 | 使用关联开发平台的 MQ，或端口映射后的 socket 连接；不能假定宿主 Unix socket 天然可用 | 容器内同一套 helper；元数据、classpath 和实例身份始终属于目标应用 |
| 没有运行服务 | 使用离线模式 | 随包内置定义、本地文件，以及后续可选的显式导出快照；不提供声称基于应用环境的检查结果 |

连接按 workspace folder 或模块绑定。一个 monorepo 中不同模块可连接不同目标；同一编辑会话必须固定应用和环境，禁止把 A 环境的节点目录与 B 环境的代码上下文混用。Develop Config 公共合同声明可选 `editorServiceUrl` 和私有 `editorAccessToken`；两款 IDE 从文件向上寻找最近的配置目录，并以该目录下的私有文件覆盖服务地址。未配置专用地址时使用 developmentServerUrl。修改配置后重新打开编辑器。共享文件只保存可提交的连接描述，本机覆盖和凭据放私有文件。实际端口等个人差异优先放本机覆盖。

当前通过 `/code-intelligence/environment` 读取同一目标的快照状态和环境身份；语言会话、目录等接口按实际响应处理，401/403、404 和服务不可达不会伪装为空目录成功。完整的多能力 discovery API 尚未定义。

Webview 的 AMIS fetcher、嵌套脚本编辑器和逻辑流属性面板共用宿主 bridge。公共层把已有 API 请求解析为允许的能力操作，宿主按所选目标转发，保持原请求体及版本字段。不得提供任意 URL 代理；schema 中的外部 API、平台部署/管理操作也不能自动获得编辑服务权限。凭据留在宿主，跨域由宿主处理，切换目标时再按目标隔离会话。未保存草稿只发给明确选择的服务，服务地址在 Develop Config 中配置，失败信息通过请求结果返回。

前端继续使用 `script-editor-intelligence/languagePlugins.ts` 的语言注册和 `lsp/session.ts` 的会话实现；通过 fetcher 和环境变更订阅适配宿主，不复制补全器、语法检查器或节点 Registry。IDE 的环境订阅每 5 秒经宿主查询同一目标的 environment 接口；身份或可用性改变会调用现有语言 bridge 的失效回调。它不声称建立了 STOMP 连接。

#### 复用 dev-helper，通过通信接口适配本地

通信设计以 [dev-helper 设计](../dev-module/ouroboros-dev-helper/DESIGN.md#4-通信与同步机制)为准。core 定义 `DevHelperClientTransport` 和 `DevHelperServerTransport`；现有平台调用服务与应用 dispatcher 依赖这两个接口。默认 MQ 适配器直接使用原 MQBridge、绑定和消息协议，留在原模块中，不先拆 MQ。自定义通信 Bean 可替换默认实现；`ouroboros.dev-helper.transport` 缺省为 `mq`，其他名称必须安装对应实现，不能仅改配置就获得 IPC。

本地方案复用现有 Command、MetadataCollector、快照协议和 `codeEnvironment`，不再分别建设 `LocalMetadataSnapshotProvider` 或本地 classpath 采集路径。`RemoteCodeEnvironments` 的依赖本来就是 `DevHelperCommandService`，因此可继续承担代码环境下载和校验，只替换其下游通信。流程图、类图、时序图及装配规则集中放在 helper 设计中。

本地 IPC 已实现为带令牌握手的 TCP 长连接。`socket` 配置用于直接运行的 Spring Boot、测试进程及容器；`test-app` 仍控制 helper 原有功能，不用于选择传输。

无开发平台时启动 `ouroboros-dev-editor-service`。该服务显式装配现有 code-intelligence、领域 CodeContextProvider 和 HelperMetadataProvider，通过 socket 取得应用环境。它不会扫描并启动整个平台，也不让业务应用依赖开发平台的语言服务模块。启动与配置示例见[本地服务指南](../dev-module/ouroboros-dev-editor-service/README.md)。

开发编辑服务沿用认证、session 和应用范围授权；传输实现继续保留 helper 身份校验。容器内路径不能直接当宿主文件路径，仍使用现有制品导出和校验流程。语言服务自身的 stdio/container 部署配置，与 helper 的消息传输是两个独立配置。

#### 动态定义、离线模式与生命周期

逻辑流节点通过 `editorCatalog` 从正式 Provider 导出，平台和 helper 共用 `LogicFlowNodeCatalog`；字段类型使用应用运行态 `FieldTypeCatalog`；审批候选人查询应用的 `CandidateProvider`。代码上下文和目录由同一宿主客户端固定到同一服务和应用。`readMetadata` 沿既有快照发布路径读取运行态元数据，不调用会重建上下文的 `refreshMetadata`。尚未运行的本地扩展不会被声称为已加载能力。

- 离线内置快照只覆盖当前仓库自带定义，不能推导业务项目的 Java SPI、动态节点、权限或自定义字段类型。未知节点及属性应保留原文并可回退编辑，不自动删除或替换为默认节点。
- 目标不可达时保留本地编辑能力，提示哪些补全/检查不可用。已经缓存的数据标明来源、版本及过期状态，不能把离线空列表当成真实业务目录。
- 缓存键包含目标、应用、运行实例、环境版本和目录 revision；条目数量不能代替内容版本。不能把本地与远端的 classpath 或目录拼接成一个无来源的结果。
- 切换目标、后端重启或环境变更时，取消请求、关闭旧语言会话、失效相关缓存并重新发现能力。编辑器关闭时解除订阅；过期响应按文档及环境版本丢弃。
- 逻辑流调试等额外功能也按目标能力启用；同步了画布代码不意味着离线获得执行/调试能力。

#### 落地顺序与验收

1. 同步共用编辑器与离线 schema，修复无操作打开造成的 `feat`、页面初值及空 `pullRefresh` 写入，并验证构建产物。
2. helper 先接入通信接口和默认 MQ 适配器（已实现），验证默认协议、自定义实现与生命周期；保持原模块和采集链路。
3. 已接入两款 IDE 的宿主 bridge、devKey 鉴权与可选 Bearer 覆盖、Cookie/session、context、语言会话、目录和环境轮询；本地 socket 与独立编辑服务已装配。
4. 从正式 provider 补齐尚未导出的动态目录；有明确需要时再增加离线快照导出和启动配置发现。

连接阶段至少覆盖：有/无相应能力、旧协议、401/403、真实空目录、断网、目标切换、进程重启、context-path、多模块多应用、两个并行语言会话的 cookie 隔离、未保存草稿、请求乱序、关闭清理、自定义节点/字段类型及未知节点保存。直连与容器验收还需确认服务看到的 classpath 与正在运行的应用一致。

### 部署与远程调试

IDEA 当前已有部署与远程调试入口，流程为：

1. 构建当前项目产物。
2. 读取 TideStack Develop Config。
3. 使用 devKey 向开发平台解析并授权 profile。
4. 上传扩展 jar。
5. 通过 `appName + profile` 解析目标容器并重启。
6. 创建远程调试 session。
7. 创建并启动 IDE Remote JVM Debug 配置。

VS Code 调试能力规划为同等能力，但应复用 TideStack Develop Config 和平台 API DTO。VS Code 侧只实现命令入口、任务执行、进度展示和 debug attach 适配。

### 项目模板

项目模板是规划能力，是否提供图形化模板入口取决于 IDE 支持程度。

| IDE | 设计方向 |
|---|---|
| IDEA | 优先使用 New Project Wizard 或目标 IDEA 平台版本支持的模板扩展 API。 |
| VS Code | 使用命令式 scaffold 流程；VS Code 没有完全等价于 IDEA New Project Wizard 的统一入口。 |

模板描述中与 Ouroboros 领域相关的部分应放在公共层，例如模块类型、默认依赖、元数据样例和构建文件片段。具体文件创建、UI 表单和项目刷新由 IDE 适配层完成。

## 支持矩阵

| 能力 | 公共层 | IDEA | VS Code |
|---|---|---|---|
| 元数据类型/schema 定义 | 已实现 | 消费打包 manifest | 消费 npm 包/vendor copy |
| 元数据表单渲染 | 公共 Web 壳 + runtime | JCEF 容器 + 文本回退 | Webview custom editor |
| 变更即保存 | 已实现 runtime | JCEF JS query save bridge | Webview message bridge |
| 诊断 | 公共 Host 校验 | JCEF 使用公共 Host；文本回退使用 JVM 校验 | diagnostics collection |
| 元数据文件模板 | 已实现 | New 菜单分组 | 命令面板/资源管理器入口 |
| 新建文件入口归一化 | 创建合同 / case corpus / 路径规则 / 模板 serializer | NewGroup adapter | Command Palette / New File / Explorer adapter |
| 多语言 | zh-CN / en-US 共享资源 | `TideStackMessages` adapter | package.nls 生成 + runtime locale |
| TideStack Develop Config | 公共 manifest | 项目设置页 | 命令面板入口 |
| Metadata Workspace | 规划中，source/priority/catalog 合同 | 规划中，Tool Window / VirtualFileSystem 视 IDE 能力选择 | 规划中，Tree View / virtual document 视 IDE 能力选择 |
| 开发服务器元数据同步 | 规划中，Workspace cache + merged view | 规划中 | 规划中 |
| 开发服务器资源浏览/管理 | 规划中，catalog + capability | 规划中，优先融合文件视图，否则 Tool Window | 规划中，优先融合 Explorer，否则 Tree View |
| 能力矩阵 | capability manifest | available/planned 消费 | available/planned 消费 |
| 部署/调试 | 复用 develop config | 已实现命令 | 规划中 |
| 项目模板 | 模板描述规划中 | 规划中 | 规划中 |

## 共享合同

`MetadataTypeContribution` 定义元数据编辑语义：

- `type`
- `fileSuffix`
- `displayName`
- `sourcePathPatterns`
- `packagedPathPrefix`
- `packagedPathPatterns`
- `documentShape`
- `editorKinds`
- `jsonSchema`
- `fileTemplate`
- `createEditorSchema`

大型编辑器 schema 可以通过 `editorSchemaFile` 指向 `common/metadata-editor-schema/editor-schemas/*.json`，构建脚本会在 `metadataTypeContributions.json` 中内联为 `editorSchema`，并复制原始资源目录供调试与审计。

`MetadataIdeFileBridge` 定义宿主文件合同：

- 加载文本与 revision
- 带 revision 冲突保护的保存
- 监听文档变更
- 列出元数据文件
- 打开引用
- 发布 diagnostics

`MetadataEditorDocumentBridge` 是图形编辑主路径需要的最小文件合同：加载文本、带 revision 保存、发布 diagnostics。VS Code 的完整 bridge 扩展该合同；IDEA JCEF 通过浏览器 Host bundle 适配 IntelliJ Document。

`MetadataEditorSession` 定义图形编辑会话的公共 interface：加载 state、保存文本、校验文本，并把 host error 统一转换成 Webview response。VS Code custom editor 和 JetBrains Browser Host 只适配 transport、主题、flush 和 IDE 生命周期。

`common/contracts/tidestack-develop-config.json` 定义 TideStack Develop Config 的文件路径、字段顺序、schema 版本和历史字段别名。IDEA 与 VS Code 分别实现原生读写，但不再各自维护合同常量。

`common/metadata-editor-runtime/src/usecases/developConfig.ts` 定义 TideStack Develop Config 的纯语义：legacy alias 读取、字段排序、removed field 清理和 shared/local 文档生成。IDE adapter 只提供工作区、文件和表单交互。

`common/metadata-editor-schema/metadataCreationContract.json` 与 `metadataCreationCases.json` 定义新建元数据文件的路径合同、路径规则、模板默认值规则和跨 IDE case corpus。TS runtime 与 Kotlin 测试共用这份语料；IDE adapter 不再单独定义固定集合文件映射，也不单独硬编码 `.json`、`metadata.json`、`src/main` 或默认 `example` 语义。

`common/metadata-editor-schema/metadataEditorAssets.json` 定义 Web 编辑器打包资产清单。VS Code copy/verify 脚本与 IDEA Gradle 资源校验消费同一清单，避免 Web 壳、AMIS editor runtime、schema manifest 或 Develop Config contract 漏包。

`common/metadata-editor-schema/i18n` 定义中英文语言资源。metadata schema、创建文件提示、设置页、命令标题、Deploy & Debug 通知和能力描述都应从这里取文案。

`common/metadata-editor-schema/tidestackDevelopCapabilities.json` 定义 IDEA 与 VS Code 的能力状态。新增能力时先更新该 manifest，再决定各 IDE adapter 是否实现入口。

未来 binding 与 template 合同沿用同一原则：公共层定义 schema、校验和领域语义；IDE 适配层实现生命周期、UI、文件写入和持久化。

未来 Metadata Workspace 合同也沿用同一原则：公共层定义 metadata source、identity、priority、freshness、catalog 和 merged view；IDE 适配层实现本地文件监听、开发服务器请求、缓存持久化位置、资源树展示和用户操作入口。

## 验收检查

```bash
# 从仓库根目录执行；先按各包锁文件准备依赖
npm --prefix ide-plugins/common/metadata-editor-schema run build
npm --prefix ide-plugins/common/metadata-editor-runtime run build
npm --prefix ide-plugins/common/metadata-editor-runtime test
# 首次运行浏览器回归前，在 runtime 目录执行 npx playwright install chromium
npm --prefix ide-plugins/common/metadata-editor-runtime run test:browser
npm --prefix ide-plugins/vscode-plugin run compile
(cd ide-plugins/intellij-plugin && ./gradlew test)
(cd ide-plugins/intellij-plugin && ./gradlew buildPlugin)
```

这些检查用于确认两类 IDE 插件仍能消费同一套 Web 壳、Host、AMIS runtime、schema、元数据类型贡献、创建合同、资产 manifest 和 Develop Config 合同。
