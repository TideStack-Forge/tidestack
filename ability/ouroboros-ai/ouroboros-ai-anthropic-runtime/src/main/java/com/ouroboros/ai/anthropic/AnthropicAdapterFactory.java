package com.ouroboros.ai.anthropic;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;

public class AnthropicAdapterFactory {
  private final Function<String, String> credentialResolver;
  private final AiProviderConnectionResolver providerConnectionResolver;

  public AnthropicAdapterFactory(Function<String, String> credentialResolver,
                                 AiProviderConnectionResolver providerConnectionResolver) {
    this.credentialResolver = credentialResolver == null ? Function.identity() : credentialResolver;
    this.providerConnectionResolver = Objects.requireNonNull(providerConnectionResolver,
        "AI provider connection resolver must not be null");
  }

  public AnthropicInvocation createInvocation(AiModelDescriptor modelDescriptor) {
    return createInvocation(modelDescriptor, null);
  }

  public AnthropicInvocation createInvocation(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    var connection = resolveConnection(modelDescriptor, invocationContext);
    var apiKey = resolveApiKey(connection);
    return new AnthropicInvocation(normalize(connection.getEndpointRef()), apiKey);
  }

  protected AnthropicChatModel createChatModel(AnthropicChatOptions options) {
    return AnthropicChatModel.builder().options(options).build();
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
        throw new AiInvocationException("Failed to resolve Anthropic credentials for provider connection: " + providerInstanceId,
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
    throw new AiInvocationException("Missing Anthropic credentials for provider connection: " + providerInstanceId);
  }

  private AiProviderConnectionDescriptor resolveConnection(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
    return providerConnectionResolver.resolve(modelDescriptor, toResolutionScope(invocationContext))
        .orElseThrow(() -> new AiInvocationException("Missing Anthropic provider connection for model: " + modelDescriptor.getModelId()));
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

  static final class AnthropicInvocation {
    private final String baseUrl;
    private final String apiKey;

    private AnthropicInvocation(String baseUrl, String apiKey) {
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
