package com.ouroboros.ai.rag;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.ai.runtime.provider.AiKnowledgeBaseDescriptorProvider;

@Configuration
public class AiRagRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AiKnowledgeBaseRegistry.class)
  public AiKnowledgeBaseRegistry aiKnowledgeBaseRegistry(List<AiKnowledgeBaseDescriptorProvider> providers) {
    return new AiKnowledgeBaseRegistry(providers == null ? Collections.emptyList() : providers);
  }

  @Bean
  @ConditionalOnMissingBean(AiRetriever.class)
  public AiRetriever aiRetriever(AiKnowledgeBaseRegistry registry, Map<String, VectorStore> vectorStores) {
    var fallback = new InMemoryAiRetriever(Collections.emptyMap());
    return vectorStores == null || vectorStores.isEmpty()
        ? fallback
        : new SpringAiVectorStoreRetriever(registry, vectorStores, fallback);
  }
}
