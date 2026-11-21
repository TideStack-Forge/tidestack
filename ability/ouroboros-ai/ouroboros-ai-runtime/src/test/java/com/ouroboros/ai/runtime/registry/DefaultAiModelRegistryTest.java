package com.ouroboros.ai.runtime.registry;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.runtime.provider.AiModelDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAiModelRegistryTest {

  @Test
  void modelRegistry_prefersFirstDescriptorForCaseInsensitiveModelId() {
    AiModelDescriptorProvider provider = () -> Arrays.asList(
        AiModelDescriptor.builder()
            .modelId("gpt-4o")
            .providerFamily(AiProviderFamily.OPENAI)
            .build(),
        AiModelDescriptor.builder()
            .modelId("GPT-4O")
            .providerFamily(AiProviderFamily.OPENAI)
            .build()
    );

    var registry = new DefaultAiModelRegistry(Collections.singletonList(provider));

    assertEquals("gpt-4o", registry.get("GPT-4o").get().getModelId());
  }
}
