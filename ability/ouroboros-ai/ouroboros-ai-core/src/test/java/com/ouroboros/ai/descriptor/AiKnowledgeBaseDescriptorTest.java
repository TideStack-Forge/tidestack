package com.ouroboros.ai.descriptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiKnowledgeBaseDescriptorTest {

  @Test
  void knowledgeBaseDescriptor_supportsSemanticRetrievalFieldsAndLegacyVectorFields() {
    var descriptor = AiKnowledgeBaseDescriptor.builder()
        .knowledgeBaseId("kb-docs")
        .retrievalMode("VECTOR")
        .retrieverRef("retriever-default")
        .backendRef("pgvector-prod")
        .embeddingModelId("text-embedding-3-large")
        .vectorStoreType("PGVECTOR")
        .indexName("kb_docs")
        .build();

    assertEquals("VECTOR", descriptor.getRetrievalMode());
    assertEquals("retriever-default", descriptor.getRetrieverRef());
    assertEquals("pgvector-prod", descriptor.getBackendRef());
    assertEquals("PGVECTOR", descriptor.getVectorStoreType());
  }
}
