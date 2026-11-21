package com.ouroboros.ai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.retrieval.RetrieverResponse;

public class InMemoryAiRetriever implements AiRetriever {
  private final Map<String, List<String>> snippetsByKnowledgeBaseId;

  public InMemoryAiRetriever(Map<String, List<String>> snippetsByKnowledgeBaseId) {
    this.snippetsByKnowledgeBaseId = Collections.unmodifiableMap(copySnippets(snippetsByKnowledgeBaseId));
  }

  @Override
  public RetrieverResponse retrieve(RetrieverRequest request) {
    Objects.requireNonNull(request, "Retriever request must not be null");
    var snippets = snippetsByKnowledgeBaseId.get(request.getKnowledgeBaseId());
    return RetrieverResponse.builder()
        .knowledgeBaseId(request.getKnowledgeBaseId())
        .snippets(snippets == null ? Collections.emptyList() : snippets)
        .documents(Collections.emptyList())
        .build();
  }

  private static Map<String, List<String>> copySnippets(Map<String, List<String>> source) {
    Map<String, List<String>> target = new LinkedHashMap<>();
    if (source == null) {
      return target;
    }
    source.forEach((knowledgeBaseId, snippets) -> {
      target.put(knowledgeBaseId, snippets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(snippets)));
    });
    return target;
  }
}
