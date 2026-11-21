package com.ouroboros.ai.openai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

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

public class OpenAiChatAdapter implements AiChatAdapter {
  private static final String PROVIDER_OPTION_API_KEY = "apiKey";
  private static final List<String> ALLOWED_PROVIDER_OPTIONS = List.of(PROVIDER_OPTION_API_KEY,
      "serviceTier", "promptCacheKey", "promptCacheRetention", "safetyIdentifier");

  private final OpenAiAdapterFactory adapterFactory;
  private final AiToolRegistry toolRegistry;

  public OpenAiChatAdapter(OpenAiAdapterFactory adapterFactory) {
    this(adapterFactory, new NoopAiToolRegistry());
  }

  public OpenAiChatAdapter(OpenAiAdapterFactory adapterFactory, AiToolRegistry toolRegistry) {
    this.adapterFactory = Objects.requireNonNull(adapterFactory, "OpenAI adapter factory must not be null");
    this.toolRegistry = toolRegistry == null ? new NoopAiToolRegistry() : toolRegistry;
  }

  @Override
  public AiProviderFamily getProviderFamily() {
    return AiProviderFamily.OPENAI;
  }

  @Override
  public ChatResponse chat(AiModelDescriptor modelDescriptor, ChatRequest request) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    Objects.requireNonNull(request, "Chat request must not be null");
    OpenAiAdapterFactory.OpenAiInvocation invocation = null;
    try {
      invocation = adapterFactory.createInvocation(modelDescriptor, request.getInvocationContext());
      var options = toOptions(modelDescriptor, request, invocation);
      var prompt = new Prompt(OpenAiChatMessageMapper.toMessages(request.getMessageList()), options);
      var response = adapterFactory.createChatModel(options).call(prompt);
      return toChatResponse(modelDescriptor, response);
    } catch (AiPolicyViolationException exception) {
      throw exception;
    } catch (AiProviderProtocolException exception) {
      throw exception;
    } catch (AiInvocationException exception) {
      throw exception;
    } catch (OpenAIServiceException exception) {
      throw new AiProviderProtocolException("OpenAI chat provider error for model: " + modelDescriptor.getModelId()
          + " HTTP status " + exception.statusCode() + ": "
          + sanitize(openAiBody(exception), apiKey(invocation)), exception);
    } catch (OpenAIIoException exception) {
      throw new AiProviderProtocolException("OpenAI chat invocation failed for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (OpenAIInvalidDataException exception) {
      throw new AiProviderProtocolException("Failed to map OpenAI chat response for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (OpenAIException exception) {
      throw new AiProviderProtocolException("OpenAI chat invocation failed for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (RuntimeException exception) {
      throw new AiProviderProtocolException("Failed to map OpenAI chat response for model: " + modelDescriptor.getModelId(),
          exception);
    }
  }

  private OpenAiChatOptions toOptions(AiModelDescriptor modelDescriptor,
                                      ChatRequest request,
                                      OpenAiAdapterFactory.OpenAiInvocation invocation) {
    var options = mergeOptions(modelDescriptor, request.getOptions());
    var providerOptions = mergeProviderOptions(modelDescriptor, options);
    rejectUnknownProviderOptions(providerOptions);
    var extraBody = new LinkedHashMap<String, Object>();
    putIfPresent(extraBody, "prompt_cache_retention", stringOption(providerOptions, "promptCacheRetention"));
    putIfPresent(extraBody, "safety_identifier", stringOption(providerOptions, "safetyIdentifier"));
    var tools = toTools(request);
    if (!tools.isEmpty()) {
      extraBody.put("tools", tools);
      putIfPresent(extraBody, "tool_choice", toolChoice(options));
    }
    return OpenAiChatOptions.builder()
        .baseUrl(invocation.getBaseUrl())
        .apiKey(invocation.getApiKey())
        .model(providerModelName(modelDescriptor, request.getModelId()))
        .temperature(options == null ? null : options.getTemperature())
        .topP(options == null ? null : options.getTopP())
        .maxTokens(options == null ? null : options.getMaxTokens())
        .stop(stop(options))
        .serviceTier(stringOption(providerOptions, "serviceTier"))
        .promptCacheKey(stringOption(providerOptions, "promptCacheKey"))
        .extraBody(extraBody.isEmpty() ? null : extraBody)
        .build();
  }

  private List<Map<String, Object>> toTools(ChatRequest request) {
    List<Map<String, Object>> tools = new ArrayList<>();
    for (String toolName : request.getToolNames()) {
      toolRegistry.get(toolName).ifPresent(toolDescriptor -> {
        if (!toolDescriptor.isAllowModelInvocation()) {
          throw new AiPolicyViolationException("AI model invocation is not allowed for tool: " + toolDescriptor.getToolName());
        }
        tools.add(toFunctionTool(toolDescriptor));
      });
    }
    return tools;
  }

  private static Map<String, Object> toFunctionTool(AiToolDescriptor toolDescriptor) {
    return mapOf("type", "function",
        "function", mapOf("description", toolDescriptor.getDescription(),
            "name", toolDescriptor.getToolName(),
            "parameters", toolDescriptor.getInputSchema().isEmpty() ? null : toolDescriptor.getInputSchema(),
            "strict", true));
  }

  private static ChatResponse toChatResponse(AiModelDescriptor modelDescriptor,
                                             org.springframework.ai.chat.model.ChatResponse response) {
    if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
      throw new IllegalStateException("OpenAI chat response is empty");
    }
    var message = response.getResult().getOutput();
    return ChatResponse.builder()
        .modelId(modelDescriptor.getModelId())
        .text(message.getText())
        .content(mapContent(message.getText()))
        .toolCalls(mapToolCalls(message.getToolCalls()))
        .usage(mapUsage(response.getMetadata() == null ? null : response.getMetadata().getUsage()))
        .build();
  }

  private static List<Map<String, Object>> mapContent(String text) {
    if (text == null) {
      return Collections.emptyList();
    }
    return List.of(mapOf("type", "output_text", "text", text));
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
      putIfPresent(toolCall, "arguments", call.arguments());
      mapped.add(toolCall);
    }
    return mapped;
  }

