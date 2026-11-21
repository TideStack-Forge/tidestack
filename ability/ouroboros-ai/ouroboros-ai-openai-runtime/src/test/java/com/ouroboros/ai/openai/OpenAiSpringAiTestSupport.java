package com.ouroboros.ai.openai;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

import okhttp3.mockwebserver.MockResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.ouroboros.ai.AiCapability;
import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.TextContentPart;
import com.ouroboros.ai.chat.ToolCallPart;
import com.ouroboros.ai.chat.ToolResultPart;
import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;
import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class OpenAiSpringAiTestSupport {

  private OpenAiSpringAiTestSupport() {
  }

  static OpenAiChatAdapter chatAdapter(String baseUrl) {
    return new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
        new RecordingConnectionResolver(connection(baseUrl, "test-key"))));
  }

  static MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  static String chatResponseBody(String text) {
    return "{\"id\":\"chat-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4o-mini\","
        + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"" + text + "\"}}],"
        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
  }

  static AiModelDescriptor chatDescriptor() {
    return AiModelDescriptor.builder()
        .modelId("platform-openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .modelName("gpt-4o-mini")
        .capability(AiCapability.CHAT)
        .defaultOptions(AiOptions.builder()
            .temperature(0.7)
            .maxTokens(32)
            .providerOption("serviceTier", "default")
            .build())
        .providerOption("safetyIdentifier", "model-safe-user")
        .build();
  }

  static AiModelDescriptor chatDescriptorWithProviderApiKey() {
    return AiModelDescriptor.builder()
        .modelId("platform-openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .providerOption("apiKey", "model-provider-api-key")
        .build();
  }

  static AiModelDescriptor embeddingDescriptor() {
    return AiModelDescriptor.builder()
        .modelId("platform-openai-embedding")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .modelName("text-embedding-3-small")
        .capability(AiCapability.EMBEDDING)
        .build();
  }

  static ChatRequest chatRequest() {
    return ChatRequest.builder()
        .modelId("platform-openai-chat")
        .message(textMessage(ChatMessageRole.SYSTEM, "system rules"))
        .message(textMessage(ChatMessageRole.USER, "hello"))
        .message(ChatMessage.builder()
            .role(ChatMessageRole.ASSISTANT)
            .addContentPart(ToolCallPart.builder()
                .callId("call-123")
                .toolName("search")
                .arguments(mapOf("query", "hello"))
                .build())
            .build())
        .message(ChatMessage.builder()
            .role(ChatMessageRole.TOOL)
            .addContentPart(ToolResultPart.builder()
                .callId("call-123")
                .toolName("search")
                .result(mapOf("count", 3))
                .build())
            .build())
        .options(AiOptions.builder()
            .temperature(0.2)
            .topP(0.9)
            .maxTokens(128)
            .stop(List.of("stop-a", "stop-b"))
            .toolChoice("auto")
            .providerOption("serviceTier", "standard")
            .providerOption("promptCacheKey", "cache-key-1")
            .providerOption("promptCacheRetention", "24h")
            .providerOption("safetyIdentifier", "safe-user")
            .build())
        .toolNames(List.of("search"))
        .invocationContext(AiInvocationContext.builder()
            .tenantId("tenant-a")
            .environment("prod")
            .build())
        .build();
  }

  static ChatMessage textMessage(ChatMessageRole role, String text) {
    return ChatMessage.builder()
        .role(role)
        .addContentPart(TextContentPart.of(text))
        .build();
  }

  static AiToolDescriptor searchTool(boolean allowed) {
    return AiToolDescriptor.builder()
        .toolName("search")
        .description("Search documents")
        .inputSchema(mapOf("type", "object", "properties", mapOf("query", mapOf("type", "string")), "required", List.of("query")))
        .allowModelInvocation(allowed)
        .build();
  }

  static AiProviderConnectionDescriptor connection(String endpoint, String credentialsRef) {
    return connectionWithOptions(endpoint, credentialsRef, null);
  }

  static AiProviderConnectionDescriptor connectionWithOptions(String endpoint, String credentialsRef,
                                                              Map<String, Object> connectionOptions) {
    return AiProviderConnectionDescriptor.builder()
        .providerInstanceId("openai-prod")
        .providerFamily(AiProviderFamily.OPENAI)
        .endpointRef(endpoint)
        .credentialsRef(credentialsRef)
        .connectionOptions(connectionOptions)
        .build();
  }

  static Map<String, Object> mapOf(Object... values) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return map;
  }

  static Map<?, ?> map(Object value) {
    return assertInstanceOf(Map.class, value);
  }

  static List<?> list(Object value) {
    return assertInstanceOf(List.class, value);
  }

  static final class RecordingOpenAiAdapterFactory extends OpenAiAdapterFactory {
    private int apiCreations;

    RecordingOpenAiAdapterFactory(Function<String, String> credentialResolver,
                                  AiProviderConnectionResolver providerConnectionResolver) {
      super(credentialResolver, providerConnectionResolver);
    }

    @Override
    protected OpenAiChatModel createChatModel(OpenAiChatOptions options) {
      apiCreations++;
      return super.createChatModel(options);
    }

    @Override
    protected OpenAiEmbeddingModel createEmbeddingModel(OpenAiEmbeddingOptions options) {
      apiCreations++;
      return super.createEmbeddingModel(options);
    }

    int getApiCreations() {
      return apiCreations;
    }
  }

  static final class RecordingConnectionResolver implements AiProviderConnectionResolver {
    private final Queue<AiProviderConnectionDescriptor> connections = new ArrayDeque<>();
    private int resolveCount;
    private AiResolutionScope lastScope;

    RecordingConnectionResolver(AiProviderConnectionDescriptor... connections) {
      Collections.addAll(this.connections, connections);
    }

    @Override
    public Optional<AiProviderConnectionDescriptor> resolve(AiModelDescriptor modelDescriptor, AiResolutionScope scope) {
      resolveCount++;
      lastScope = scope;
      return Optional.ofNullable(connections.poll());
    }

    int getResolveCount() {
      return resolveCount;
    }

    AiResolutionScope getLastScope() {
      return lastScope;
    }
  }

  static final class EmptyToolRegistry implements AiToolRegistry {
    @Override
    public Optional<AiToolDescriptor> get(String toolName) {
      return Optional.empty();
    }

    @Override
    public List<AiToolDescriptor> getAll() {
      return Collections.emptyList();
    }
  }

  static final class SingleToolRegistry implements AiToolRegistry {
    private final AiToolDescriptor tool;

    SingleToolRegistry(AiToolDescriptor tool) {
      this.tool = tool;
    }

    @Override
    public Optional<AiToolDescriptor> get(String toolName) {
      return tool.getToolName().equals(toolName) ? Optional.of(tool) : Optional.empty();
    }

    @Override
    public List<AiToolDescriptor> getAll() {
      return List.of(tool);
    }
  }
}
