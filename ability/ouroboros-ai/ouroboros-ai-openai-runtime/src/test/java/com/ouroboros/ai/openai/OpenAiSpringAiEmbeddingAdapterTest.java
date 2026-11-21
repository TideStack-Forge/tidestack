package com.ouroboros.ai.openai;

import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSpringAiEmbeddingAdapterTest {

  @Test
  void mapsSpringAiEmbeddingVectorModelUsageAndRequestOptions() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"object\":\"list\",\"model\":\"text-embedding-3-small\","
          + "\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2,0.3]}],"
          + "\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}"));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
              OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")));
      var factory = new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(value -> value, resolver);
      var adapter = new OpenAiEmbeddingAdapter(factory);
      var request = EmbeddingRequest.builder()
          .modelId("platform-openai-embedding")
          .inputs(List.of("embed me"))
          .options(AiOptions.builder().providerOption("dimensions", 3).build())
          .build();

      var response = adapter.embed(OpenAiSpringAiTestSupport.embeddingDescriptor(), request);

      assertEquals("platform-openai-embedding", response.getModelId());
      assertArrayEquals(new double[]{0.1, 0.2, 0.3}, response.getVectors().get(0).stream()
          .mapToDouble(Double::doubleValue)
          .toArray(), 0.000001);
      assertEquals(2, response.getUsage().get("totalTokens"));
      assertEquals(1, resolver.getResolveCount());
      assertEquals(1, factory.getApiCreations());
      var recorded = server.takeRequest();
      assertEquals("Bearer connection-key", recorded.getHeader("Authorization"));
      var body = Json.toMap(recorded.getBody().readUtf8());
      assertEquals("text-embedding-3-small", body.get("model"));
      assertEquals(3, body.get("dimensions"));
      assertEquals("embed me", OpenAiSpringAiTestSupport.list(body.get("input")).get(0));
    }
  }

  @Test
  void rejectsUnknownEmbeddingOptionBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new OpenAiEmbeddingAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var request = EmbeddingRequest.builder()
          .modelId("platform-openai-embedding")
          .inputs(List.of("docs"))
          .options(AiOptions.builder().providerOption("unknownEmbeddingOption", "value").build())
          .build();

      var exception = assertThrows(AiProviderProtocolException.class,
          () -> adapter.embed(OpenAiSpringAiTestSupport.embeddingDescriptor(), request));

      assertEquals("OpenAI embedding provider option is not supported: unknownEmbeddingOption", exception.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void mapsEmbeddingProviderErrorWithoutLeakingCredential() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
          .setBody("{\"error\":{\"code\":\"bad_request\",\"message\":\"bad input secret-api-key\"}}"));
      var adapter = new OpenAiEmbeddingAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "secret-api-key")))));
      var request = EmbeddingRequest.builder()
          .modelId("platform-openai-embedding")
          .inputs(List.of("docs"))
          .build();

      var providerError = assertThrows(AiProviderProtocolException.class,
          () -> adapter.embed(OpenAiSpringAiTestSupport.embeddingDescriptor(), request));

      assertTrue(providerError.getMessage().contains("OpenAI embedding provider error for model: platform-openai-embedding"));
      assertTrue(providerError.getMessage().contains("HTTP status 400"));
      assertFalse(providerError.getMessage().contains("secret-api-key"));
    }
  }
}