  private static Map<String, Object> mapUsage(org.springframework.ai.chat.metadata.Usage usage) {
    if (usage == null) {
      return Collections.emptyMap();
    }
    return mapOf("inputTokens", usage.getPromptTokens(),
        "outputTokens", usage.getCompletionTokens(),
        "totalTokens", usage.getTotalTokens());
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
        throw new AiProviderProtocolException("OpenAI provider option is not supported: " + entry.getKey());
      }
    }
  }

  private static String providerModelName(AiModelDescriptor modelDescriptor, String fallbackModelId) {
    return hasText(modelDescriptor.getModelName()) ? modelDescriptor.getModelName() : fallbackModelId;
  }

  private static List<String> stop(AiOptions options) {
    return options == null || options.getStop().isEmpty() ? null : options.getStop();
  }

  private static Object toolChoice(AiOptions options) {
    if (options == null || !hasText(options.getToolChoice())) {
      return null;
    }
    var normalized = options.getToolChoice().trim();
    if ("auto".equalsIgnoreCase(normalized) || "none".equalsIgnoreCase(normalized)
        || "required".equalsIgnoreCase(normalized)) {
      return normalized.toLowerCase(java.util.Locale.ROOT);
    }
    if ("any".equalsIgnoreCase(normalized)) {
      return "required";
    }
    return mapOf("type", "function", "function", mapOf("name", normalized));
  }

  private static String stringOption(Map<String, Object> options, String key) {
    var value = options.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static String apiKey(OpenAiAdapterFactory.OpenAiInvocation invocation) {
    return invocation == null ? null : invocation.getApiKey();
  }

  private static String openAiBody(OpenAIServiceException exception) {
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
