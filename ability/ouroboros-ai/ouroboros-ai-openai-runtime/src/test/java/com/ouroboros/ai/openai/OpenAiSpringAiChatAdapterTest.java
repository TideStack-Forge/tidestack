package com.ouroboros.ai.openai;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiSpringAiChatAdapterTest {

  @Test
  void mapsPlatformChatRequestAndSpringAiResponseThroughCanonicalConnection() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"id\":\"chat-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4o-mini\","
          + "\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":\"hello\","
          + "\"tool_calls\":[{\"id\":\"call-response\",\"type\":\"function\",\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"query\\\":\\\"hello\\\"}\"}}]}}],"
          + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}"));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connection(server.url("/").toString(), "openai-secret"));
      var factory = new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);
      var adapter = new OpenAiChatAdapter(factory,
          new OpenAiSpringAiTestSupport.SingleToolRegistry(OpenAiSpringAiTestSupport.searchTool(true)));

      var response = adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest());

      assertEquals("hello", response.getText());
      assertEquals("hello", response.getContent().get(0).get("text"));
      assertEquals("call-response", response.getToolCalls().get(0).get("callId"));
      assertEquals("search", response.getToolCalls().get(0).get("name"));
      assertEquals(8, response.getUsage().get("totalTokens"));
      assertEquals(1, resolver.getResolveCount());
      assertEquals(1, factory.getApiCreations());
      assertEquals("tenant-a", resolver.getLastScope().getTenantId());
      assertEquals("prod", resolver.getLastScope().getEnvironment());

      var recorded = server.takeRequest();
      assertEquals("/v1/chat/completions", recorded.getPath());
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
      var messages = OpenAiSpringAiTestSupport.list(body.get("messages"));
      assertEquals("system", OpenAiSpringAiTestSupport.map(messages.get(0)).get("role"));
      assertEquals("system rules", OpenAiSpringAiTestSupport.map(messages.get(0)).get("content"));
      assertEquals("user", OpenAiSpringAiTestSupport.map(messages.get(1)).get("role"));
      assertEquals("hello", OpenAiSpringAiTestSupport.map(messages.get(1)).get("content"));
      var assistant = OpenAiSpringAiTestSupport.map(messages.get(2));
      assertEquals("assistant", assistant.get("role"));
      var toolCall = OpenAiSpringAiTestSupport.map(OpenAiSpringAiTestSupport.list(assistant.get("tool_calls")).get(0));
      assertEquals("call-123", toolCall.get("id"));
      assertEquals("search", OpenAiSpringAiTestSupport.map(toolCall.get("function")).get("name"));
      var toolResult = OpenAiSpringAiTestSupport.map(messages.get(3));
      assertEquals("tool", toolResult.get("role"));
      assertEquals("call-123", toolResult.get("tool_call_id"));
      var tool = OpenAiSpringAiTestSupport.map(OpenAiSpringAiTestSupport.list(body.get("tools")).get(0));
      assertEquals("function", tool.get("type"));
      assertEquals("search", OpenAiSpringAiTestSupport.map(tool.get("function")).get("name"));
    }
  }

  @Test
  void rejectsDeveloperRoleInsteadOfDowngradingItToSystem() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connection(server.url("/").toString(), "openai-secret"));
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(ref -> "resolved-" + ref, resolver));
      var request = com.ouroboros.ai.chat.ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(com.ouroboros.ai.chat.ChatMessageRole.DEVELOPER, "developer rules"))
          .build();

      var exception = assertThrows(com.ouroboros.ai.exception.AiProviderProtocolException.class,
          () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), request));

      assertEquals("OpenAI developer role is not supported by the current Spring AI chat model", exception.getMessage());
      assertEquals(1, resolver.getResolveCount());
    }
  }

  @Test
  void createsSpringAiApiPerInvocationSoCredentialRotationIsSafe() throws Exception {
    try (MockWebServer firstServer = new MockWebServer();
         MockWebServer secondServer = new MockWebServer()) {
      firstServer.enqueue(OpenAiSpringAiTestSupport.jsonResponse(OpenAiSpringAiTestSupport.chatResponseBody("first")));
      secondServer.enqueue(OpenAiSpringAiTestSupport.jsonResponse(OpenAiSpringAiTestSupport.chatResponseBody("second")));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connection(firstServer.url("/").toString(), "secret-a"),
          OpenAiSpringAiTestSupport.connection(secondServer.url("/").toString(), "secret-b"));
      var factory = new OpenAiSpringAiTestSupport.RecordingOpenAiAdapterFactory(ref -> "resolved-" + ref, resolver);
      var adapter = new OpenAiChatAdapter(factory, new OpenAiSpringAiTestSupport.EmptyToolRegistry());

      var first = adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), com.ouroboros.ai.chat.ChatRequest.builder()
          .modelId("request-model-a")
          .message(OpenAiSpringAiTestSupport.textMessage(com.ouroboros.ai.chat.ChatMessageRole.USER, "one"))
          .build());
      var second = adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), com.ouroboros.ai.chat.ChatRequest.builder()
          .modelId("request-model-b")
          .message(OpenAiSpringAiTestSupport.textMessage(com.ouroboros.ai.chat.ChatMessageRole.USER, "two"))
          .build());

      assertEquals("first", first.getText());
      assertEquals("second", second.getText());
      assertEquals(2, resolver.getResolveCount());
      assertEquals(2, factory.getApiCreations());
      assertEquals("Bearer resolved-secret-a", firstServer.takeRequest().getHeader("Authorization"));
      assertEquals("Bearer resolved-secret-b", secondServer.takeRequest().getHeader("Authorization"));
    }
  }

  @Test
  void mapsNamedToolChoiceToOpenAiFunctionToolChoiceObject() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse(OpenAiSpringAiTestSupport.chatResponseBody("tool selected")));
      var resolver = new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
          OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
              OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")));
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value, resolver),
          new OpenAiSpringAiTestSupport.SingleToolRegistry(OpenAiSpringAiTestSupport.searchTool(true)));
      var request = com.ouroboros.ai.chat.ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(com.ouroboros.ai.chat.ChatMessageRole.USER, "hello"))
          .options(com.ouroboros.ai.options.AiOptions.builder().toolChoice("search").build())
          .toolNames(java.util.List.of("search"))
          .build();

      adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), request);

      var body = Json.toMap(server.takeRequest().getBody().readUtf8());
      var toolChoice = OpenAiSpringAiTestSupport.map(body.get("tool_choice"));
      assertEquals("function", toolChoice.get("type"));
      assertEquals("search", OpenAiSpringAiTestSupport.map(toolChoice.get("function")).get("name"));
    }
  }
}
