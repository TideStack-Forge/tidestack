# 后端脚本语言插件接入

语言插件的装配点统一安装在开发平台，复用领域上下文、应用环境制品同步、LSP 进程/文档生命周期和 Monaco providers。助手只提供实际应用环境与运行时目录，不启动语言服务。插件负责语义引擎启动及该引擎对上下文的表示；编辑器插件与脚本运行时插件各有职责，安装编辑能力不会自动增加一种可执行脚本语言。

## 1. 后端接入

实现 `com.ouroboros.dev.codeintelligence.language.LanguageServerProvider`，以扩展包现有 AutoConfiguration 的 `@Bean` 注册，并把 AutoConfiguration 写入 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。平台的 CodeIntelligenceAutoConfiguration 装配唯一 `EditorLanguageServices`，无需再创建注册中心或 Command；此扩展包应安装在开发平台。

| 接口 | 插件职责 |
| --- | --- |
| languageId() | 稳定的 LSP languageId，匹配 `[a-z][a-z0-9-]{0,31}`；重复注册启动失败 |
| engineVersion() | 镜像/发行物的稳定版本，参与池键和 Host 握手，改变引擎时必须改变版本 |
| fileExtension() | 带点的安全文件后缀，如 `.groovy`；共享运行时创建隔离 file URI |
| available() | 服务发行包是否已配置可用；语言目录返回这一状态 |
| command(workspace, environment) | 返回受信任的进程参数列表；读取不可变 LanguageEnvironment 中已校验的本地 classpath、版本与 Java major，准备初始化文件，不接受前端 shell 命令 |
| contextMessages(workspace, context) | 把共享 `ouroboros/context` 消息中的 `params.snapshot` 投影成声明文件或服务专有协议。保留文档版本及 unknown 信息；不能执行用户脚本来获得类型 |
| synchronizationMethod() | 默认无 fence。仅对支持专有同步请求的服务返回方法名；Groovy 返回 `ouroboros/barrier` |

例如扩展包的装配如下；具体 Provider 仍须实现语义引擎及上下文投影：

```java
@AutoConfiguration
public class ExampleLanguageAutoConfiguration {
  @Bean
  LanguageServerProvider exampleLanguageProvider(ExampleLanguageProperties properties) {
    return new ExampleLanguageProvider(properties);
  }
}
```

`EditorLanguageServices` 注入 `List<LanguageServerProvider>`，以 `languageId + engineVersion + environment.version` 复用进程。相同有序依赖内容及 Java major 的应用共享编译器，文档、Binding 和 owner 分别隔离。不同环境不能混用类型。平台全池默认最多 4 个进程；无活跃文档的环境可被回收腾位，全部活跃时新环境明确失败。空闲 10 分钟清理文档及进程，平台退出时终止进程并删除 workspace。

`EditorLanguageSessions` 直接调用平台进程池，所有 LSP 请求都不再经过助手。它只在打开文档时通过 CodeEnvironmentResolver 获取实际应用环境。`RemoteCodeEnvironments` 经既有 DevHelperCommandService / RabbitMQ 调用 `codeEnvironment`；助手返回有序 SHA-256 清单、Java major 和缺失制品的 128 KiB 分块。classpath 目录只将 class 文件确定性打包；Boot 类根展平成普通 JAR 包路径，嵌套 JAR 在扫描资源有效期内复制或导出其类；普通 jar 保留原内容；平台校验大小与摘要后按内容寻址缓存。没有共享文件系统要求，也不把应用类加载进平台 JVM。平台已有 jar 仅在内容摘要完全相同的情况下复用。

新语言继续使用这份通用环境协议。非 JVM 语言所需的专有环境尚无生产实现；若将来需要解释器版本或包元数据，应扩展同一版本化环境合同，并由语言 Provider 消费，不能为每种语言复制一套环境通道、会话或进程池。

## 2. 领域上下文复用

