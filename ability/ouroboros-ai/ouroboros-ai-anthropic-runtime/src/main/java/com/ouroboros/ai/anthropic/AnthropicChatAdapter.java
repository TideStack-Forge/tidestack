package com.ouroboros.ai.anthropic;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolChoiceNone;
import com.anthropic.models.messages.ToolChoiceTool;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicServiceTier;
import org.springframework.ai.anthropic.AnthropicSkillContainer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.ChatResponse;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.util.Json;

public class AnthropicChatAdapter implements AiChatAdapter {
  private static final String PROVIDER_OPTION_API_KEY = "apiKey";
  private static final String PROVIDER_OPTION_ANTHROPIC_VERSION = "anthropicVersion";
  private static final String PROVIDER_OPTION_ANTHROPIC_BETA = "anthropicBeta";
  private static final String PROVIDER_OPTION_SERVICE_TIER = "serviceTier";
  private static final String PROVIDER_OPTION_TOP_K = "topK";
  private static final String PROVIDER_OPTION_SYSTEM = "system";
  private static final String PROVIDER_OPTION_INFERENCE_GEO = "inferenceGeo";
  private static final String PROVIDER_OPTION_CONTAINER = "container";
  private static final String PROVIDER_OPTION_STOP_SEQUENCES = "stopSequences";
  private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
  private static final int DEFAULT_MAX_TOKENS = 1024;
  private static final List<String> ALLOWED_PROVIDER_OPTIONS = List.of(PROVIDER_OPTION_API_KEY,
      PROVIDER_OPTION_ANTHROPIC_VERSION, PROVIDER_OPTION_ANTHROPIC_BETA, PROVIDER_OPTION_SERVICE_TIER,
      PROVIDER_OPTION_TOP_K, PROVIDER_OPTION_SYSTEM, PROVIDER_OPTION_INFERENCE_GEO,
      PROVIDER_OPTION_CONTAINER, PROVIDER_OPTION_STOP_SEQUENCES);

  private final AnthropicAdapterFactory adapterFactory;
  private final AiToolRegistry toolRegistry;

  public AnthropicChatAdapter(AnthropicAdapterFactory adapterFactory) {
    this(adapterFactory, new NoopAiToolRegistry());
  }

  public AnthropicChatAdapter(AnthropicAdapterFactory adapterFactory, AiToolRegistry toolRegistry) {
    this.adapterFactory = Objects.requireNonNull(adapterFactory, "Anthropic adapter factory must not be null");
    this.toolRegistry = toolRegistry == null ? new NoopAiToolRegistry() : toolRegistry;
  }

  @Override
  public AiProviderFamily getProviderFamily() {
    return AiProviderFamily.ANTHROPIC;
  }

