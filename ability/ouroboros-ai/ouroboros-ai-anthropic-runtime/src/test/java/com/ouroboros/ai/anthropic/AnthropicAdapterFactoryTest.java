package com.ouroboros.ai.anthropic;

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

class AnthropicAdapterFactoryTest {

  @Test
  void anthropicFactory_prefersResolvedProviderConnectionAndForwardsInvocationScopeOnce() {
    var resolver = new RecordingConnectionResolver(connection("https://anthropic-gateway.internal", "gateway-secret"));
    var factory = new AnthropicAdapterFactory(ref -> "resolved-" + ref, resolver);

    var invocation = factory.createInvocation(modelDescriptor(), AiInvocationContext.builder()
        .tenantId("tenant-a")
        .appName("develop")
        .environment("prod")
        .workspace("ws-dev")
        .userId("user-1")
        .build());

    assertEquals("https://anthropic-gateway.internal", invocation.getBaseUrl());
    assertEquals("resolved-gateway-secret", invocation.getApiKey());
    assertEquals(1, resolver.getResolveCount());
    assertEquals("tenant-a", resolver.getLastScope().getTenantId());
    assertEquals("develop", resolver.getLastScope().getAppName());
    assertEquals("prod", resolver.getLastScope().getEnvironment());
    assertEquals("ws-dev", resolver.getLastScope().getWorkspace());
    assertEquals("user-1", resolver.getLastScope().getUserId());
  }

  @Test
  void anthropicFactory_usesEmptyScopeForNullContextAndConnectionOptionsApiKey() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://anthropic-gateway.internal", null,
        AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-api-key")));
    var factory = new AnthropicAdapterFactory(ref -> "resolved-" + ref, resolver);

    var invocation = factory.createInvocation(modelDescriptor());

    assertEquals("https://anthropic-gateway.internal", invocation.getBaseUrl());
    assertEquals("connection-api-key", invocation.getApiKey());
    assertEquals(1, resolver.getResolveCount());
    assertNull(resolver.getLastScope().getTenantId());
    assertNull(resolver.getLastScope().getAppName());
    assertNull(resolver.getLastScope().getEnvironment());
    assertNull(resolver.getLastScope().getWorkspace());
    assertNull(resolver.getLastScope().getUserId());
  }

  @Test
  void anthropicFactory_fallsBackToConnectionOptionsApiKeyWhenResolvedCredentialIsBlank() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://anthropic-gateway.internal", "gateway-secret",
        AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-api-key")));
    var factory = new AnthropicAdapterFactory(ref -> " ", resolver);

    var invocation = factory.createInvocation(modelDescriptor());

    assertEquals("connection-api-key", invocation.getApiKey());
  }

  @Test
  void anthropicFactory_doesNotCacheCredentialOrEndpointResolution() {
    var resolver = new RecordingConnectionResolver(
        connection("https://anthropic-a.internal", "secret-a"),
        connection("https://anthropic-b.internal", "secret-b"));
    var factory = new AnthropicAdapterFactory(ref -> "resolved-" + ref, resolver);

    var first = factory.createInvocation(modelDescriptor());
    var second = factory.createInvocation(modelDescriptor());

    assertEquals("https://anthropic-a.internal", first.getBaseUrl());
    assertEquals("resolved-secret-a", first.getApiKey());
    assertEquals("https://anthropic-b.internal", second.getBaseUrl());
    assertEquals("resolved-secret-b", second.getApiKey());
    assertEquals(2, resolver.getResolveCount());
  }

  @Test
  void anthropicFactory_throwsCanonicalErrorWhenProviderConnectionIsMissing() {
    var factory = new AnthropicAdapterFactory(Function.identity(), new RecordingConnectionResolver());

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptor()));

    assertEquals("Missing Anthropic provider connection for model: anthropic-chat", exception.getMessage());
  }

  @Test
  void anthropicFactory_wrapsCredentialResolverFailureWithProviderConnectionId() {
    var cause = new IllegalStateException("secret backend unavailable");
    var factory = new AnthropicAdapterFactory(ref -> {
      throw cause;
    }, new RecordingConnectionResolver(connection("https://anthropic-gateway.internal", "gateway-secret")));

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptor()));

    assertEquals("Failed to resolve Anthropic credentials for provider connection: anthropic-prod", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void anthropicFactory_ignoresModelProviderOptionsApiKeyWhenConnectionHasNoCredentials() {
    var resolver = new RecordingConnectionResolver(connectionWithOptions("https://anthropic-gateway.internal", null, null));
    var factory = new AnthropicAdapterFactory(Function.identity(), resolver);

    var exception = assertThrows(AiInvocationException.class, () -> factory.createInvocation(modelDescriptorWithProviderApiKey()));

    assertEquals("Missing Anthropic credentials for provider connection: anthropic-prod", exception.getMessage());
  }

  private static AiModelDescriptor modelDescriptor() {
    return AiModelDescriptor.builder()
        .modelId("anthropic-chat")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .providerInstanceId("anthropic-prod")
        .build();
  }

  private static AiModelDescriptor modelDescriptorWithProviderApiKey() {
    return AiModelDescriptor.builder()
        .modelId("anthropic-chat")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .providerInstanceId("anthropic-prod")
        .providerOption("apiKey", "model-provider-api-key")
        .build();
  }

  private static AiProviderConnectionDescriptor connection(String endpoint, String credentialsRef) {
    return connectionWithOptions(endpoint, credentialsRef, null);
  }

  private static AiProviderConnectionDescriptor connectionWithOptions(String endpoint, String credentialsRef,
                                                                      java.util.Map<String, Object> connectionOptions) {
    return AiProviderConnectionDescriptor.builder()
        .providerInstanceId("anthropic-prod")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .endpointRef(endpoint)
        .credentialsRef(credentialsRef)
        .connectionOptions(connectionOptions)
        .build();
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
