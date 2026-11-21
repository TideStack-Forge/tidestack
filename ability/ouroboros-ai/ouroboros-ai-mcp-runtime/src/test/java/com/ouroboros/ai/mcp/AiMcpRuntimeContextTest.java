package com.ouroboros.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMcpRuntimeContextTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(AiMcpRuntimeAutoConfiguration.class);

  @Test
  void explicitMcpRuntimeContextWiresRegistryBridgeAndToolProvider() {
    contextRunner.run(context -> {
      assertTrue(context.containsBean("aiMcpClientRegistry"));
      assertTrue(context.containsBean("aiMcpBridgeService"));
      assertTrue(context.containsBean("aiMcpToolDescriptorProvider"));
    });
  }
}
