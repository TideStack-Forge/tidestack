# Ouroboros AI

`ouroboros-ai` 为平台提供大模型接入能力包；主仓 Java 21 / Spring Boot 3.5.11 为编译运行基线，provider 使用 Spring AI。公共 API 仍以 Ouroboros 自己的 capability、descriptor、registry、request/response 和 exception 合同为准；Spring AI 只作为 provider runtime 的内部实现细节，不暴露到稳定 SPI。

公共入口 `ouroboros-ai-starter` 当前通过 POM 依赖映射到 Spring AI aggregate starter；需要单 provider 的应用可显式依赖对应 starter。历史 default-exit packet 属于当时的验收记录，不能证明当前 HEAD、部署环境或外部 provider 已通过验收。

## 接口与装配边界

- `providerInstanceId` 是模型到连接拓扑的绑定方式；endpoint 与凭据必须由 `AiProviderConnectionDescriptorProvider` 提供，不再通过 `AiModelDescriptor` 承载。
- `bindingRef`、`retrieverRef`、`backendRef` 已进入 descriptor 合同，但仍不等于默认 alias 自动启用完整 RAG 编排或独立 tool executor。
- `tenant/app/environment/workspace` 解析作用域和 precedence chain 已进入运行时语义模型，但示例 YAML 只是“可映射的 descriptor 形状”，不会自动获得多来源合并、配置中心覆盖或 workspace overlay 魔法。
- 当前稳定服务入口仍以同步 `chat` / `embed` 为主；`CHAT_STREAM`、独立 MCP server export、独立 tool executor 等仍属于后续扩展方向，不在默认 alias 中承诺稳定 API。
- RAG readiness 是 `explicit-opt-in`：`ouroboros-ai-rag-runtime` 可显式接入 Spring AI `VectorStore`，但不作为 `ouroboros-ai-starter` 默认硬门槛。
- MCP readiness 是 `explicit-opt-in`：当前交付是 MCP client bridge / tool descriptor 接入，不包含平台稳定的 MCP server export。

## 模块组成

- `ouroboros-ai-core`: 平台统一 capability、descriptor、request/response、options、exception 定义。
- `ouroboros-ai-starter`: 面向消费方的稳定默认入口，当前映射到 `ouroboros-ai-starter-spring-ai`。
- `ouroboros-ai-starter-runtime`: provider-neutral runtime starter，只引入平台 runtime kernel。
- `ouroboros-ai-starter-openai`: provider-specific starter，引入 runtime kernel 和 OpenAI runtime。
- `ouroboros-ai-starter-anthropic`: provider-specific starter，引入 runtime kernel 和 Anthropic runtime。
- `ouroboros-ai-starter-spring-ai`: Spring AI convenience aggregate starter，引入 OpenAI 与 Anthropic provider-specific starters。
- `ouroboros-ai-starter-legacy`: 维护用途 starter；当前复用相同 runtime/provider，并额外引入 RAG，Java 21 profile 下还引入 MCP。
- `ouroboros-ai-runtime`: descriptor provider、registry、invocation pipeline、adapter SPI。
- `ouroboros-ai-openai-runtime`: OpenAI family Spring AI-backed adapter，支持 chat、embedding 与 OpenAI-compatible gateway 形态。
- `ouroboros-ai-anthropic-runtime`: Anthropic family Spring AI-backed chat adapter，保留 Anthropic 原生 provider 语义；embedding readiness 为 `n/a`。
- `ouroboros-ai-rag-runtime`: 显式 opt-in 的知识库 descriptor、retriever SPI、内存检索器和 Spring AI `VectorStore` adapter。
- `ouroboros-ai-mcp-runtime`: 显式 opt-in 的 MCP endpoint 发现、客户端桥接和工具注册表装配；MCP server export 不进入默认稳定合同。

## 依赖约束

- 消费方默认依赖 `ouroboros-ai-starter`。
- 只需要单个 provider 的平台应用可以显式依赖 `ouroboros-ai-starter-openai` 或 `ouroboros-ai-starter-anthropic`，避免把未使用 provider 带入最终 artifact。
- 需要同时启用 OpenAI 与 Anthropic 的应用可以依赖公共 `ouroboros-ai-starter`，或显式依赖 `ouroboros-ai-starter-spring-ai` 来锁定 Spring AI aggregate。
- 维护现有 legacy 依赖的应用可显式依赖 `ouroboros-ai-starter-legacy`；其当前 POM 并不保留另一套旧 provider 实现。
- 聚合模块和平台应用不应直接依赖 `ouroboros-ai-openai-runtime`、`ouroboros-ai-anthropic-runtime`、`ouroboros-ai-rag-runtime`、`ouroboros-ai-mcp-runtime`。
- line-specific starter 是受控 rollout / provider 粒度打包的装配入口；provider runtime 模块本身仍不是业务模块依赖面。

## 当前边界

