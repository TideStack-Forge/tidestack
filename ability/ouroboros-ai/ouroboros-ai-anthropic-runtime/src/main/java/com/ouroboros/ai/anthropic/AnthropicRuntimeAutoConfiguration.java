package com.ouroboros.ai.anthropic;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;

@Configuration
public class AnthropicRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AnthropicAdapterFactory.class)
  public AnthropicAdapterFactory anthropicAdapterFactory(AiProviderConnectionResolver aiProviderConnectionResolver) {
    return new AnthropicAdapterFactory(Function.identity(), aiProviderConnectionResolver);
  }

  @Bean
  @ConditionalOnMissingBean(AnthropicChatAdapter.class)
  public AiChatAdapter anthropicChatAdapter(AnthropicAdapterFactory anthropicAdapterFactory, AiToolRegistry aiToolRegistry) {
    return new AnthropicChatAdapter(anthropicAdapterFactory, aiToolRegistry);
  }
}
