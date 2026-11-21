package com.ouroboros.ai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;
import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.retrieval.RetrieverResponse;

final class SpringAiVectorStoreRetriever implements AiRetriever {
  private static final int DEFAULT_TOP_K = SearchRequest.DEFAULT_TOP_K;
  private static final String VECTOR_STORE_TYPE_PGVECTOR = "pgvector";
  private static final String VECTOR_STORE_TYPE_SPRING_AI = "spring-ai";
  private static final String VECTOR_STORE_TYPE_VECTOR_STORE = "vector-store";
  private static final String RETRIEVAL_POLICY_TOP_K = "topK";
  private static final String RETRIEVAL_POLICY_SIMILARITY_THRESHOLD = "similarityThreshold";
  private static final String METADATA_INDEX_NAME = "indexName";

  private final AiKnowledgeBaseRegistry registry;
  private final Map<String, VectorStore> vectorStores;
  private final AiRetriever fallback;

  SpringAiVectorStoreRetriever(AiKnowledgeBaseRegistry registry,
                               Map<String, VectorStore> vectorStores,
                               AiRetriever fallback) {
    this.registry = Objects.requireNonNull(registry, "AI knowledge base registry must not be null");
    this.vectorStores = Collections.unmodifiableMap(copyStores(vectorStores));
    this.fallback = Objects.requireNonNull(fallback, "Fallback AI retriever must not be null");
  }

  @Override
  public RetrieverResponse retrieve(RetrieverRequest request) {
    Objects.requireNonNull(request, "Retriever request must not be null");
    var descriptor = registry.get(request.getKnowledgeBaseId()).orElse(null);
    if (descriptor == null || !isSpringAiVectorStore(descriptor)) {
      return fallback.retrieve(request);
    }
    var vectorStore = selectVectorStore(descriptor).orElse(null);
    if (vectorStore == null) {
      return emptyResponse(request.getKnowledgeBaseId());
    }
    var documents = vectorStore.similaritySearch(toSearchRequest(descriptor, request));
    return toRetrieverResponse(request.getKnowledgeBaseId(), documents);
  }

  private SearchRequest toSearchRequest(AiKnowledgeBaseDescriptor descriptor, RetrieverRequest request) {
    var builder = SearchRequest.builder()
        .query(request.getQuery() == null ? "" : request.getQuery())
        .topK(resolveTopK(descriptor, request))
        .similarityThreshold(resolveSimilarityThreshold(descriptor));
    var filterExpression = filterExpression(descriptor, request);
    if (filterExpression != null) {
      builder.filterExpression(filterExpression);
    }
    return builder.build();
  }

  private Optional<VectorStore> selectVectorStore(AiKnowledgeBaseDescriptor descriptor) {
    var backendRef = normalize(descriptor.getBackendRef());
    if (backendRef != null) {
      return Optional.ofNullable(vectorStores.get(backendRef));
    }
    var retrieverRef = normalize(descriptor.getRetrieverRef());
    if (retrieverRef != null) {
      return Optional.ofNullable(vectorStores.get(retrieverRef));
    }
    var vectorStoreType = normalize(descriptor.getVectorStoreType());
    if (vectorStoreType != null && vectorStores.containsKey(vectorStoreType)) {
      return Optional.of(vectorStores.get(vectorStoreType));
    }
    if (vectorStores.size() == 1) {
      return Optional.of(vectorStores.values().iterator().next());
    }
    return Optional.empty();
  }

  private static boolean isSpringAiVectorStore(AiKnowledgeBaseDescriptor descriptor) {
    var vectorStoreType = normalize(descriptor.getVectorStoreType());
    return VECTOR_STORE_TYPE_PGVECTOR.equals(vectorStoreType)
        || VECTOR_STORE_TYPE_SPRING_AI.equals(vectorStoreType)
        || VECTOR_STORE_TYPE_VECTOR_STORE.equals(vectorStoreType);
  }

  private static Filter.Expression filterExpression(AiKnowledgeBaseDescriptor descriptor, RetrieverRequest request) {
    Map<String, Object> filters = new LinkedHashMap<>();
    filters.putAll(descriptor.getMetadataFilters());
    if (hasText(descriptor.getIndexName()) && !filters.containsKey(METADATA_INDEX_NAME)) {
      filters.put(METADATA_INDEX_NAME, descriptor.getIndexName());
    }
    filters.putAll(request.getMetadataFilter());
    if (filters.isEmpty()) {
      return null;
    }
    var builder = new FilterExpressionBuilder();
    FilterExpressionBuilder.Op expression = null;
    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      if (!hasText(entry.getKey()) || entry.getValue() == null) {
        continue;
      }
      var current = builder.eq(entry.getKey(), entry.getValue());
      expression = expression == null ? current : builder.and(expression, current);
    }
    return expression == null ? null : expression.build();
  }

  private static int resolveTopK(AiKnowledgeBaseDescriptor descriptor, RetrieverRequest request) {
    if (request.getTopK() != null && request.getTopK() > 0) {
      return request.getTopK();
    }
    var topK = integerOption(descriptor.getRetrievalPolicy().get(RETRIEVAL_POLICY_TOP_K));
    return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
  }

  private static double resolveSimilarityThreshold(AiKnowledgeBaseDescriptor descriptor) {
    var threshold = doubleOption(descriptor.getRetrievalPolicy().get(RETRIEVAL_POLICY_SIMILARITY_THRESHOLD));
    return threshold == null ? SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL : threshold;
  }

  private static RetrieverResponse toRetrieverResponse(String knowledgeBaseId, List<Document> documents) {
    List<String> snippets = new ArrayList<>();
    List<Map<String, Object>> mappedDocuments = new ArrayList<>();
    for (Document document : Optional.ofNullable(documents).orElse(Collections.emptyList())) {
      if (document == null) {
        continue;
      }
      snippets.add(document.getText());
      mappedDocuments.add(toDocumentMap(document));
    }
    return RetrieverResponse.builder()
        .knowledgeBaseId(knowledgeBaseId)
        .snippets(snippets)
        .documents(mappedDocuments)
        .build();
  }

  private static Map<String, Object> toDocumentMap(Document document) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    putIfPresent(mapped, "id", document.getId());
    putIfPresent(mapped, "text", document.getText());
    putIfPresent(mapped, "score", document.getScore());
    mapped.put("metadata", document.getMetadata());
    return mapped;
  }

  private static RetrieverResponse emptyResponse(String knowledgeBaseId) {
    return RetrieverResponse.builder()
        .knowledgeBaseId(knowledgeBaseId)
        .snippets(Collections.emptyList())
        .documents(Collections.emptyList())
        .build();
  }

  private static Map<String, VectorStore> copyStores(Map<String, VectorStore> source) {
    Map<String, VectorStore> target = new LinkedHashMap<>();
    if (source == null) {
      return target;
    }
    source.forEach((name, store) -> {
      if (hasText(name) && store != null) {
        target.put(normalize(name), store);
      }
    });
    return target;
  }

  private static Integer integerOption(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private static Double doubleOption(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private static void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
