package com.ouroboros.ai.options;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiOptionsTest {

  @Test
  void aiOptions_mergeKeepsPortableFieldsAndPreservesProviderOptions() {
    var merged = AiOptions.builder()
        .temperature(0.2d)
        .maxTokens(1024)
        .providerOption("reasoning", "medium")
        .build()
        .merge(AiOptions.builder()
            .topP(0.9d)
            .providerOption("serviceTier", "default")
            .build());

    assertEquals(0.2d, merged.getTemperature());
    assertEquals(Integer.valueOf(1024), merged.getMaxTokens());
    assertEquals("medium", merged.getProviderOptions().get("reasoning"));
    assertEquals("default", merged.getProviderOptions().get("serviceTier"));
  }
}
