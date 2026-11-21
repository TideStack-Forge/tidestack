package com.ouroboros.ai.openai;

import java.util.List;
import java.util.function.Function;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSpringAiProviderMappingFeasibilityTest {

  @Test
  void springAiApiAndModelBuildersSupportDynamicBaseUrlAndDefaultOptions() {
    var options = OpenAiChatOptions.builder()
        .baseUrl("http://127.0.0.1:9")
        .apiKey("test-key")
        .model("gpt-4o-mini")
        .temperature(0.11)
        .maxTokens(32)
        .build();
    var model = OpenAiChatModel.builder()
        .options(options)
        .build();
    var embeddingOptions = OpenAiEmbeddingOptions.builder()
        .baseUrl("http://127.0.0.1:9")
        .apiKey("test-key")
        .model("text-embedding-3-small")
        .dimensions(8)
        .build();

    assertNotNull(model);
    assertEquals("http://127.0.0.1:9", model.getOptions().getBaseUrl());
    assertEquals("gpt-4o-mini", model.getOptions().getModel());
    assertEquals("text-embedding-3-small", embeddingOptions.getModel());
    assertEquals(8, embeddingOptions.getDimensions());
  }

  @Test
  void mapsPlatformChatOptionsToolsAndSpringAiResponseThroughCanonicalConnection() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"id\":\"chat-1\",\"object\":\"chat.completion\",\"created\":1,"
          + "\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
          + "\"message\":{\"role\":\"assistant\",\"content\":\"hello\",\"tool_calls\":[{\"id\":\"call-response\","
          + "\"type\":\"function\",\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"query\\\":\\\"hello\\\"}\"}}]}}],"
          + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}"));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connection(server.url("/").toString(), "openai-secret"));
      var factory = new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);
      var adapter = new OpenAiChatAdapter(factory,
          new OpenAiSpringAiTestSupport.SingleToolRegistry(OpenAiSpringAiTestSupport.searchTool(true)));

      var response = adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest());

      assertEquals("hello", response.getText());
      assertEquals("call-response", response.getToolCalls().get(0).get("callId"));
      assertEquals("search", response.getToolCalls().get(0).get("name"));
      assertEquals(8, response.getUsage().get("totalTokens"));
      assertEquals(1, resolver.getResolveCount());
      assertEquals(1, factory.getApiCreations());

      var recorded = server.takeRequest();
      assertEquals("Bearer resolved-openai-secret", recorded.getHeader("Authorization"));
      var body = Json.toMap(recorded.getBody().readUtf8());
      assertEquals("gpt-4o-mini", body.get("model"));
      assertEquals(0.2, body.get("temperature"));
      assertEquals(0.9, body.get("top_p"));
      assertEquals(128, body.get("max_tokens"));
      assertEquals("standard", body.get("service_tier"));
      assertEquals("cache-key-1", body.get("prompt_cache_key"));
      assertEquals("24h", body.get("prompt_cache_retention"));
      assertEquals("safe-user", body.get("safety_identifier"));
      assertEquals("auto", body.get("tool_choice"));
      var tool = OpenAiSpringAiTestSupport.map(OpenAiSpringAiTestSupport.list(body.get("tools")).get(0));
      assertEquals("function", tool.get("type"));
      assertEquals("search", OpenAiSpringAiTestSupport.map(tool.get("function")).get("name"));
    }
  }

  @Test
  void usesRequestModelIdFallbackAndDoesNotCacheCredentialRotation() throws Exception {
    try (MockWebServer firstServer = new MockWebServer();
         MockWebServer secondServer = new MockWebServer()) {
      firstServer.enqueue(OpenAiSpringAiTestSupport.jsonResponse(OpenAiSpringAiTestSupport.chatResponseBody("first")));
      secondServer.enqueue(OpenAiSpringAiTestSupport.jsonResponse(OpenAiSpringAiTestSupport.chatResponseBody("second")));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connection(firstServer.url("/").toString(), "secret-a"),
          OpenAiSpringAiTestSupport.connection(secondServer.url("/").toString(), "secret-b"));
      var factory = new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);
      var adapter = new OpenAiChatAdapter(factory, new OpenAiSpringAiTestSupport.EmptyToolRegistry());
      var descriptor = AiModelDescriptor.builder()
          .modelId("platform-openai-chat")
          .providerFamily(AiProviderFamily.OPENAI)
          .providerInstanceId("openai-prod")
          .build();

      var first = adapter.chat(descriptor, ChatRequest.builder()
          .modelId("request-model-a")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "one"))
          .build());
      var second = adapter.chat(descriptor, ChatRequest.builder()
          .modelId("request-model-b")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "two"))
          .build());

      assertEquals("first", first.getText());
      assertEquals("second", second.getText());
      assertEquals(2, resolver.getResolveCount());
      assertEquals(2, factory.getApiCreations());
      var firstRequest = firstServer.takeRequest();
      var secondRequest = secondServer.takeRequest();
      assertEquals("Bearer resolved-secret-a", firstRequest.getHeader("Authorization"));
      assertEquals("Bearer resolved-secret-b", secondRequest.getHeader("Authorization"));
      assertEquals("request-model-a", Json.toMap(firstRequest.getBody().readUtf8()).get("model"));
      assertEquals("request-model-b", Json.toMap(secondRequest.getBody().readUtf8()).get("model"));
    }
  }

  @Test
  void inheritsCanonicalConnectionAndCredentialFailures() {
    var missing = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver()));
    var missingConnection = assertThrows(AiInvocationException.class,
        () -> missing.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Missing OpenAI provider connection for model: platform-openai-chat", missingConnection.getMessage());

    var cause = new IllegalStateException("secret backend unavailable");
    var failingCredential = new OpenAiChatAdapter(new OpenAiAdapterFactory(ref -> {
      throw cause;
    }, new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
        OpenAiSpringAiTestSupport.connection("http://127.0.0.1:9/", "openai-secret"))));
    var credentialFailure = assertThrows(AiInvocationException.class,
        () -> failingCredential.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Failed to resolve OpenAI credentials for provider connection: openai-prod", credentialFailure.getMessage());
    assertSame(cause, credentialFailure.getCause());

    var missingCredential = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
            OpenAiSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null, null))));
    var exception = assertThrows(AiInvocationException.class,
        () -> missingCredential.chat(OpenAiSpringAiTestSupport.chatDescriptorWithProviderApiKey(),
            OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Missing OpenAI credentials for provider connection: openai-prod", exception.getMessage());
  }

  @Test
  void rejectsUnknownProviderOptionAndDeniedToolBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var unknownAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var unknownOption = ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .options(AiOptions.builder().providerOption("unknownOpenAiOption", "value").build())
          .build();

      var unknown = assertThrows(AiProviderProtocolException.class,
          () -> unknownAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), unknownOption));
      assertEquals("OpenAI provider option is not supported: unknownOpenAiOption", unknown.getMessage());

      var denyAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))),
          new OpenAiSpringAiTestSupport.SingleToolRegistry(OpenAiSpringAiTestSupport.searchTool(false)));
      var deniedTool = ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .toolNames(List.of("search"))
          .build();
      var denied = assertThrows(AiPolicyViolationException.class,
          () -> denyAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), deniedTool));
      assertEquals("AI model invocation is not allowed for tool: search", denied.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void mapsSpringAiEmbeddingVectorModelAndUsage() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"object\":\"list\",\"model\":\"text-embedding-3-small\","
          + "\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2,0.3]}],"
          + "\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}"));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
              OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")));
      var adapter = new OpenAiEmbeddingAdapter(new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(Function.identity(), resolver));
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
      var body = Json.toMap(server.takeRequest().getBody().readUtf8());
      assertEquals("text-embedding-3-small", body.get("model"));
      assertEquals(3, body.get("dimensions"));
      assertEquals("embed me", OpenAiSpringAiTestSupport.list(body.get("input")).get(0));
    }
  }

  @Test
  void mapsProviderHttpTransportAndResponseMappingFailuresWithoutLeakingCredentials() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
          .setBody("{\"error\":{\"code\":\"bad_request\",\"message\":\"bad input secret-api-key\"}}"));
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "secret-api-key")))));

      var providerError = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));

      assertTrue(providerError.getMessage().contains("OpenAI chat provider error for model: platform-openai-chat"));
      assertTrue(providerError.getMessage().contains("HTTP status 400"));
      assertTrue(providerError.getMessage().contains("bad_request"));
      assertFalse(providerError.getMessage().contains("secret-api-key"));
    }

    var transportAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
            OpenAiSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null,
                OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
    var transport = assertThrows(AiProviderProtocolException.class,
        () -> transportAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("OpenAI chat invocation failed for model: platform-openai-chat", transport.getMessage());
    assertTrue(transport.getCause().getClass().getName().startsWith("com.openai.errors."));

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"id\":\"chat-1\",\"choices\":[{\"message\":null}]}"));
      var mappingAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(Function.identity(),
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var mapping = assertThrows(AiProviderProtocolException.class,
          () -> mappingAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
      assertEquals("Failed to map OpenAI chat response for model: platform-openai-chat", mapping.getMessage());
    }
  }
}
