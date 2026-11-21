package com.ouroboros.ai.rag;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;
import com.ouroboros.ai.runtime.provider.AiKnowledgeBaseDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRagRuntimeContextTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(AiRagRuntimeAutoConfiguration.class);

  @Test
  void defaultContextKeepsEmptyInMemoryRetrieverWhenNoVectorStoreExists() {
    contextRunner.run(context -> {
      assertTrue(context.containsBean("aiKnowledgeBaseRegistry"));
      assertTrue(context.containsBean("aiRetriever"));
      assertInstanceOf(InMemoryAiRetriever.class, context.getBean(AiRetriever.class));
    });
  }

  @Test
  void explicitVectorStoreContextUsesSpringAiRetrieverInsideRagRuntimeOnly() {
    AiKnowledgeBaseDescriptorProvider provider = () -> Collections.singletonList(
        AiKnowledgeBaseDescriptor.builder()
            .knowledgeBaseId("kb-docs")
            .vectorStoreType("spring-ai")
            .backendRef("docsStore")
            .enabled(true)
            .build()
    );

    contextRunner
        .withBean(AiKnowledgeBaseDescriptorProvider.class, () -> provider)
        .withBean("docsStore", VectorStore.class, EmptyVectorStore::new)
        .run(context -> assertInstanceOf(SpringAiVectorStoreRetriever.class, context.getBean(AiRetriever.class)));
  }

  private static final class EmptyVectorStore implements VectorStore {

    @Override
    public void add(List<Document> documents) {
      throw new UnsupportedOperationException("test vector store is read-only");
    }

    @Override
    public void delete(List<String> idList) {
      throw new UnsupportedOperationException("test vector store is read-only");
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
      throw new UnsupportedOperationException("test vector store is read-only");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
      return Collections.emptyList();
    }

    @Override
    public <T> Optional<T> getNativeClient() {
      return Optional.empty();
    }
  }
}
