# Groovy 4 语言服务

本模块构建后端脚本编辑器的独立 stdio LSP 分发包，复用 Groovy-Eclipse/GE 6.0 与 e4.38 JDT，固定 Groovy 4.0.30 和 Java 21。应用的脚本执行引擎及 compile() 行为保持原状；patched JDT 只加载在隔离进程内。

## 构建

在 worktree 根目录运行，需要 Java 21、Maven、Python 3，并可访问 Maven Central、Groovy JFrog 和 `raw.githubusercontent.com`。源码直链固定到 `build/assets.json` 中的 commit，并按 Git blob 摘要校验；构建不依赖 GitHub REST API 配额或本机 `gh` 登录。

```bash
mvn -B -q -pl dev-module/ouroboros-dev-groovy-language-server -am package -DskipTests
```

下载器的离线行为测试接入 Maven `test` 阶段，覆盖 API 限流时的源码下载、缓存复用及摘要不匹配拒绝。单独运行：

```bash
mvn -B -pl dev-module/ouroboros-dev-groovy-language-server test
```

输出 `target/distribution` 和 `target/ouroboros-dev-groovy-language-server.zip`，后者作为 Maven `distribution` classifier 附件。`build/assets.json` 锁定二进制和官方源码来源；完整编译 77 个 compiler override 与 4 个 trace 源文件，既有实验差异 class 不参与正式构建。`target/distribution/provenance.json` 记录来源摘要、实际编译数量和分发内容，缓存位于本模块 target/cache。此分发包是项目从固定官方源码构建的装配，不声称为上游官方 Groovy 发行版。

stdio 模式在开发平台配置 `ouroboros.code-intelligence.groovy.distribution` 指向解压目录；进程命令由正式 GroovyLanguageServerProvider 生成，读取经助手同步并校验的实际应用 classpath。container 模式由原 dev-runtime 托管专属语言容器，Host 通过内部 TCP 接收内容寻址的环境制品，再启动镜像自带的 stdio 引擎。两种模式都不在平台或助手 JVM 内加载语义引擎，助手只导出实际环境。共享生命周期及其他语言接入见[插件指南](../ouroboros-dev-code-intelligence/LANGUAGE-PLUGINS.md)。

发行目录同时包含 host.jar、engine.json 与 Dockerfile，可用 `docker build -t ouroboros-language-groovy:script-el-completion target/distribution` 构建（在本模块执行）。容器开关、镜像映射、原 RuntimeBackend 装配与租约边界见插件指南第 8 节。Host 与语义 JVM 使用各自堆上限，实际内存还包含 native/JDT 开销；不能按 heap 估算总 RSS。


## 编辑能力与范围

实现补全、悬停、签名、语法诊断、当前文档定义/引用/重命名、文档符号、语义 tokens、缩进格式化和补齐括号 quick fix。GE 负责局部变量、Java 泛型、闭包、collect、DelegatesTo 和 GString 的类型语义；ContextTypeLookup 只把平台 Binding 和结构 Map 接入正式 typeLookup 扩展。

用户 ASTTest、Grape 下载和脚本主体不会在分析时执行。字段形状来自 CodeContextService/TypeShape/FlowNodeAnalyzer，unknown 保持未知。不提供完整 IDE 的跨文件重构、任意 DSL 的确定类型、全套代码风格格式化或全部 quick fix。增量行为有文档版本约束，文本及上下文变化会重建同一 working copy 的模型。

## 分段验证

场景合同、前置依赖与历史研究见 [testing 场景](../../testing/integration/feature/development/groovy-language-service/harness.playbook.md)。正式产品 LSP：

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/product_probe.py
```

独立平台/助手 JVM 与真实 RabbitMQ 环境同步链路（另一个终端运行 server；本机需已配置开发 RabbitMQ）：

```bash
python3 testing/integration/feature/development/groovy-language-service/scripts/full_chain.py --prepare --spring.application.name=example
python3 testing/integration/feature/development/groovy-language-service/scripts/chain_probe.py
```

Monaco 联调、录屏、trace（在 frontends/web/main-app）：

```bash
CODE_CONTEXT_BACKEND=groovy npx playwright test --config=playwright.code-intelligence.config.js
npx vitest run tests/unit/src/components/amis/renderers/editor-lsp-session.test.js tests/unit/src/components/amis/renderers/script-editor-intelligence-runtime.test.js
```

浏览器用 AMIS 表单夹具提供草稿与布局，走正式 Controller/Service/Command、真实 RabbitMQ 和 helper 扩展 JAR，未模拟代码提示响应。元数据由助手调用实际运行时快照工厂采集，应用工具 Binding 目录使用受控空目录；GraalJS 全局对象由实际执行器采集；不是完整平台登录/画布部署测试。截图、video.webm 和 trace 位于 main-app/target/playwright-groovy-lsp；原始协议和分段结果归 tmp/testing-evidence/groovy-language-service。测试不能替代目标环境部署验收或生产负载基准。
