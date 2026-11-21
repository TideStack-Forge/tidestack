# 正式语言链路验收

从指定 worktree 根目录运行。正式构建与配置入口见 [语言服务模块](../../../../../dev-module/ouroboros-dev-groovy-language-server/README.md)，以下顺序便于定位失败段落。Java 21、Maven、Python 3、Node/npm 为前置依赖；真实链路还需本机开发 RabbitMQ。所有启动进程均属于本场景，停止时只关闭本场景 PID。

```bash
mvn -B -q -pl dev-module/ouroboros-dev-groovy-language-server -am package -DskipTests
python3 testing/integration/feature/development/groovy-language-service/scripts/product_probe.py
python3 testing/integration/feature/development/groovy-language-service/scripts/full_chain.py --prepare --spring.application.name=example
```

server 在 127.0.0.1:43184 运行，另一个终端执行 `chain_probe.py`。浏览器在 frontends/web/main-app 执行 `CODE_CONTEXT_BACKEND=groovy npx playwright test --config=playwright.code-intelligence.config.js`，它会启动端口 43183 的真实前端；无复用服务时自动 prepare/start 后端。

首次 product_probe 依赖真实 Java 夹具及上下文样例；缺少时先执行下文的 community_probe.py --check（准备 Java 夹具），再执行 platform_probe.py（通过正式领域服务生成上下文）。正式编译器构建不依赖这些测试样例。

证据分别归 `tmp/testing-evidence/groovy-language-service/product/run-*`、`chain/run-*` 及 `frontends/web/main-app/target/playwright-groovy-lsp`。前两者有 results.json 和原始交换；浏览器每个用例包含 video.webm、截图、trace.zip（包含协议附件）。录屏使用表单与应用工具 Binding 夹具，实际 code-intelligence 网络响应未模拟。浏览器可能记录既有 AMIS findDOMNode 警告和 harness 不提供的 STOMP 路由 404；pageerror 与 code-intelligence 请求单独断言。

共享插件测试命令：

```bash
mvn -B -q -pl dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime,core/ouroboros-logicflow/ouroboros-logicflow-dev -am test -Dtest=ApplicationCodeEnvironmentTest,RemoteCodeEnvironmentsTest,HelperCommandDispatcherGenerationTest,JavascriptEditorEnvironmentTest,LanguageServerRuntimeTest,EditorLanguageServicesTest,EditorLanguageSessionsTest,LogicFlowCodeContextProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Dgroovy.language.distribution="$PWD/dev-module/ouroboros-dev-groovy-language-server/target/distribution"
```

## 历史研究复跑

以下保持原型调查的可追溯性，不能作为当前正式产品链路的入口。


从指定 worktree 根目录运行。需要 Java 21、Maven、Python 3，以及 Maven Central、GitHub API 和 Groovy JFrog 下载访问。所有第三方源码、依赖装配、workspace 与证据留在本 worktree 的 tmp；不修改产品依赖和脚本执行行为。

## 1. 社区 LSP 基线

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/community_probe.py --check
python3 testing/integration/feature/development/groovy-language-service/scripts/community_probe.py
```

预检检查 Java、Maven、固定源码、目标 Groovy JAR 与 classpath 夹具；首次执行准备受控依赖并构建 Java 夹具。结果归 tmp/testing-evidence/groovy-language-service/community/，含真实 stdio JSON-RPC、诊断通知、stderr、逐 case 结果。runner 的零退出仅表示实验完成；读取 results.json 中的 fail/gated/not-covered，不能当作完整能力通过。

## 2. 准备实验 GE train

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/prepare_headless.py
python3 testing/integration/feature/development/groovy-language-service/scripts/prepare_headless.py --check
```

headless-assets.json 锁定官方 GE 6.0/e4.38 二进制 SHA-256、4.0.30 源码 commit 和 Git blob 摘要、Maven Central 4.0.30 JAR 摘要。下载先验证摘要再入缓存；使用本地 javac 编译 7 个变化源文件，移除目标源码树删除的覆盖类，复用未变 parser2/trace 和其余覆盖类。它是明确标注的实验 train，不是官方发布物；Maven Central 的基础 JAR 不声称与 Git 仓库的分发 JAR 字节相同。

编译产物和 assembly.json 存入 tmp/groovy-semantic-prototype/ge430-assembled/；编译日志归 evidence/headless/compiler-build.log。校验缺失或摘要不符时停止，不能拿旧的 4.0.29 结果填充 4.0.30 验收。