`CodeContextService` 按 resource type 选择领域 `CodeContextProvider`；逻辑流继续调用 `LogicFlowDataflowAnalyzer` 与 `FlowNodeAnalyzer` SPI，输出语言无关的 TypeShape。上游 schema 校验、映射、引用合同及命名端口均在这一步完成。脚本节点语言由节点本身声明，输入传播不识别或硬编码 Groovy。Java 对象通过既有 JavaShapeIntrospector 提供泛型和成员形状。

语言插件把这份 snapshot 转成其类型系统能理解的形式。Groovy 使用 GE typeLookup 的结构 Map 和真实 JDT 类型；未来语言可以生成声明文件、stub 模块或适配自身协议。声明是静态分析输入，不代替运行时对象。脚本输出优先使用显式 outputSchema，否则领域 Provider 通过 FlowTypeContracts 收集 ScriptAnalysisQuery。平台在图分析外调用独立语言服务取得输出合同，再重跑纯图分析。嵌套 callFlow 共用同一合同收集器和结果表；节点 SPI 不发网络请求。

新语言通过 initialize.capabilities.experimental.ouroboros 声明 protocolVersion=1 和 inferOutput=true，处理 ouroboros/inferOutput。平台先发送 didOpen 和 ouroboros/context；inferOutput 参数为 textDocument、documentVersion、draftRevision，结果包含同版本与 shape（TypeShape.schema）。输入环境由连接绑定，Binding 由 context.snapshot.globals 提供。输出合同不执行脚本，也不读取运行时实例；静态分析不能证明的动态行为返回 unknown。

Groovy 使用当前 GroovyCompilationUnit 的带类型 module 及 JDT 应用类型，编辑补全与输出推断共用语义引擎；已经删除平台内 GroovyScriptTypeProvider 与旧 ScriptTypeProvider SPI。JS 输出解析在独立 JVM 使用 Graal Parser，历史 js 标识在输出边界归一为 javascript，编辑提示继续使用浏览器中隔离的 TypeScript Worker。平台只组织图与传播 TypeShape，不解析脚本。一次请求最多 32 波、128 个查询，在 30 秒预算内继续组织分析；单次引擎启动/请求仍受各自时限约束，因此该预算不是整个 HTTP 请求的硬时限。成功输出按环境摘要与源码/输入合同缓存，最多 128 项，失败不永久缓存。

## 3. 前端接入

后端代码字段沿用 `script-editor` 的 `codeSite`、`codePointer`、`codeResourceType`、`language`、`intelligenceApi` 与完整资源草稿。JS/SpEL/JSON/模板/JSONPath/FreeMarker 在语言注册中提供 createAdapter；其他有效 languageId 自动进入共享 LSP adapter，后端必须存在对应插件。

若 Monaco 没有该语言的词法包，可以在前端扩展初始化时调用：

```ts
registerEditorLanguagePlugin({
  languageId: 'example',
  prepare: async ({ monaco }) => {
    monaco.languages.setMonarchTokensProvider('example', exampleGrammar)
  },
})
```

Groovy 用此接口准备 TextMate。没有词法插件时仍能以 Monaco 基础文本模式获得服务端语义功能。所有 completion/hover/signature/navigation/rename/symbol/format/codeAction/semanticTokens provider 都由同一 adapter 注册；是否注册、触发字符及 tokens legend 来自 initialize.capabilities。

### AMIS 表单校验

`script-editor` 默认通过 AMIS 官方异步 `validate()` 将代码诊断接入表单：Error 阻止提交并显示行号及消息，Warning 不阻止。关闭代码校验时在字段配置 `validateCode: false`：

```json
{
  "type": "script-editor",
  "name": "script",
  "language": "groovy",
  "validateCode": false
}
```

该参数只关闭代码诊断的表单阻断；`required` 等已有规则及编辑器提示继续生效。表达式字段使用 `script-editor` 时同样接入。AMIS 注册的 `json-data-editor` 在 JSON 格式/根类型验证通过后，等待 `codeContext` 对应的语法诊断；同样支持顶层 `validateCode: false`。没有 `codeContext` 的普通 JSON 控件仅检查格式/根类型。供组合业务组件直接复用的 `JsonDataEditorControl.validate()` 保留同步合同，这些宿主需要阻断代码错误时应等待其 `validateForSubmit()`。

