package com.ouroboros.ai.runtime;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.ai.runtime.pipeline.AiInvocationPipeline;
import com.ouroboros.ai.runtime.pipeline.AiInvocationStage;
import com.ouroboros.ai.runtime.provider.AiModelDescriptorProvider;
import com.ouroboros.ai.runtime.provider.AiPolicyDescriptorProvider;
import com.ouroboros.ai.runtime.provider.AiProviderConnectionDescriptorProvider;
import com.ouroboros.ai.runtime.provider.AiToolDescriptorProvider;
import com.ouroboros.ai.runtime.resolution.AiPolicyResolver;
import com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver;
import com.ouroboros.ai.runtime.resolution.AiRuntimeSnapshotResolver;
import com.ouroboros.ai.runtime.resolution.DefaultAiPolicyResolver;
import com.ouroboros.ai.runtime.resolution.DefaultAiProviderConnectionResolver;
import com.ouroboros.ai.runtime.resolution.DefaultAiRuntimeSnapshotResolver;
import com.ouroboros.ai.runtime.registry.AiModelRegistry;
import com.ouroboros.ai.runtime.registry.AiPolicyRegistry;
import com.ouroboros.ai.runtime.registry.AiProviderConnectionRegistry;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;
import com.ouroboros.ai.runtime.registry.DefaultAiModelRegistry;
import com.ouroboros.ai.runtime.registry.DefaultAiPolicyRegistry;
import com.ouroboros.ai.runtime.registry.DefaultAiProviderConnectionRegistry;
import com.ouroboros.ai.runtime.registry.DefaultAiToolRegistry;
import com.ouroboros.ai.runtime.service.AiInvocationService;
import com.ouroboros.ai.runtime.service.DefaultAiInvocationService;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

@Configuration
public class AiRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AiModelRegistry.class)
  public AiModelRegistry aiModelRegistry(List<AiModelDescriptorProvider> providers) {
    return new DefaultAiModelRegistry(providers);
  }

  @Bean
  @ConditionalOnMissingBean(AiToolRegistry.class)
  public AiToolRegistry aiToolRegistry(List<AiToolDescriptorProvider> providers) {
    return new DefaultAiToolRegistry(providers);
  }

  @Bean
  @ConditionalOnMissingBean(AiProviderConnectionRegistry.class)
  public AiProviderConnectionRegistry aiProviderConnectionRegistry(List<AiProviderConnectionDescriptorProvider> providers) {
    return new DefaultAiProviderConnectionRegistry(providers);
  }

  @Bean
  @ConditionalOnMissingBean(AiPolicyRegistry.class)
  public AiPolicyRegistry aiPolicyRegistry(List<AiPolicyDescriptorProvider> providers) {
    return new DefaultAiPolicyRegistry(providers);
  }

  @Bean
  @ConditionalOnMissingBean(AiProviderConnectionResolver.class)
  public AiProviderConnectionResolver aiProviderConnectionResolver(AiProviderConnectionRegistry aiProviderConnectionRegistry) {
    return new DefaultAiProviderConnectionResolver(aiProviderConnectionRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(AiPolicyResolver.class)
  public AiPolicyResolver aiPolicyResolver(AiPolicyRegistry aiPolicyRegistry) {
    return new DefaultAiPolicyResolver(aiPolicyRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(AiRuntimeSnapshotResolver.class)
  public AiRuntimeSnapshotResolver aiRuntimeSnapshotResolver(AiModelRegistry aiModelRegistry,
                                                             AiPolicyResolver aiPolicyResolver) {
    return new DefaultAiRuntimeSnapshotResolver(aiModelRegistry, aiPolicyResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiInvocationPipeline aiInvocationPipeline(List<AiInvocationStage> stages) {
    return new AiInvocationPipeline(stages);
  }

  @Bean
  @ConditionalOnMissingBean(AiInvocationService.class)
  public AiInvocationService aiInvocationService(AiRuntimeSnapshotResolver aiRuntimeSnapshotResolver,
                                                 AiInvocationPipeline aiInvocationPipeline,
                                                 List<AiChatAdapter> chatAdapters,
                                                 List<AiEmbeddingAdapter> embeddingAdapters) {
    return new DefaultAiInvocationService(aiRuntimeSnapshotResolver, aiInvocationPipeline, chatAdapters, embeddingAdapters);
  }
}