  @Override
  public ChatResponse chat(AiModelDescriptor modelDescriptor, ChatRequest request) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    Objects.requireNonNull(request, "Chat request must not be null");
    AnthropicAdapterFactory.AnthropicInvocation invocation = null;
    try {
      invocation = adapterFactory.createInvocation(modelDescriptor, request.getInvocationContext());
      var options = toOptions(modelDescriptor, request, invocation);
      var messages = AnthropicChatMessageMapper.toMessages(request.getMessageList(), providerSystem(modelDescriptor, request.getOptions()));
      var response = adapterFactory.createChatModel(options).call(new Prompt(messages, options));
      return toChatResponse(modelDescriptor, response);
    } catch (AiPolicyViolationException exception) {
      throw exception;
    } catch (AiProviderProtocolException exception) {
      throw exception;
    } catch (AiInvocationException exception) {
      throw exception;
    } catch (AnthropicServiceException exception) {
      throw new AiProviderProtocolException("Anthropic chat provider error for model: " + modelDescriptor.getModelId()
          + " HTTP status " + exception.statusCode() + ": "
          + sanitize(anthropicBody(exception), apiKey(invocation)), exception);
    } catch (AnthropicInvalidDataException exception) {
      throw new AiProviderProtocolException("Failed to map Anthropic chat response for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (AnthropicException exception) {
      throw new AiProviderProtocolException("Anthropic chat invocation failed for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (RuntimeException exception) {
      throw new AiProviderProtocolException("Failed to map Anthropic chat response for model: " + modelDescriptor.getModelId(),
          exception);
    }
  }

  private AnthropicChatOptions toOptions(AiModelDescriptor modelDescriptor,
                                         ChatRequest request,
                                         AnthropicAdapterFactory.AnthropicInvocation invocation) {
    var options = mergeOptions(modelDescriptor, request.getOptions());
    var providerOptions = mergeProviderOptions(modelDescriptor, options);
    rejectUnknownProviderOptions(providerOptions);
    validateAnthropicVersion(providerOptions.get(PROVIDER_OPTION_ANTHROPIC_VERSION));
    return AnthropicChatOptions.builder()
        .baseUrl(invocation.getBaseUrl())
        .apiKey(invocation.getApiKey())
        .model(Model.of(providerModelName(modelDescriptor, request.getModelId())))
        .maxTokens(resolveMaxTokens(options))
        .temperature(options == null ? null : options.getTemperature())
        .topP(options == null ? null : options.getTopP())
        .topK(integerOption(providerOptions, PROVIDER_OPTION_TOP_K))
        .stopSequences(stopSequences(options, providerOptions))
        .toolChoice(toolChoice(options))
        .toolCallbacks(toToolCallbacks(request))
        .httpHeaders(httpHeaders(providerOptions))
        .serviceTier(serviceTier(providerOptions.get(PROVIDER_OPTION_SERVICE_TIER)))
        .inferenceGeo(stringOption(providerOptions, PROVIDER_OPTION_INFERENCE_GEO))
        .skillContainer(skillContainer(providerOptions.get(PROVIDER_OPTION_CONTAINER)))
        .build();
  }

  private List<ToolCallback> toToolCallbacks(ChatRequest request) {
    if (request.getToolNames().isEmpty()) {
      return Collections.emptyList();
    }
    List<ToolCallback> callbacks = new ArrayList<>();
    for (String toolName : request.getToolNames()) {
      toolRegistry.get(toolName).ifPresent(toolDescriptor -> {
        if (!toolDescriptor.isAllowModelInvocation()) {
          throw new AiPolicyViolationException("AI model invocation is not allowed for tool: " + toolDescriptor.getToolName());
        }
        callbacks.add(new DescriptorToolCallback(toolDescriptor));
      });
    }
    return callbacks;
  }

  private static ChatResponse toChatResponse(AiModelDescriptor modelDescriptor,
                                             org.springframework.ai.chat.model.ChatResponse response) {
    if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
      throw new IllegalStateException("Anthropic chat response is empty");
    }
    var message = response.getResult().getOutput();
    return ChatResponse.builder()
        .modelId(modelDescriptor.getModelId())
        .text(message.getText())
        .content(mapContent(message))
        .toolCalls(mapToolCalls(message.getToolCalls()))
        .usage(mapUsage(response.getMetadata() == null ? null : response.getMetadata().getUsage()))
        .build();
  }

  private static List<Map<String, Object>> mapContent(AssistantMessage message) {
    List<Map<String, Object>> content = new ArrayList<>();
    if (hasText(message.getText())) {
      content.add(mapOf("type", "text", "text", message.getText()));
    }
    for (Map<String, Object> toolCall : mapToolCalls(message.getToolCalls())) {
      content.add(mapOf("type", "tool_use",
          "id", toolCall.get("callId"),
          "name", toolCall.get("name"),
          "input", toolCall.get("arguments")));
    }
    return content;
  }