提交先刷新完整草稿上下文，再调用当前 adapter 的可选 `validate()` 等待诊断，最后只读取当前文档的 Error markers。提供诊断的快照 adapter 应声明 `diagnosticOwner`，以免切换语言前的原生服务晚到结果混入校验；共享 LSP adapter 统一使用 `editor-lsp`。LSP 插件必须发布带当前文档 version 的 `publishDiagnostics`，无错误也要发送空数组。旧版本结果不放行；等待当前版本超时或语言服务失败返回校验错误，不执行用户代码来验证。

Groovy 使用独立服务的语法/语义诊断。后端 JavaScript 复用隔离 ECMAScript Worker，以持久 JavaScript 文档检查语法，再从带上下文声明的 TypeScript 文档取得源码范围内的类型错误；两个虚拟文档持续同步并随 adapter 释放，不加载 DOM/Node 类型。

SpEL、模板、JSONPath、FreeMarker 的基本语法诊断复用同一 context 请求，由开发平台的 `CodeSyntaxDiagnostics` 按领域 provider 返回的 profile/hints 解析。SpEL 调用 `EL.validateSyntax`，FreeMarker 调用 `TextTemplateEngine.validateSyntax`，JSONPath 调用现有 Jayway parser；均不求值、不执行代码、不读取配置值或加载 include。领域扩展仍在 `CodeContextProvider` 描述位置和语义，重型语言仍通过 `LanguageServerProvider` 接入独立服务。

响应在 `languageProjectionHints.syntaxDiagnostics` 声明能力，在 `snapshot.diagnostics` 携带 `source: "ouroboros-syntax"`、LSP 风格 severity/range 与相对当前字段的 JSON `path`；range 为零起始 UTF-16 坐标，定位到解码后的字符串。前端 bridge 将其映射为 `editor-syntax` markers，150ms 合并编辑刷新，丢弃过期响应，提交时等待当前版本；JSON 转义由同一字符串映射处理。领域的 unknown/合同提示不转换成语法 Error。

裸表达式、模板、配置专用、通知 payload 替换和旧映射语义分别遵循现有 `embeddedExpressionMode` / `templateMode` / `embeddedPayloadFields` 等 hints。JSON mapping 只检查可执行的字符串值，跳过 key、非字符串和领域声明的字面数组；`#{key}`、通知 `${key}` 仅检查占位符结构。配置引用参与 JSONPath 时无法静态确定最终路径，仅检查引用结构。语法通过不保证变量存在、类型正确、结果符合业务约束或运行成功。

## 4. 协议和生命周期合同

- 服务器使用 stdio 或内部 TCP 的 Content-Length JSON-RPC framing，静态 LSP capabilities，UTF-16 位置。客户端发送完整文本变更；这也是 Incremental 同步允许的整文档替换形式。
- 平台会话以 app + owner + helperInstanceId 隔离。通用运行时映射公共会话 URI 与带后缀的 workspace file URI。返回的导航和编辑只在当前文档范围内生效；跨文件编辑明确拒绝。
- contextMessages 是必须实现的语言适配，标准 LSP 本身不了解平台上下文。没有专有 fence 的服务可异步 publishDiagnostics；共享 HTTP notifications 轮询收取，前端只应用当前 URI/文档版本。当前诊断合同要求服务发布 version。
- 服务器主动 request 会收到 MethodNotFound；不提供动态能力注册、执行客户端命令、workspace 配置查询、跨文件读取和文件操作。需要这些能力的新引擎须通过插件适配或先扩展明确的公共合同，不能靠绕过边界接入。
- 文档关闭会 didClose；空闲 10 分钟到期，平台关闭时回收进程/维护线程/独立目录。进程退出后旧会话拒绝请求，客户端清理并在后续请求重开；不会假装旧 AST 仍存在。
- 应用扩展包更新以正式 helper 实例和 ClassLoader 更换为环境失效边界；前端收到既有环境通知后销毁旧会话并重新打开。无需让每个语言重新实现应用隔离。

