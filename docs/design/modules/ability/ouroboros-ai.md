# ouroboros-ai 模块设计摘要

## 模块定位

`ouroboros-ai` 是 ability 层的 AI 接入能力包，为平台提供统一的模型、连接、策略、工具、知识库与 MCP 端点接入骨架。它以 Spring Boot 3.5+ / Spring AI 作为当前默认运行线，但对外稳定合同仍是 Ouroboros 自己的 capability、descriptor、registry、request/response 与 exception 定义；Spring AI 只是 provider runtime 的内部实现细节，不暴露到稳定 SPI。

## 责任边界

该模块负责 descriptor 合同与 registry/resolution 语义、同步 `chat` / `embed` 的调用管道、provider adapter 装配，以及显式 opt-in 的 RAG 与 MCP 桥接；不负责业务侧提示词编排与智能体产品逻辑，也不负责多来源配置合并或配置中心覆盖。它是接入骨架，不是业务智能面。

## 依赖关系

- `ouroboros-ai-core` 是纯合同模块，仅依赖 `common-exception`，不引入任何 provider SDK。
- `ouroboros-ai-runtime` 依赖 `ouroboros-ai-core`、`common-app`、`common-cache` 与 `spring-boot-autoconfigure`。
- provider runtime 依赖 `ouroboros-ai-runtime` 加各自 SDK：`ouroboros-ai-openai-runtime` → `spring-ai-openai`，`ouroboros-ai-anthropic-runtime` → `spring-ai-anthropic`，`ouroboros-ai-rag-runtime` → `spring-ai-vector-store`，`ouroboros-ai-mcp-runtime` → `mcp`。
- starter 线只做装配：`ouroboros-ai-starter` → `ouroboros-ai-starter-spring-ai` → `ouroboros-ai-starter-openai` / `-anthropic` → `ouroboros-ai-starter-runtime` → `ouroboros-ai-runtime`。
- 被依赖面很窄：`platform/ouroboros-mothership` 依赖 `ouroboros-ai-starter-spring-ai`，`ability/ouroboros-ability-all/ouroboros-ability-all-runtime` 依赖 `ouroboros-ai-starter-runtime`，`ouroboros-ability-all-core` 依赖 `ouroboros-ai-core`；其余引用集中在 `bom/pom.xml` 做版本管理。

## 关键抽象

- `AiCapability`、`AiProviderFamily`：能力与 provider 族枚举，是 descriptor 的取值基础。
- `AiModelDescriptor` / `AiProviderConnectionDescriptor`：模型与连接拓扑分离，`providerInstanceId` 负责二者绑定，endpoint 与凭据只由连接描述符承载。
- `AiModelRegistry` / `DefaultAiModelRegistry`、`AiProviderConnectionRegistry`、`AiPolicyRegistry`、`AiToolRegistry`：注册与查找面。
- `AiModelDescriptorProvider`、`AiProviderConnectionDescriptorProvider`、`AiPolicyDescriptorProvider`、`AiToolDescriptorProvider`、`AiKnowledgeBaseDescriptorProvider`、`AiMcpEndpointDescriptorProvider`：外部配置映射进运行时的 SPI 入口。
- `AiInvocationService` / `DefaultAiInvocationService`：稳定服务入口，当前以同步 `ChatResponse chat(...)` 与 `EmbeddingResponse embed(...)` 为主。
- `AiChatAdapter`、`AiEmbeddingAdapter`：provider 侧适配 SPI，`getProviderFamily()` 决定路由归属。
- `AiInvocationPipeline` / `AiInvocationStage`：按 `getOrder()` 排序的调用前置管道（`beforeChat` / `beforeEmbedding`）。
- `AiRuntimeSnapshotResolver`、`AiPolicyResolver`、`AiProviderConnectionResolver` 与 `AiEffectiveRuntimeSnapshot`：作用域解析与生效快照。
- `ChatRequest` / `ChatResponse` / `ChatMessage` 及 `TextContentPart`、`ToolCallPart` 等内容分片，`StreamingChatEvent` 已定义但尚未进入默认稳定入口。
- `AiResolutionScope` 与 `AiInvocationContext`：承载 tenant / app / environment / workspace 的作用域解析语义，precedence chain 由使用方通过 descriptor provider 落地。
- `AiInvocationException`、`AiPolicyViolationException`、`AiProviderProtocolException`：AI 域异常面；provider adapter 会把 `OpenAIException` 系列映射为 `AiProviderProtocolException`，SDK 异常不越过 SPI 边界。
- `AiKnowledgeBaseDescriptor`、`AiMcpEndpointDescriptor`、`AiToolDescriptor` / `AiToolBinding`、`AiPolicyDescriptor` 与 `AiEffectivePolicy`：RAG、MCP、工具绑定与策略四类可选能力的合同，均为保留引用而非默认启用的编排。

## 关键子模块

- `ouroboros-ai-core`：32 个 main java 文件，唯一无框架依赖的合同层。
- `ouroboros-ai-runtime`：28 个 main java 文件，承载 registry、resolution、pipeline 与 adapter SPI。
- `ouroboros-ai-openai-runtime` / `ouroboros-ai-anthropic-runtime`：5 / 4 个文件，Spring AI 支撑的 provider 适配，Anthropic 的 embedding readiness 为 `n/a`。
- `ouroboros-ai-rag-runtime` / `ouroboros-ai-mcp-runtime`：各 5 / 4 个文件，均为显式 opt-in 扩展线。
- `ouroboros-ai-starter*` starter：只做依赖聚合与 line 锁定，不含业务实现。

## 扩展机制

新增 provider 走“descriptor provider → registry → adapter SPI”三段式，而不是修改运行时内核；新能力族只在 `AiCapability` / `AiProviderFamily` 上扩展取值。自动配置按模块注册：`AiRuntimeAutoConfiguration`、`OpenAiRuntimeAutoConfiguration`、`AnthropicRuntimeAutoConfiguration`、`AiRagRuntimeAutoConfiguration`、`AiMcpRuntimeAutoConfiguration` 同时出现在各自 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中。通用规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[ouroboros-ai/README.md](../../../../ability/ouroboros-ai/README.md)，含模块组成、依赖约束、readiness 矩阵、接入链路与样例配置。
- `样例配置`：[examples/application-ai-openai.yml](../../../../ability/ouroboros-ai/examples/application-ai-openai.yml)、[application-ai-anthropic.yml](../../../../ability/ouroboros-ai/examples/application-ai-anthropic.yml)、[application-ai-litellm.yml](../../../../ability/ouroboros-ai/examples/application-ai-litellm.yml)、[application-ai-ollama.yml](../../../../ability/ouroboros-ai/examples/application-ai-ollama.yml)。

## 演进状态

分层收敛度较高：core / runtime / provider-runtime / starter 四段边界清楚，core 保持零框架依赖，消费面被收窄到 `ouroboros-ai-starter*`。三处需要注意：(1) 维护模块以聚合 POM 为准，忽略目录中的旧构建产物不构成模块；(2) `ouroboros-ai-mcp-runtime` 由父 POM 的 `jdk21` profile 引入，JDK 21 及以上会自动激活；仓库 Java 21 默认基线因此会构建该模块，模块进入构建不等于 MCP 能力默认启用；(3) `CHAT_STREAM`、独立 tool executor、MCP server export 仍处扩展方向，README 明确不承诺稳定 API，因此 `StreamingChatEvent`、`AiToolBinding` 等类型不应被消费方当作既成稳定合同。