  private static List<Map<String, Object>> mapToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return Collections.emptyList();
    }
    List<Map<String, Object>> mapped = new ArrayList<>();
    for (AssistantMessage.ToolCall call : toolCalls) {
      Map<String, Object> toolCall = new LinkedHashMap<>();
      putIfPresent(toolCall, "id", call.id());
      putIfPresent(toolCall, "callId", call.id());
      putIfPresent(toolCall, "name", call.name());
      putIfPresent(toolCall, "arguments", parseArguments(call.arguments()));
      mapped.add(toolCall);
    }
    return mapped;
  }

  private static Object parseArguments(String arguments) {
    if (!hasText(arguments)) {
      return Collections.emptyMap();
    }
    try {
      return Json.toMap(arguments);
    } catch (RuntimeException exception) {
      return arguments;
    }
  }

  private static Map<String, Object> mapUsage(org.springframework.ai.chat.metadata.Usage usage) {
    if (usage == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> mapped = new LinkedHashMap<>();
    putIfPresent(mapped, "inputTokens", usage.getPromptTokens());
    putIfPresent(mapped, "outputTokens", usage.getCompletionTokens());
    putIfPresent(mapped, "totalTokens", usage.getTotalTokens());
    return mapped;
  }

  private static AiOptions mergeOptions(AiModelDescriptor modelDescriptor, AiOptions requestOptions) {
    return modelDescriptor.getDefaultOptions() == null
        ? requestOptions
        : requestOptions == null ? modelDescriptor.getDefaultOptions() : modelDescriptor.getDefaultOptions().merge(requestOptions);
  }

  private static Map<String, Object> mergeProviderOptions(AiModelDescriptor modelDescriptor, AiOptions options) {
    Map<String, Object> providerOptions = new LinkedHashMap<>();
    providerOptions.putAll(modelDescriptor.getProviderOptions());
    if (options != null) {
      providerOptions.putAll(options.getProviderOptions());
    }
    return providerOptions;
  }

  private static void rejectUnknownProviderOptions(Map<String, Object> providerOptions) {
    for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
      if (entry.getValue() != null && !ALLOWED_PROVIDER_OPTIONS.contains(entry.getKey())) {
        throw new AiProviderProtocolException("Anthropic provider option is not supported: " + entry.getKey());
      }
    }
  }

  private static void validateAnthropicVersion(Object value) {
    if (value == null) {
      return;
    }
    var anthropicVersion = String.valueOf(value);
    if (!DEFAULT_ANTHROPIC_VERSION.equals(anthropicVersion)) {
      throw new AiProviderProtocolException("Anthropic transition adapter currently supports only anthropicVersion=" + DEFAULT_ANTHROPIC_VERSION);
    }
  }

  private static String providerSystem(AiModelDescriptor modelDescriptor, AiOptions requestOptions) {
    var options = mergeOptions(modelDescriptor, requestOptions);
    var providerOptions = mergeProviderOptions(modelDescriptor, options);
    rejectUnknownProviderOptions(providerOptions);
    return stringOption(providerOptions, PROVIDER_OPTION_SYSTEM);
  }

  private static int resolveMaxTokens(AiOptions options) {
    return options != null && options.getMaxTokens() != null ? options.getMaxTokens() : DEFAULT_MAX_TOKENS;
  }

  private static List<String> stopSequences(AiOptions options, Map<String, Object> providerOptions) {
    var providerStop = providerOptions.get(PROVIDER_OPTION_STOP_SEQUENCES);
    if (providerStop != null) {
      return stringList(providerStop);
    }
    return options == null || options.getStop().isEmpty() ? null : options.getStop();
  }

  private static ToolChoice toolChoice(AiOptions options) {
    if (options == null || !hasText(options.getToolChoice())) {
      return null;
    }
    var normalized = options.getToolChoice().trim();
    if ("auto".equalsIgnoreCase(normalized)) {
      return ToolChoice.ofAuto(ToolChoiceAuto.builder().build());
    }
    if ("none".equalsIgnoreCase(normalized)) {
      return ToolChoice.ofNone(ToolChoiceNone.builder().build());
    }
    if ("any".equalsIgnoreCase(normalized) || "required".equalsIgnoreCase(normalized)) {
      return ToolChoice.ofAny(ToolChoiceAny.builder().build());
    }
    return ToolChoice.ofTool(ToolChoiceTool.builder().name(normalized).build());
  }

  private static Map<String, String> httpHeaders(Map<String, Object> providerOptions) {
    var anthropicBeta = providerOptions.get(PROVIDER_OPTION_ANTHROPIC_BETA);
    if (anthropicBeta == null) {
      return null;
    }
    return Map.of("anthropic-beta", String.join(",", stringList(anthropicBeta)));
  }

  private static AnthropicServiceTier serviceTier(Object value) {
    if (value == null) {
      return null;
    }
    var normalized = String.valueOf(value).trim().replace('-', '_').toUpperCase(Locale.ROOT);
    try {
      return AnthropicServiceTier.valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new AiProviderProtocolException("Anthropic serviceTier is not supported: " + value, exception);
    }
  }

  private static AnthropicSkillContainer skillContainer(Object value) {
    if (value == null) {
      return null;
    }
    var skills = stringList(containerSkills(value));
    if (skills.isEmpty()) {
      return null;
    }
    var builder = AnthropicSkillContainer.builder();
    for (String skill : skills) {
      builder.skill(skill);
    }
    return builder.build();
  }

  private static Object containerSkills(Object value) {
    if (value instanceof Map) {
      var map = objectMap((Map<?, ?>) value);
      return map.getOrDefault("skills", map.get("skill"));
    }
    return value;
  }

  private static Integer integerOption(Map<String, Object> options, String key) {
    var value = options.get(key);
    if (value == null) {
      return null;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private static String stringOption(Map<String, Object> options, String key) {
    var value = options.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static List<String> stringList(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
    List<String> values = new ArrayList<>();
    if (value instanceof Iterable) {
      for (Object item : (Iterable<?>) value) {
        addString(values, item);
      }
      return values;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int index = 0; index < length; index++) {
        addString(values, Array.get(value, index));
      }
      return values;
    }
    addString(values, value);
    return values;
  }

  private static void addString(List<String> values, Object value) {
    if (value != null && hasText(String.valueOf(value))) {
      values.add(String.valueOf(value));
    }
  }

  private static Map<String, Object> objectMap(Map<?, ?> source) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (key != null) {
        mapped.put(String.valueOf(key), value);
      }
    });
    return mapped;
  }

  private static String providerModelName(AiModelDescriptor modelDescriptor, String fallbackModelId) {
    return hasText(modelDescriptor.getModelName()) ? modelDescriptor.getModelName() : fallbackModelId;
  }

  private static String apiKey(AnthropicAdapterFactory.AnthropicInvocation invocation) {
    return invocation == null ? null : invocation.getApiKey();
  }

  private static String anthropicBody(AnthropicServiceException exception) {
    return exception.body() == null ? exception.getMessage() : exception.body().toString();
  }

  private static String sanitize(String message, String apiKey) {
    if (message == null || apiKey == null) {
      return message;
    }
    return message.replace(apiKey, "[redacted]");
  }

  private static Map<String, Object> mapOf(Object... values) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      if (values[i + 1] != null) {
        map.put(String.valueOf(values[i]), values[i + 1]);
      }
    }
    return map;
  }

  private static void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static final class DescriptorToolCallback implements ToolCallback {
    private final AiToolDescriptor toolDescriptor;

    private DescriptorToolCallback(AiToolDescriptor toolDescriptor) {
      this.toolDescriptor = toolDescriptor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder()
          .name(toolDescriptor.getToolName())
          .description(toolDescriptor.getDescription())
          .inputSchema(inputSchema(toolDescriptor.getInputSchema()))
          .build();
    }

    @Override
    public String call(String input) {
      throw new AiPolicyViolationException(
          "Spring AI internal tool execution is disabled for Anthropic tool: " + toolDescriptor.getToolName());
    }

    private static String inputSchema(Map<String, Object> schema) {
      var effectiveSchema = schema.isEmpty() ? mapOf("type", "object", "properties", Collections.emptyMap()) : schema;
      return Json.tryToJsonString(effectiveSchema)
          .getOrElseThrow(exception -> new AiProviderProtocolException("Failed to serialize Anthropic tool schema", exception));
    }
  }

  private static final class NoopAiToolRegistry implements AiToolRegistry {

    @Override
    public Optional<AiToolDescriptor> get(String toolName) {
      return Optional.empty();
    }

    @Override
    public List<AiToolDescriptor> getAll() {
      return Collections.emptyList();
    }
  }
}