## 5. Groovy 配置与验证

开发平台 Spring 系统配置（助手无需配置语义引擎发行包）：

```properties
ouroboros.code-intelligence.groovy.distribution=/absolute/path/to/distribution
ouroboros.code-intelligence.language.storage=/absolute/path/to/language-storage
ouroboros.code-intelligence.language.max-processes=4
ouroboros.code-intelligence.language.idle-minutes=10
ouroboros.code-intelligence.language.open-timeout-millis=120000
ouroboros.code-intelligence.language.manifest-timeout-millis=90000
ouroboros.code-intelligence.language.max-cache-bytes=1073741824
ouroboros.code-intelligence.groovy.max-heap-mib=768
ouroboros.code-intelligence.groovy.processors=4
```

GroovyLanguageServerProvider 将 LanguageEnvironment 中已物化的实际应用 classpath 写入独立 workspace，运行固定 Groovy 4.0.30 + Java 21 服务。缺少分发包时明确失败，不能静默改用不完整语义实现。

`EditorLanguageServicesTest` 使用两种第二语言协议夹具验证差异化上下文投影、能力声明、进程退出后重开与资源释放；它们不是新增生产语言。`LogicFlowCodeContextProviderTest` 以 JS、Groovy、fixture-language 验证相同上游映射形状。`editor-lsp-session.test.js` 验证 capabilities 装配、文档同步、取消与失败会话重开。真实 Groovy 的完整构建及录屏命令见[语言服务模块](../ouroboros-dev-groovy-language-server/README.md)。

## 6. 环境、缓存与资源边界

环境版本覆盖有序依赖内容与 Java major，不是应用名。采集包含实际父 ClassLoader 的可见依赖；JDK 由 Java major 约束，不复制 jrt 系统模块。助手实例、应用 incarnation 与 runtime generation 仍用于同步目标和会话失效校验；相同内容允许跨助手复用制品与语言进程。Java major 与平台不一致时明确拒绝。单制品最多 256 MiB、最多 4096 项，环境解析缓存最多 128 项；失败可重试，部分下载会清理。默认制品缓存上限 1 GiB，满时明确失败；本版不自动淘汰制品，运维需在服务停止且无引用时清理缓存或调大容量。

Groovy 每进程最大 heap 默认 768 MiB，这是堆上限，不能据此估算含 JDT/native 开销的实际 RSS。容量限制作用于整个平台实例，不能保证多个平台副本总计 4 个。领域推断和协议编排在平台进程，语言解析与语义引擎在独立进程或专属容器；应用反射、实际扩展目录和 GraalJS 固定探针仍由助手采集。探针短暂创建执行器以描述内建对象，不执行用户脚本，不是常驻语义引擎。

后端 JavaScript 的轻量 TypeScript 类型服务在浏览器独立 Worker 中运行，只有 ECMAScript 库。GraalJS 声明由实际执行器采集的 engineGlobals 投影；不加载 DOM/Node 类型，也不改变前端 Monaco defaults。它不经服务器进程池；将来若换成需要远端引擎的 JS 分析器，应接入同一 LanguageServerProvider / LSP 合同。

首次打开包含实际依赖扫描/打包、同步和索引，可能明显慢于编辑请求。环境清单命令单独默认等待 90 秒（manifest-timeout-millis），分块和普通编辑请求仍为 30 秒；大应用应一起设置清单与 open 时限，不能让内部短时限提前截断外层冷启动。open 接口使用单独的可配置异步时限，超时后才创建好的会话会回收；普通 LSP 请求仍保持 30 秒超时。此等待不会创建新的助手解析引擎，也不代表 cold 环境已被预热。

## 7. TCP 启动合同

`LanguageServerDeployment` 是部署扩展点，返回 `LanguageServerConnection` 与服务端 documentRoot。stdio 与 TCP 连接复用同一消息分帧、请求时限和关闭处理；语言 Provider 不操作 Docker/Kubernetes。普通编辑能力仍使用 LSP，环境准备通过同一 JSON-RPC 连接完成：

