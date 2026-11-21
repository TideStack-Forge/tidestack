package com.ouroboros.ai.openai;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;

public class OpenAiAdapterFactory {
  private final Function<String, String> credentialResolver;
  private final AiProviderConnectionResolver providerConnectionResolver;

  public OpenAiAdapterFactory(Function<String, String> credentialResolver,
                              AiProviderConnectionResolver providerConnectionResolver) {
    this.credentialResolver = credentialResolver == null ? Function.identity() : credentialResolver;
    this.providerConnectionResolver = Objects.requireNonNull(providerConnectionResolver,
        "AI provider connection resolver must not be null");
  }

  public OpenAiInvocation createInvocation(AiModelDescriptor modelDescriptor) {
    return createInvocation(modelDescriptor, null);
  }

  public OpenAiInvocation createInvocation(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    var connection = resolveConnection(modelDescriptor, invocationContext);
    var apiKey = resolveApiKey(connection);
    var baseUrl = normalizeBaseUrl(connection.getEndpointRef());
    return new OpenAiInvocation(baseUrl, apiKey);
  }

  protected OpenAiChatModel createChatModel(OpenAiChatOptions options) {
    return OpenAiChatModel.builder().options(options).build();
  }

  protected OpenAiEmbeddingModel createEmbeddingModel(OpenAiEmbeddingOptions options) {
    return OpenAiEmbeddingModel.builder().options(options).build();
  }

  protected String resolveApiKey(AiProviderConnectionDescriptor connection) {
    var providerInstanceId = connection.getProviderInstanceId();
    var credentialsRef = normalize(connection.getCredentialsRef());
    if (credentialsRef != null) {
      try {
        var resolved = normalize(credentialResolver.apply(credentialsRef));
        if (resolved != null) {
          return resolved;
        }
      } catch (RuntimeException exception) {
        throw new AiInvocationException("Failed to resolve OpenAI credentials for provider connection: " + providerInstanceId,
            exception);
      }
    }
    var connectionApiKey = connection.getConnectionOptions().get("apiKey");
    if (connectionApiKey != null) {
      var resolved = normalize(String.valueOf(connectionApiKey));
      if (resolved != null) {
        return resolved;
      }
    }
    throw new AiInvocationException("Missing OpenAI credentials for provider connection: " + providerInstanceId);
  }

  private AiProviderConnectionDescriptor resolveConnection(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
    return providerConnectionResolver.resolve(modelDescriptor, toResolutionScope(invocationContext))
        .orElseThrow(() -> new AiInvocationException("Missing OpenAI provider connection for model: " + modelDescriptor.getModelId()));
  }

  private static AiResolutionScope toResolutionScope(AiInvocationContext invocationContext) {
    return invocationContext == null ? AiResolutionScope.builder().build() : invocationContext.toResolutionScope();
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    var normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static String normalizeBaseUrl(String value) {
    var normalized = normalize(value);
    if (normalized == null) {
      return normalized;
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.endsWith("/v1")) {
      return normalized;
    }
    return normalized + "/v1";
  }

  static final class OpenAiInvocation {
    private final String baseUrl;
    private final String apiKey;

    private OpenAiInvocation(String baseUrl, String apiKey) {
      this.baseUrl = baseUrl;
      this.apiKey = apiKey;
    }

    String getBaseUrl() {
      return baseUrl;
    }

    String getApiKey() {
      return apiKey;
    }
  }
}
