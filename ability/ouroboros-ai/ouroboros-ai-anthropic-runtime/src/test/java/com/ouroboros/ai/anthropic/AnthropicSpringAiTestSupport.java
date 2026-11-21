package com.ouroboros.ai.anthropic;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

import okhttp3.mockwebserver.MockResponse;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import com.ouroboros.ai.AiCapability;
import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.MediaContentPart;
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

final class AnthropicSpringAiTestSupport {

  private AnthropicSpringAiTestSupport() {
  }

  static AnthropicChatAdapter chatAdapter(String baseUrl) {
    return new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
        new RecordingConnectionResolver(connection(baseUrl, "test-key"))));
  }

  static MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  static String textResponseBody(String text) {
    return "{\"id\":\"msg_123\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet-20241022\","
        + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],"
        + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
        + "\"usage\":{\"input_tokens\":5,\"output_tokens\":2}}";
  }

  static String toolUseResponseBody() {
    return "{\"id\":\"msg_456\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet-20241022\","
        + "\"content\":[{\"type\":\"tool_use\",\"id\":\"call-response\",\"name\":\"search\",\"input\":{\"query\":\"hello\"}},"
        + "{\"type\":\"text\",\"text\":\"ok\"}],"
        + "\"stop_reason\":\"tool_use\",\"stop_sequence\":null,"
        + "\"usage\":{\"input_tokens\":4,\"output_tokens\":3}}";
  }

  static AiModelDescriptor chatDescriptor() {
    return AiModelDescriptor.builder()
        .modelId("platform-anthropic-chat")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .providerInstanceId("anthropic-test")
        .modelName("claude-3-5-sonnet-20241022")
        .capability(AiCapability.CHAT)
        .defaultOptions(AiOptions.builder()
            .temperature(0.4)
            .maxTokens(64)
            .providerOption("anthropicVersion", "2023-06-01")
            .build())
        .build();
  }

  static AiModelDescriptor chatDescriptorWithProviderApiKey() {
    return AiModelDescriptor.builder()
        .modelId("platform-anthropic-chat")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .providerInstanceId("anthropic-test")
        .providerOption("apiKey", "model-provider-api-key")
        .build();
  }

  static ChatRequest chatRequest() {
    return ChatRequest.builder()
        .modelId("platform-anthropic-chat")
        .message(textMessage(ChatMessageRole.SYSTEM, "system rules"))
        .message(textMessage(ChatMessageRole.DEVELOPER, "developer rules"))
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
            .topP(0.7)
            .maxTokens(256)
            .stop(List.of("portable-stop"))
            .toolChoice("required")
            .providerOption("anthropicBeta", new String[] {"beta-array"})
            .providerOption("serviceTier", "standard_only")
            .providerOption("topK", 3)
            .providerOption("system", "provider system")
            .providerOption("inferenceGeo", "eu")
            .providerOption("container", List.of("pdf"))
            .providerOption("stopSequences", List.of("END", "STOP"))
            .build())
        .toolNames(List.of("search"))
        .invocationContext(AiInvocationContext.builder()
            .tenantId("tenant-a")
            .environment("prod")
            .build())
        .build();
  }

  static ChatRequest blankCallIdChat() {
    return ChatRequest.builder()
        .modelId("platform-anthropic-chat")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.TOOL)
            .addContentPart(ToolResultPart.builder()
                .callId(" ")
                .toolName("search")
                .result(Collections.emptyMap())
                .build())
            .build())
        .build();
  }

  static ChatRequest mediaChat() {
    return ChatRequest.builder()
        .modelId("platform-anthropic-chat")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.USER)
            .addContentPart(MediaContentPart.builder().mediaType("image/png").uri("file://image.png").build())
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
        .description("Search records")
        .inputSchema(mapOf("type", "object", "properties", mapOf("query", mapOf("type", "string")),
            "required", List.of("query"), "additionalProperties", false))
        .allowModelInvocation(allowed)
        .build();
  }

  static AiProviderConnectionDescriptor connection(String endpoint, String credentialsRef) {
    return connectionWithOptions(endpoint, credentialsRef, null);
  }

  static AiProviderConnectionDescriptor connectionWithOptions(String endpoint, String credentialsRef,
                                                              Map<String, Object> connectionOptions) {
    return AiProviderConnectionDescriptor.builder()
        .providerInstanceId("anthropic-test")
        .providerFamily(AiProviderFamily.ANTHROPIC)
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

  static final class RecordingAnthropicAdapterFactory extends AnthropicAdapterFactory {
    private int modelCreations;

    RecordingAnthropicAdapterFactory(Function<String, String> credentialResolver,
                                     AiProviderConnectionResolver providerConnectionResolver) {
      super(credentialResolver, providerConnectionResolver);
    }

    @Override
    protected AnthropicChatModel createChatModel(AnthropicChatOptions options) {
      modelCreations++;
      return super.createChatModel(options);
    }

    int getModelCreations() {
      return modelCreations;
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

  static final class SingleToolRegistry implements AiToolRegistry {
    private final AiToolDescriptor toolDescriptor;

    SingleToolRegistry(AiToolDescriptor toolDescriptor) {
      this.toolDescriptor = toolDescriptor;
    }

    @Override
    public Optional<AiToolDescriptor> get(String toolName) {
      return toolDescriptor.getToolName().equals(toolName) ? Optional.of(toolDescriptor) : Optional.empty();
    }

    @Override
    public List<AiToolDescriptor> getAll() {
      return List.of(toolDescriptor);
    }
  }
}
