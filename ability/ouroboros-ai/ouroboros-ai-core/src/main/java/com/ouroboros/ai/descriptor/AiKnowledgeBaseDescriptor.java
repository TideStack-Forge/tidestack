package com.ouroboros.ai.descriptor;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiKnowledgeBaseDescriptor {
  private final String knowledgeBaseId;
  private final String retrievalMode;
  private final String retrieverRef;
  private final String backendRef;
  private final String embeddingModelId;
  private final String vectorStoreType;
  private final String indexName;
  private final Map<String, Object> chunkingPolicy;
  private final Map<String, Object> retrievalPolicy;
  private final Map<String, Object> metadataFilters;
  private final Map<String, Object> metadata;
  private final boolean enabled;

  private AiKnowledgeBaseDescriptor(Builder builder) {
    this.knowledgeBaseId = builder.knowledgeBaseId;
    this.retrievalMode = builder.retrievalMode;
    this.retrieverRef = builder.retrieverRef;
    this.backendRef = builder.backendRef;
    this.embeddingModelId = builder.embeddingModelId;
    this.vectorStoreType = builder.vectorStoreType;
    this.indexName = builder.indexName;
    this.chunkingPolicy = AiModelCopies.copyMap(builder.chunkingPolicy);
    this.retrievalPolicy = AiModelCopies.copyMap(builder.retrievalPolicy);
    this.metadataFilters = AiModelCopies.copyMap(builder.metadataFilters);
    this.metadata = AiModelCopies.copyMap(builder.metadata);
    this.enabled = builder.enabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public String getRetrievalMode() {
    return retrievalMode;
  }

  public String getRetrieverRef() {
    return retrieverRef;
  }

  public String getBackendRef() {
    return backendRef;
  }

  public String getEmbeddingModelId() {
    return embeddingModelId;
  }

  public String getVectorStoreType() {
    return vectorStoreType;
  }

  public String getIndexName() {
    return indexName;
  }

  public Map<String, Object> getChunkingPolicy() {
    return chunkingPolicy;
  }

  public Map<String, Object> getRetrievalPolicy() {
    return retrievalPolicy;
  }

  public Map<String, Object> getMetadataFilters() {
    return metadataFilters;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static final class Builder {
    private String knowledgeBaseId;
    private String retrievalMode;
    private String retrieverRef;
    private String backendRef;
    private String embeddingModelId;
    private String vectorStoreType;
    private String indexName;
    private Map<String, Object> chunkingPolicy;
    private Map<String, Object> retrievalPolicy;
    private Map<String, Object> metadataFilters;
    private Map<String, Object> metadata;
    private boolean enabled = true;

    private Builder() {
    }

    public Builder knowledgeBaseId(String knowledgeBaseId) {
      this.knowledgeBaseId = knowledgeBaseId;
      return this;
    }

    public Builder retrievalMode(String retrievalMode) {
      this.retrievalMode = retrievalMode;
      return this;
    }

    public Builder retrieverRef(String retrieverRef) {
      this.retrieverRef = retrieverRef;
      return this;
    }

    public Builder backendRef(String backendRef) {
      this.backendRef = backendRef;
      return this;
    }

    public Builder embeddingModelId(String embeddingModelId) {
      this.embeddingModelId = embeddingModelId;
      return this;
    }

    public Builder vectorStoreType(String vectorStoreType) {
      this.vectorStoreType = vectorStoreType;
      return this;
    }

    public Builder indexName(String indexName) {
      this.indexName = indexName;
      return this;
    }

    public Builder chunkingPolicy(Map<String, Object> chunkingPolicy) {
      this.chunkingPolicy = chunkingPolicy;
      return this;
    }

    public Builder retrievalPolicy(Map<String, Object> retrievalPolicy) {
      this.retrievalPolicy = retrievalPolicy;
      return this;
    }

    public Builder metadataFilters(Map<String, Object> metadataFilters) {
      this.metadataFilters = metadataFilters;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public AiKnowledgeBaseDescriptor build() {
      return new AiKnowledgeBaseDescriptor(this);
    }
  }
}
