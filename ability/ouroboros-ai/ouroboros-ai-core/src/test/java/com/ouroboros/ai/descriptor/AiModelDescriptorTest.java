package com.ouroboros.ai.descriptor;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiCapability;
import com.ouroboros.ai.AiProviderFamily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelDescriptorTest {

  @Test
  void modelDescriptor_keepsProviderFamilyCapabilitiesAndProviderOptions() {
    var descriptor = AiModelDescriptor.builder()
        .modelId("claude-sonnet")
        .providerFamily(AiProviderFamily.ANTHROPIC)
        .providerInstanceId("anthropic-prod")
        .modelName("claude-3-7-sonnet")
        .capability(AiCapability.CHAT)
        .capability(AiCapability.TOOL_CALLING)
        .providerOption("anthropicVersion", "2023-06-01")
        .build();

    assertEquals(AiProviderFamily.ANTHROPIC, descriptor.getProviderFamily());
    assertEquals("anthropic-prod", descriptor.getProviderInstanceId());
    assertEquals("claude-3-7-sonnet", descriptor.getModelName());
    assertTrue(descriptor.getCapabilities().contains(AiCapability.TOOL_CALLING));
    assertEquals("2023-06-01", descriptor.getProviderOptions().get("anthropicVersion"));
  }
}