## 3. 平台上下文

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/platform_probe.py
```

运行现有 LogicFlowCodeContextProviderTest，读取本轮 Surefire 提供的实际 classpath，再编译并运行 ContextSnapshotProbe。它使用真实 CodeContextService、FlowNodeAnalyzer SPI、JavaShapeIntrospector，通过“校验→映射→Groovy 脚本”生成两份版本不同的上下文。当前应用元数据来源是受控内存夹具，未连接真实 helper。请求中的脚本主体含抛异常语句；不执行它。

断言根形状为 Map、映射及数组元素类型正确、命名端口别名一致、反射上下文保留 Java 类型、上下文版本变化、无领域诊断。证据归 evidence/platform/；这一步不启动平台 HTTP 服务。

## 4. 语义内核和编辑能力分开验收

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/headless_probe.py --check
python3 testing/integration/feature/development/groovy-language-service/scripts/headless_probe.py --gate core
python3 testing/integration/feature/development/groovy-language-service/scripts/headless_probe.py --gate editor
```

每次启动独立 Equinox/JDT workspace，配置当前 Java 21 的 jrt-fs.jar 和真实 Java 夹具。ContextTypeLookup 通过 GE 正式 typeLookup extension 注册；使用同一个 GroovyCompilationUnit 验证上下文切换，变更时通过 getNewModuleInfo 重建 AST。结构 Map 引用在克隆时保留字段，同时维持 java.util.Map 身份及方法。

core 门禁要求 15 个内核/上下文用例通过。editor 门禁额外要求已执行的补全、定位和 GString 方法用例通过；当前预期会暴露候选缺口并非零退出，不能下调通过标准。两种门禁均不能代表未覆盖的格式化、代码操作、语义着色、引用/重命名 API、LSP 传输和 Monaco 已通过。

每轮证据进入 evidence/headless/run-*/：semantics.json 保存实际类型、声明范围、参数签名、诊断、耗时和 JVM heap；results.json 保存门禁、来源和未覆盖项；headless.log 保存框架输出。latest.json 指向最近一轮。进程始终在 finally 中关闭 Equinox；没有常驻服务需要手动停止。内存是观察时 JVM heap，不是 RSS/峰值；单进程样例耗时不能作为产品 SLA 或升级收益。

正式产品入口、真实 helper classpath、并发会话隔离、LSP 传输、前端联动和录屏另行验收。

当前正式部署为独立 helper / platform JVM；helper 只导出实际依赖，LSP 子进程必须属于平台。检查 chain/processes.json、进程树及 workspace/classpath.json，确认助手无语义子进程、扩展 JAR 从平台内容缓存读取。缓存和容量的公共合同见平台 LANGUAGE-PLUGINS.md。


## 专属容器与输出合同验收

先构建正式发行物和镜像，再启动已有完整链路夹具：

```sh
python3 dev-module/ouroboros-dev-groovy-language-server/build/distribution.py
docker build -t ouroboros-language-groovy:script-el-completion dev-module/ouroboros-dev-groovy-language-server/target/distribution
python3 testing/integration/feature/development/groovy-language-service/scripts/full_chain.py --prepare --container-image ouroboros-language-groovy:script-el-completion --spring.application.name=example
```

在 `frontends/web/main-app` 执行专用配置，已有健康后端会复用：

```sh
CODE_CONTEXT_BACKEND=groovy LANGUAGE_CONTAINER_IMAGE=ouroboros-language-groovy:script-el-completion npx playwright test --config=playwright.code-intelligence.config.js
```

独立先跑 DockerLanguageRuntimeIntegrationTest（系统属性 `language.container.image`）、TcpLanguageDeploymentTest（`groovy.language.distribution`）和 GroovyDataflowContractTest，再跑五项浏览器用例。行为测试按模块原 Maven reactor 执行；这几个系统属性缺失时条件用例会 skipped，不应计作验收通过。Kubernetes 用 MockWebServer 验证后端合同，真实集群另行验收。

原始证据位于本工作区 tmp/script-el-integration、tmp/testing-evidence/groovy-language-service 和 main-app/target/playwright-groovy-lsp。浏览器录像、HTTP 交换和独立容器 inspect 都需保留；本地文件应在清理工作区前另存到任务或 CI。完成后终止本次 full_chain 驱动，其 finally 关闭 helper/platform；语言池关闭回收容器。不要停止其他任务或用户容器。