| 方法 | 输入 | 结果 |
| --- | --- | --- |
| ouroboros/prepareEnvironment | protocolVersion=1、language、engineVersion、environmentVersion、javaMajor、有序 artifacts[{sha256,size}] | missing 摘要清单 |
| ouroboros/putArtifact | 已声明 sha256、offset、最多 128 KiB 的 Base64 data | 校验偏移/大小，最后校验 SHA-256 后发布制品 |
| ouroboros/start | 空参数 | documentRoot；之后该连接承载引擎 LSP |

公共 `LanguageServiceHost` 由镜像自己的 engine.json 指定启动命令，客户端不能发送可执行命令。工作区与依赖路径由服务端物化；平台路径不跨机器传递。每个 Host 同时只有一个环境，环境总制品最多 1 GiB、最多 4096 项、每项最多 256 MiB；下一环境清理未引用制品。TCP 无跨信任域认证或 TLS，本版只用于同机 loopback 或受控集群内部网络，不能直接暴露给外部客户端。

Groovy 发行包同时包含 host.jar、engine.json 和 Dockerfile；Linux 仅解析符合 Eclipse-PlatformFilter 的扩展，Mac 专用 bundle 不阻断 Linux 引擎。

## 8. 通过原运行机制托管容器

默认 deployment=stdio，解析器在平台启动的独立 JVM 内运行。容器模式使用同一语言池，部署实现调用原 DevRuntimeService，再由正式 RuntimeBackend 创建专属语言工作负载。助手只同步环境；平台 JVM 不加载 Groovy 编译器或应用 Class。

```yaml
ouroboros:
  code-intelligence:
    language:
      deployment: container
      backend: DOCKER # 集群内平台可改为 KUBERNETES
      images:
        groovy: ouroboros-language-groovy:script-el-completion
      memory-bytes: 1073741824
      nano-cpus: 4000000000
      max-processes: 4
      idle-minutes: 10
```

镜像由 Groovy 模块的 `build/distribution.py` 生成构建上下文，再用 Dockerfile 构建；标签应替换为实际发布的镜像版本。平台须安装对应的原 dev-runtime-docker 或 dev-runtime-kubernetes 扩展及其正式配置。语言 Provider 仍由原 AutoConfiguration 注册；部署只选择镜像和运行后端，不另做语言注册。

容器模式只提供明确配置镜像的语言，不会静默退回本机进程。目前提供 Groovy 镜像；JavaScript 的独立输出服务有 stdio 实现，尚无生产容器镜像。JS 浏览器 ECMAScript 编辑 Worker 不受这个部署开关影响。

RuntimeKind.LANGUAGE_SERVICE 使用独立 workloadId，WorkspaceMode.NONE 不创建应用工作区/PVC。Docker 仅把 8099 绑定到 loopback 的随机端口，沿 ContainerService 设置 CPU/内存及 AutoRemove；Docker daemon 必须与平台处于可访问该 loopback 的同机网络，远端 daemon 场景未支持。Kubernetes 使用独立 Deployment 与 ClusterIP，不创建公网 Ingress；平台须能访问集群 Service DNS。两者都不挂载平台或应用目录，依赖经版本化制品协议交付。

池按语言、引擎版本与应用环境摘要复用，文档单独隔离。连接关闭调用原 runtime delete；启动失败沿同一路径回滚，保留清理异常。Docker 的 leased Host 在断开后退出，未首次连接 60 秒或连接闲置 15 分钟也会退出；AutoRemove 回收容器。Kubernetes 使用原租约清理器，固定一小时租约到期会清理工作负载；本版没有续租，长时间编辑会中断，后续请求按原会话恢复机制重开。平台正常退出和池空闲回收主动关闭连接及 runtime。

真实 Docker 验收覆盖正式 RuntimeService → Backend → ContainerService → Host → Groovy → 可见 Monaco 提示与输出合同；Kubernetes 只完成模拟 API 的生命周期、资源与过期租约验证，未在真实集群验收。资源限制是配置上限，不能把 heap 上限当作 RSS 或生产性能结论。
