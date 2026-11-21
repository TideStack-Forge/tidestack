package com.ouroboros.ai.openai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiAdapterFactoryTest {

  @Test
  void openAiFactory_prefersResolvedProviderConnectionAndForwardsInvocationScopeOnce() {
    var resolver = new RecordingConnectionResolver(connection("https://gateway.internal/v1", "gateway-secret"));
    var factory = new OpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);

    var invocation = factory.createInvocation(modelDescriptor(), AiInvocationContext.builder()
        .tenantId("tenant-a")
        .appName("develop")
        .environment("prod")
        .workspace("ws-dev")
        .userId("user-1")
        .build());

    assertEquals("resolved-gateway-secret", invocation.getApiKey());
    assertEquals("https://gateway.internal/v1", invocation.getBaseUrl());
    assertEquals(1, resolver.getResolveCount());
    assertEquals("tenant-a", resolver.getLastScope().getTenantId());
    assertEquals("develop", resolver.getLastScope().getAppName());
    assertEquals("prod", resolver.getLastScope().getEnvironment());
    assertEquals("ws-dev", resolver.getLastScope().getWorkspace());
    assertEquals("user-1", resolver.getLastScope().getUserId());
  }

  @Test
  void openAiFactory_usesEmptyScopeForNullContextAndConnectionOptionsApiKey() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://gateway.internal/v1", null,
        mapOf("apiKey", "connection-api-key")));
    var factory = new OpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);

    var invocation = factory.createInvocation(modelDescriptor());

    assertEquals("connection-api-key", invocation.getApiKey());
    assertEquals("https://gateway.internal/v1", invocation.getBaseUrl());
    assertEquals(1, resolver.getResolveCount());
    assertNull(resolver.getLastScope().getTenantId());
    assertNull(resolver.getLastScope().getAppName());
    assertNull(resolver.getLastScope().getEnvironment());
    assertNull(resolver.getLastScope().getWorkspace());
    assertNull(resolver.getLastScope().getUserId());
  }

  @Test
  void openAiFactory_doesNotDuplicateVersionPathWhenEndpointHasTrailingSlash() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://gateway.internal/v1/", null,
        mapOf("apiKey", "connection-api-key")));
    var factory = new OpenAiAdapterFactory(Function.identity(), resolver);

    var invocation = factory.createInvocation(modelDescriptor());

    assertEquals("https://gateway.internal/v1", invocation.getBaseUrl());
  }

  @Test
  void openAiFactory_doesNotCacheApiInstancesAcrossCredentialRotation() {
    var resolver = new RecordingConnectionResolver(
        connectionWithOptions("https://gateway.internal/v1", null, mapOf("apiKey", "first-key")),
        connectionWithOptions("https://gateway.internal/v1", null, mapOf("apiKey", "second-key")));
    var factory = new OpenAiAdapterFactory(Function.identity(), resolver);

    var first = factory.createInvocation(modelDescriptor());
    var second = factory.createInvocation(modelDescriptor());

    assertEquals("first-key", first.getApiKey());
    assertEquals("second-key", second.getApiKey());
    assertEquals(2, resolver.getResolveCount());
  }

  @Test
  void openAiFactory_fallsBackToConnectionOptionsApiKeyWhenResolvedCredentialIsBlank() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://gateway.internal/v1", "gateway-secret",
        mapOf("apiKey", "connection-api-key")));
    var factory = new OpenAiAdapterFactory(ref -> " ", resolver);

    var invocation = factory.createInvocation(modelDescriptor());

    assertEquals("connection-api-key", invocation.getApiKey());
  }

  @Test
  void openAiFactory_throwsCanonicalErrorWhenProviderConnectionIsMissing() {
    var factory = new OpenAiAdapterFactory(Function.identity(), new RecordingConnectionResolver());

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptor()));

    assertEquals("Missing OpenAI provider connection for model: openai-chat", exception.getMessage());
  }

  @Test
  void openAiFactory_wrapsCredentialResolverFailureWithProviderConnectionId() {
    var cause = new IllegalStateException("secret backend unavailable");
    var factory = new OpenAiAdapterFactory(ref -> {
      throw cause;
    }, new RecordingConnectionResolver(connection("https://gateway.internal/v1", "gateway-secret")));

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptor()));

    assertEquals("Failed to resolve OpenAI credentials for provider connection: openai-prod", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void openAiFactory_ignoresModelProviderOptionsApiKeyWhenConnectionHasNoCredentials() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://gateway.internal/v1", null, null));
    var factory = new OpenAiAdapterFactory(Function.identity(), resolver);

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptorWithProviderApiKey()));

    assertEquals("Missing OpenAI credentials for provider connection: openai-prod", exception.getMessage());
  }

  private static AiModelDescriptor modelDescriptor() {
    return AiModelDescriptor.builder()
        .modelId("openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .build();
  }

  private static AiModelDescriptor modelDescriptorWithProviderApiKey() {
    return AiModelDescriptor.builder()
        .modelId("openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .providerOption("apiKey", "model-provider-api-key")
        .build();
  }

  private static AiProviderConnectionDescriptor connection(String endpoint, String credentialsRef) {
    return connectionWithOptions(endpoint, credentialsRef, null);
  }

  private static AiProviderConnectionDescriptor connectionWithOptions(String endpoint, String credentialsRef,
                                                                      Map<String, Object> connectionOptions) {
    return AiProviderConnectionDescriptor.builder()
        .providerInstanceId("openai-prod")
        .providerFamily(AiProviderFamily.OPENAI)
        .endpointRef(endpoint)
        .credentialsRef(credentialsRef)
        .connectionOptions(connectionOptions)
        .build();
  }

  private static Map<String, Object> mapOf(Object... values) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return map;
  }

  private static final class RecordingConnectionResolver implements AiProviderConnectionResolver {
    private final java.util.Queue<AiProviderConnectionDescriptor> connections = new java.util.ArrayDeque<>();
    private int resolveCount;
    private AiResolutionScope lastScope;

    private RecordingConnectionResolver(AiProviderConnectionDescriptor... connections) {
      java.util.Collections.addAll(this.connections, connections);
    }

    @Override
    public Optional<AiProviderConnectionDescriptor> resolve(AiModelDescriptor modelDescriptor, AiResolutionScope scope) {
      this.resolveCount++;
      this.lastScope = scope;
      return Optional.ofNullable(connections.poll());
    }

    private int getResolveCount() {
      return resolveCount;
    }

    private AiResolutionScope getLastScope() {
      return lastScope;
    }
  }

}
