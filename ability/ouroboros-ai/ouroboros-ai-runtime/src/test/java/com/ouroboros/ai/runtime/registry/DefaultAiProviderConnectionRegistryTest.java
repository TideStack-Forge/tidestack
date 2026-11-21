package com.ouroboros.ai.runtime.registry;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.runtime.provider.AiProviderConnectionDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAiProviderConnectionRegistryTest {

  @Test
  void providerConnectionRegistry_prefersLowerOrderProviderAndNormalizesKeys() {
    var registry = new DefaultAiProviderConnectionRegistry(Arrays.asList(highOrderProvider(), lowOrderProvider()));

    assertEquals("openai-prod", registry.get(" OPENAI-PROD ").get().getProviderInstanceId());
    assertEquals(1, registry.getAll().size());
  }

  private static AiProviderConnectionDescriptorProvider highOrderProvider() {
    return new AiProviderConnectionDescriptorProvider() {
      @Override
      public java.util.List<AiProviderConnectionDescriptor> getProviderConnectionDescriptors() {
        return Collections.singletonList(
            AiProviderConnectionDescriptor.builder()
                .providerInstanceId("OPENAI-PROD")
                .providerFamily(AiProviderFamily.OPENAI)
                .endpointRef("https://fallback.internal/v1")
                .credentialsRef("fallback-key")
                .build()
        );
      }

      @Override
      public int getOrder() {
        return 100;
      }
    };
  }

  private static AiProviderConnectionDescriptorProvider lowOrderProvider() {
    return new AiProviderConnectionDescriptorProvider() {
      @Override
      public java.util.List<AiProviderConnectionDescriptor> getProviderConnectionDescriptors() {
        return Collections.singletonList(
            AiProviderConnectionDescriptor.builder()
                .providerInstanceId("openai-prod")
                .providerFamily(AiProviderFamily.OPENAI)
                .endpointRef("https://gateway.internal/v1")
                .credentialsRef("gateway-key")
                .build()
        );
      }

      @Override
      public int getOrder() {
        return 10;
      }
    };
  }
}
