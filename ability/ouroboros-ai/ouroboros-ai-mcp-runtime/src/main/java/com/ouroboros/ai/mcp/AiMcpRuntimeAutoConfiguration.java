package com.ouroboros.ai.mcp;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.ai.runtime.provider.AiMcpEndpointDescriptorProvider;
import com.ouroboros.ai.runtime.provider.AiToolDescriptorProvider;

@Configuration
public class AiMcpRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AiMcpClientRegistry.class)
  public AiMcpClientRegistry aiMcpClientRegistry(List<AiMcpEndpointDescriptorProvider> endpointProviders) {
    return new AiMcpClientRegistry(endpointProviders == null ? Collections.emptyList() : endpointProviders);
  }

  @Bean
  @ConditionalOnMissingBean(AiMcpBridgeService.class)
  public AiMcpBridgeService aiMcpBridgeService(AiMcpClientRegistry aiMcpClientRegistry) {
    return new AiMcpBridgeService(aiMcpClientRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(AiMcpToolDescriptorProvider.class)
  public AiToolDescriptorProvider aiMcpToolDescriptorProvider(AiMcpBridgeService aiMcpBridgeService) {
    return new AiMcpToolDescriptorProvider(aiMcpBridgeService);
  }
}
