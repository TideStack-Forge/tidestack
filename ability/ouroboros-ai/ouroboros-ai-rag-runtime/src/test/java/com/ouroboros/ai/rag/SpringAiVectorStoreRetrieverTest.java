package com.ouroboros.ai.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;
import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.runtime.provider.AiKnowledgeBaseDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiVectorStoreRetrieverTest {

  @Test
  void retrieve_mapsDescriptorRequestAndDocumentsThroughSpringAiVectorStore() {
    var vectorStore = new CapturingVectorStore(Arrays.asList(
        Document.builder().id("a").text("alpha").metadata(metadata("tenant", "dev", "kind", "guide", "indexName", "kb-index")).score(0.91).build(),
        Document.builder().id("b").text("beta").metadata(metadata("tenant", "prod", "kind", "guide", "indexName", "kb-index")).score(0.88).build(),
        Document.builder().id("c").text("gamma").metadata(metadata("tenant", "dev", "kind", "faq", "indexName", "kb-index")).score(0.72).build()
    ));
    var registry = registry(AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .vectorStoreType("spring-ai")
        .backendRef("docsStore")
        .indexName("kb-index")
        .metadataFilters(metadata("tenant", "dev"))
        .retrievalPolicy(metadata("topK", 2, "similarityThreshold", 0.25))
        .enabled(true)
        .build());
    var retriever = new SpringAiVectorStoreRetriever(registry,
        Collections.singletonMap("docsStore", vectorStore),
        new InMemoryAiRetriever(Collections.emptyMap()));

    var response = retriever.retrieve(RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("install guide")
        .metadataFilter(metadata("kind", "guide"))
        .build());

    assertEquals("install guide", vectorStore.lastRequest.getQuery());
    assertEquals(2, vectorStore.lastRequest.getTopK());
    assertEquals(0.25, vectorStore.lastRequest.getSimilarityThreshold(), 0.001);
    assertNotNull(vectorStore.lastRequest.getFilterExpression());
    assertEquals(Collections.singletonList("alpha"), response.getSnippets());
    assertEquals("a", response.getDocuments().get(0).get("id"));
    assertEquals("alpha", response.getDocuments().get(0).get("text"));
    assertEquals(0.91, response.getDocuments().get(0).get("score"));
    assertEquals("dev", ((Map<?, ?>) response.getDocuments().get(0).get("metadata")).get("tenant"));
  }

  @Test
  void retrieve_treatsPgvectorDescriptorAsSpringAiVectorStore() {
    var vectorStore = new CapturingVectorStore(Collections.singletonList(
        Document.builder().id("pg").text("pgvector").metadata(Collections.emptyMap()).score(0.91).build()
    ));
    var registry = registry(AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .vectorStoreType("pgvector")
        .backendRef("docsStore")
        .enabled(true)
        .build());
    var retriever = new SpringAiVectorStoreRetriever(registry,
        Collections.singletonMap("docsStore", vectorStore),
        new InMemoryAiRetriever(Collections.singletonMap("kb-docs", Collections.singletonList("fallback"))));

    var response = retriever.retrieve(RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("anything")
        .build());

    assertEquals(Collections.singletonList("pgvector"), response.getSnippets());
    assertNotNull(vectorStore.lastRequest);
  }

  @Test
  void retrieve_returnsEmptyAndDoesNotQueryAnotherStoreWhenExplicitBackendRefIsMissing() {
    var wrongStore = new CapturingVectorStore(Collections.emptyList());
    var registry = registry(AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .vectorStoreType("spring-ai")
        .backendRef("missingStore")
        .retrievalPolicy(metadata("topK", 5))
        .enabled(true)
        .build());
    var retriever = new SpringAiVectorStoreRetriever(registry,
        Collections.singletonMap("docsStore", wrongStore),
        new InMemoryAiRetriever(Collections.emptyMap()));

    var response = retriever.retrieve(RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("anything")
        .topK(1)
        .build());

    assertTrue(response.getSnippets().isEmpty());
    assertTrue(response.getDocuments().isEmpty());
    assertNull(wrongStore.lastRequest);
  }

  @Test
  void retrieve_returnsEmptyAndDoesNotQueryAnotherStoreWhenExplicitRetrieverRefIsMissing() {
    var wrongStore = new CapturingVectorStore(Collections.emptyList());
    var registry = registry(AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .vectorStoreType("spring-ai")
        .retrieverRef("missingRetriever")
        .retrievalPolicy(metadata("topK", 5))
        .enabled(true)
        .build());
    var retriever = new SpringAiVectorStoreRetriever(registry,
        Collections.singletonMap("docsStore", wrongStore),
        new InMemoryAiRetriever(Collections.emptyMap()));

    var response = retriever.retrieve(RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("anything")
        .topK(1)
        .build());

    assertTrue(response.getSnippets().isEmpty());
    assertTrue(response.getDocuments().isEmpty());
    assertNull(wrongStore.lastRequest);
  }

  @Test
  void retrieve_fallsBackWhenDescriptorOnlyHasGenericBackendRef() {
    var vectorStore = new CapturingVectorStore(Collections.singletonList(
        Document.builder().id("a").text("spring-ai").metadata(Collections.emptyMap()).score(0.91).build()
    ));
    var registry = registry(AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .backendRef("docsStore")
        .enabled(true)
        .build());
    var retriever = new SpringAiVectorStoreRetriever(registry,
        Collections.singletonMap("docsStore", vectorStore),
        new InMemoryAiRetriever(Collections.singletonMap("kb-docs", Collections.singletonList("fallback"))));

    var response = retriever.retrieve(RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("anything")
        .build());

    assertEquals(Collections.singletonList("fallback"), response.getSnippets());
    assertNull(vectorStore.lastRequest);
  }

  private static AiKnowledgeBaseRegistry registry(AiKnowledgeBaseDescriptor descriptor) {
    AiKnowledgeBaseDescriptorProvider provider = () -> Collections.singletonList(descriptor);
    return new AiKnowledgeBaseRegistry(Collections.singletonList(provider));
  }

  private static Map<String, Object> metadata(Object... values) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) {
      metadata.put(String.valueOf(values[index]), values[index + 1]);
    }
    return metadata;
  }

  private static final class CapturingVectorStore implements VectorStore {
    private final List<Document> documents;
    private SearchRequest lastRequest;

    private CapturingVectorStore(List<Document> documents) {
      this.documents = documents;
    }

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
      this.lastRequest = request;
      return documents.stream()
          .filter(document -> matches(request.getFilterExpression(), document.getMetadata()))
          .limit(request.getTopK())
          .collect(Collectors.toList());
    }

    @Override
    public <T> Optional<T> getNativeClient() {
      return Optional.empty();
    }

    private static boolean matches(Filter.Expression expression, Map<String, Object> metadata) {
      if (expression == null) {
        return true;
      }
      if (expression.type() == Filter.ExpressionType.AND) {
        return matches((Filter.Expression) expression.left(), metadata)
            && matches((Filter.Expression) expression.right(), metadata);
      }
      if (expression.type() == Filter.ExpressionType.EQ) {
        var key = (Filter.Key) expression.left();
        var value = (Filter.Value) expression.right();
        return String.valueOf(value.value()).equals(String.valueOf(metadata.get(key.key())));
      }
      return true;
    }
  }
}
