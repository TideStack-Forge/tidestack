package com.ouroboros.ai.retrieval;

import java.util.Map;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.support.AiModelCopies;

public final class RetrieverRequest {
  private final String knowledgeBaseId;
  private final String query;
  private final Integer topK;
  private final Map<String, Object> metadataFilter;
  private final AiInvocationContext invocationContext;

  private RetrieverRequest(Builder builder) {
    this.knowledgeBaseId = builder.knowledgeBaseId;
    this.query = builder.query;
    this.topK = builder.topK;
    this.metadataFilter = AiModelCopies.copyMap(builder.metadataFilter);
    this.invocationContext = builder.invocationContext;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public String getQuery() {
    return query;
  }

  public Integer getTopK() {
    return topK;
  }

  public Map<String, Object> getMetadataFilter() {
    return metadataFilter;
  }

  public AiInvocationContext getInvocationContext() {
    return invocationContext;
  }

  public static final class Builder {
    private String knowledgeBaseId;
    private String query;
    private Integer topK;
    private Map<String, Object> metadataFilter;
    private AiInvocationContext invocationContext;

    private Builder() {
    }

    public Builder knowledgeBaseId(String knowledgeBaseId) {
      this.knowledgeBaseId = knowledgeBaseId;
      return this;
    }

    public Builder query(String query) {
      this.query = query;
      return this;
    }

    public Builder topK(Integer topK) {
      this.topK = topK;
      return this;
    }

    public Builder metadataFilter(Map<String, Object> metadataFilter) {
      this.metadataFilter = metadataFilter;
      return this;
    }

    public Builder invocationContext(AiInvocationContext invocationContext) {
      this.invocationContext = invocationContext;
      return this;
    }

    public RetrieverRequest build() {
      return new RetrieverRequest(this);
    }
  }
}
