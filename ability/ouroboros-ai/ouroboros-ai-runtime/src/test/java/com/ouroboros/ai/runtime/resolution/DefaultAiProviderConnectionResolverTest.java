package com.ouroboros.ai.runtime.resolution;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.runtime.registry.AiProviderConnectionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultAiProviderConnectionResolverTest {

  @Test
  void providerConnectionResolver_returnsRegistryDescriptorForProviderInstanceId() {
    var connection = connection("openai-prod");
    var resolver = new DefaultAiProviderConnectionResolver(new SingleConnectionRegistry(connection));

    var resolved = resolver.resolve(model("openai-prod"), AiResolutionScope.builder().build());

    assertSame(connection, resolved.get());
  }

  @Test
  void providerConnectionResolver_returnsEmptyForUnknownProviderInstanceId() {
    var resolver = new DefaultAiProviderConnectionResolver(new SingleConnectionRegistry(connection("openai-prod")));

    var resolved = resolver.resolve(model("openai-missing"), AiResolutionScope.builder().build());

    assertFalse(resolved.isPresent());
  }

  @Test
  void providerConnectionResolver_returnsEmptyWhenModelHasNoProviderInstanceId() {
    var resolver = new DefaultAiProviderConnectionResolver(new SingleConnectionRegistry(connection("openai-prod")));

    var resolved = resolver.resolve(model(" "), AiResolutionScope.builder().build());

    assertFalse(resolved.isPresent());
  }

  private static AiModelDescriptor model(String providerInstanceId) {
    return AiModelDescriptor.builder()
        .modelId("openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId(providerInstanceId)
        .build();
  }

  private static AiProviderConnectionDescriptor connection(String providerInstanceId) {
    return AiProviderConnectionDescriptor.builder()
        .providerInstanceId(providerInstanceId)
        .providerFamily(AiProviderFamily.OPENAI)
        .endpointRef("https://gateway.internal/v1")
        .credentialsRef("gateway-secret")
        .build();
  }

  private static final class SingleConnectionRegistry implements AiProviderConnectionRegistry {
    private final AiProviderConnectionDescriptor connection;

    private SingleConnectionRegistry(AiProviderConnectionDescriptor connection) {
      this.connection = connection;
    }

    @Override
    public Optional<AiProviderConnectionDescriptor> get(String providerInstanceId) {
      return connection.getProviderInstanceId().equals(providerInstanceId) ? Optional.of(connection) : Optional.empty();
    }

    @Override
    public List<AiProviderConnectionDescriptor> getAll() {
      return Collections.singletonList(connection);
    }
  }
}
