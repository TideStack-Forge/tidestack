package com.ouroboros.ai.openai;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

@Configuration
public class OpenAiRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(OpenAiAdapterFactory.class)
  public OpenAiAdapterFactory openAiAdapterFactory(AiProviderConnectionResolver aiProviderConnectionResolver) {
    return new OpenAiAdapterFactory(Function.identity(), aiProviderConnectionResolver);
  }

  @Bean
  @ConditionalOnMissingBean(OpenAiChatAdapter.class)
  public AiChatAdapter openAiChatAdapter(OpenAiAdapterFactory openAiAdapterFactory, AiToolRegistry aiToolRegistry) {
    return new OpenAiChatAdapter(openAiAdapterFactory, aiToolRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(OpenAiEmbeddingAdapter.class)
  public AiEmbeddingAdapter openAiEmbeddingAdapter(OpenAiAdapterFactory openAiAdapterFactory) {
    return new OpenAiEmbeddingAdapter(openAiAdapterFactory);
  }
}
