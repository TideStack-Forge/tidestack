package com.ouroboros.ai.rag;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;
import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.runtime.provider.AiKnowledgeBaseDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAiRetrieverTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(AiRagRuntimeAutoConfiguration.class);

  @Test
  void inMemoryRetriever_returnsConfiguredSnippetsInStableOrder() {
    var retriever = new InMemoryAiRetriever(Collections.singletonMap(
        "kb-docs", Arrays.asList("chunk-1", "chunk-2")
    ));

    var result = retriever.retrieve(TestRequests.retriever("kb-docs", "login code"));

    assertEquals(Arrays.asList("chunk-1", "chunk-2"), result.getSnippets());
  }

  @Test
  void knowledgeBaseRegistry_prefersFirstEnabledDescriptorForCaseInsensitiveKnowledgeBaseId() {
    AiKnowledgeBaseDescriptorProvider provider = () -> Arrays.asList(
        AiKnowledgeBaseDescriptor.builder()
            .knowledgeBaseId("kb-docs")
            .vectorStoreType("memory")
            .enabled(true)
            .build(),
        AiKnowledgeBaseDescriptor.builder()
            .knowledgeBaseId("KB-DOCS")
            .vectorStoreType("other")
            .enabled(true)
            .build()
    );

    var registry = new AiKnowledgeBaseRegistry(Collections.singletonList(provider));

    assertEquals("kb-docs", registry.get("KB-Docs").get().getKnowledgeBaseId());
    assertEquals(1, registry.getAll().size());
  }

  @Test
  void autoConfiguration_registersRegistryAndRetrieverBeans() {
    AiKnowledgeBaseDescriptorProvider provider = () -> Collections.singletonList(
        AiKnowledgeBaseDescriptor.builder()
            .knowledgeBaseId("kb-docs")
            .vectorStoreType("memory")
            .enabled(true)
            .build()
    );

    contextRunner
        .withBean(AiKnowledgeBaseDescriptorProvider.class, () -> provider)
        .run(context -> {
          assertTrue(context.containsBean("aiKnowledgeBaseRegistry"));
          assertTrue(context.containsBean("aiRetriever"));
          assertEquals("kb-docs", context.getBean(AiKnowledgeBaseRegistry.class).get("KB-DOCS").get().getKnowledgeBaseId());
        });
  }

  private static final class TestRequests {

    private static RetrieverRequest retriever(String knowledgeBaseId, String query) {
      return RetrieverRequest.builder()
          .knowledgeBaseId(knowledgeBaseId)
          .query(query)
          .build();
    }
  }
}