- 第一阶段默认 provider line 聚焦 `OPENAI` 与 `ANTHROPIC`。
- OpenAI adapter 实现同步 chat 与 embedding；外部服务可用性需在目标环境验证。
- Anthropic adapter 实现同步 chat，不提供 embedding；不要配置 Anthropic embedding model 作为默认能力。
- `LiteLLM`、`Ollama` 先按 OpenAI-compatible 入口接入，不新增 provider family。
- 其他协议族暂时只保留扩展点，目前尚未实现。
- RAG 与 MCP 是 `explicit-opt-in`，不是默认 alias hard gate。
- `examples/*.yml` 是建议的外部配置契约，需要由平台自己的 `AiModelDescriptorProvider`、`AiKnowledgeBaseDescriptorProvider`、`AiMcpEndpointDescriptorProvider` 等组件去映射；当前运行时不直接自动绑定这些 YAML。

## Smoke Verification

以下命令在仓库根目录、Java 21 环境执行；`jdk21` profile 纳入 MCP 模块。命令是验证入口，本页不记录本次执行结果。

```bash
mvn -Pjdk21 -pl :ouroboros-ai-core,:ouroboros-ai-runtime,:ouroboros-ai-openai-runtime,:ouroboros-ai-anthropic-runtime,:ouroboros-ai-rag-runtime,:ouroboros-ai-mcp-runtime -am test
mvn -pl :ouroboros-ability-all-runtime -am -DskipTests compile
```

## 推荐接入方式

当前推荐使用“外部配置 -> descriptor provider -> registry -> adapter”装配链路：

```java
@Configuration
public class AiDescriptorConfiguration {

  @Bean
  public AiProviderConnectionDescriptorProvider aiProviderConnectionDescriptorProvider() {
    return () -> Collections.singletonList(
        AiProviderConnectionDescriptor.builder()
            .providerInstanceId("openai-prod")
            .providerFamily(AiProviderFamily.OPENAI)
            .transportKind("HTTP")
            .endpointRef("https://api.openai.com/v1")
            .credentialsRef("secret://ai/openai/prod")
            .build()
    );
  }

  @Bean
  public AiModelDescriptorProvider aiModelDescriptorProvider() {
    return () -> Collections.singletonList(
        AiModelDescriptor.builder()
            .modelId("openai-chat")
            .providerInstanceId("openai-prod")
            .providerFamily(AiProviderFamily.OPENAI)
            .providerKey("openai")
            .modelName("gpt-4o")
            .capability(AiCapability.CHAT)
            .capability(AiCapability.TOOL_CALLING)
            .defaultOptions(AiOptions.builder().temperature(0.2d).maxTokens(4096).build())
            .build()
    );
  }
}
```

如果当前接入点还没有单独维护 provider connection，应先补 `AiProviderConnectionDescriptorProvider`；运行时不再从 `AiModelDescriptor` 回退读取 endpoint 或 credentials。

## Provider 说明

- OpenAI:
  - 推荐把 endpoint 与 credentials 放到 `AiProviderConnectionDescriptor`。
  - `providerOptions.apiKey` 仍可作为兼容字段，但不建议继续把连接语义堆在 model descriptor 上。
  - chat 与 embedding 由 OpenAI runtime adapter 提供，接入时分别验证请求、响应和失败分支。
- Anthropic:
  - 生产接入优先使用原生 `ANTHROPIC` adapter，不建议退化成 OpenAI compatibility mode。
  - 当前过渡实现只接受 `providerOptions.anthropicVersion=2023-06-01`。
  - chat 由 Anthropic runtime adapter 提供；embedding 不在该 adapter 能力范围。
- LiteLLM:
  - 在平台侧仍然按 `OPENAI` family 描述。
  - 如果网关背后路由到 Anthropic，这也只应作为兼容模式；正式生产配置仍建议保留独立 `ANTHROPIC` 描述符。
- Ollama:
  - 只作为本地联调和非生产验证样例。
  - 仍然使用 `OPENAI` family 的兼容协议面，但推荐也通过 `providerInstanceId` 显式挂接本地连接描述。

## Descriptor 约束提醒

- `AiKnowledgeBaseDescriptor.backendRef` / `retrieverRef`
  - 是跨实现的保留引用字段。
  - Spring AI `VectorStore` adapter 只在显式 opt-in 的 `ouroboros-ai-rag-runtime` 中生效。
- `AiToolDescriptor.bindingRef`
  - 先作为后续工具执行绑定合同。
  - 当前并没有独立的通用 `AiToolExecutor` 稳定 SPI。
- `AiPolicyDescriptor`
  - 已支持 registry、resolver 与 effective snapshot。
  - 但不同来源的 precedence chain 仍需要你自己的 descriptor provider 明确落地。

## 样例文件

- [application-ai-openai.yml](examples/application-ai-openai.yml): OpenAI 官方直连样例。
- [application-ai-anthropic.yml](examples/application-ai-anthropic.yml): Anthropic 原生直连样例。
- [application-ai-litellm.yml](examples/application-ai-litellm.yml): LiteLLM 网关样例。
- [application-ai-ollama.yml](examples/application-ai-ollama.yml): Ollama 本地验证样例。

## Legacy 维护说明

`ouroboros-ai-starter-legacy` 是显式维护入口；当前与默认 starter 共用 provider 实现，并增加 RAG/MCP 的装配依赖。切换依赖会改变打包内容，需检查目标应用的依赖树并重新验收，不能仅凭 legacy 名称认定已回退到历史实现。
