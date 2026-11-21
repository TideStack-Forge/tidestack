package com.ouroboros.ai.retrieval;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class RetrieverResponse {
  private final String knowledgeBaseId;
  private final List<String> snippets;
  private final List<Map<String, Object>> documents;

  private RetrieverResponse(Builder builder) {
    this.knowledgeBaseId = builder.knowledgeBaseId;
    this.snippets = AiModelCopies.copyStringList(builder.snippets);
    this.documents = AiModelCopies.copyList(builder.documents);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public List<String> getSnippets() {
    return snippets;
  }

  public List<Map<String, Object>> getDocuments() {
    return documents;
  }

  public static final class Builder {
    private String knowledgeBaseId;
    private List<String> snippets;
    private List<Map<String, Object>> documents;

    private Builder() {
    }

    public Builder knowledgeBaseId(String knowledgeBaseId) {
      this.knowledgeBaseId = knowledgeBaseId;
      return this;
    }

    public Builder snippets(List<String> snippets) {
      this.snippets = snippets;
      return this;
    }

    public Builder documents(List<Map<String, Object>> documents) {
      this.documents = documents;
      return this;
    }

    public RetrieverResponse build() {
      return new RetrieverResponse(this);
    }
  }
}
