package com.ouroboros.ai.request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.embedding.EmbeddingResponse;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.retrieval.RetrieverResponse;
import com.ouroboros.ai.tool.AiToolCallResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelRequestObjectsTest {

  @Test
  void embeddingAndRetrieverRequestsPreserveOptionsAndContext() {
    AiInvocationContext context = AiInvocationContext.builder()
        .tenantId("tenant-a")
        .appName("app-a")
        .environment("prod")
        .workspace("workspace-a")
        .userId("user-a")
        .build();

    AiOptions options = AiOptions.builder()
        .temperature(0.25d)
        .maxTokens(256)
        .providerOption("reasoning", "medium")
        .build();

    EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
        .modelId("text-embedding-3-large")
        .inputs(new ArrayList<>(Arrays.asList("hello", "world")))
        .options(options)
        .invocationContext(context)
        .build();

    RetrieverRequest retrieverRequest = RetrieverRequest.builder()
        .knowledgeBaseId("kb-docs")
        .query("spring boot 2.7")
        .topK(5)
        .metadataFilter(Collections.singletonMap("source", "docs"))
        .invocationContext(context)
        .build();

    assertEquals("text-embedding-3-large", embeddingRequest.getModelId());
    assertEquals(Arrays.asList("hello", "world"), embeddingRequest.getInputs());
    assertSame(options, embeddingRequest.getOptions());
    assertEquals("kb-docs", retrieverRequest.getKnowledgeBaseId());
    assertEquals("docs", retrieverRequest.getMetadataFilter().get("source"));
    assertSame(context, retrieverRequest.getInvocationContext());
    assertThrows(UnsupportedOperationException.class, () -> embeddingRequest.getInputs().add("x"));
  }

  @Test
  void embeddingAndRetrieverResponsesCopyNestedCollections() {
    List<Double> vector = new ArrayList<>(Arrays.asList(0.1d, 0.2d));
    List<List<Double>> vectors = new ArrayList<>();
    vectors.add(vector);
    Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("inputTokens", 3);

    List<String> snippets = new ArrayList<>(Arrays.asList("snippet-1", "snippet-2"));
    List<Map<String, Object>> documents = new ArrayList<>();
    documents.add(Collections.<String, Object>singletonMap("title", "doc-1"));

    EmbeddingResponse embeddingResponse = EmbeddingResponse.builder()
        .modelId("text-embedding-3-large")
        .vectors(vectors)
        .usage(usage)
        .build();

    RetrieverResponse retrieverResponse = RetrieverResponse.builder()
        .knowledgeBaseId("kb-docs")
        .snippets(snippets)
        .documents(documents)
        .build();

    assertEquals(2, embeddingResponse.getVectors().get(0).size());
    assertEquals(3, embeddingResponse.getUsage().get("inputTokens"));
    assertEquals(Arrays.asList("snippet-1", "snippet-2"), retrieverResponse.getSnippets());
    assertEquals("doc-1", retrieverResponse.getDocuments().get(0).get("title"));
    assertThrows(UnsupportedOperationException.class, () -> retrieverResponse.getSnippets().add("extra"));
  }

  @Test
  void aiToolCallResultKeepsSuccessFlagRawResultAndErrorMessage() {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("status", "ok");
    Object rawResult = new Object();

    AiToolCallResult result = AiToolCallResult.builder()
        .toolName("query-order")
        .success(true)
        .output(output)
        .rawResult(rawResult)
        .errorMessage(null)
        .build();

    assertEquals("query-order", result.getToolName());
    assertFalse(!result.isSuccess());
    assertSame(rawResult, result.getRawResult());
    assertEquals("ok", result.getOutput().get("status"));
    assertThrows(UnsupportedOperationException.class, () -> result.getOutput().put("x", "y"));
  }
}
