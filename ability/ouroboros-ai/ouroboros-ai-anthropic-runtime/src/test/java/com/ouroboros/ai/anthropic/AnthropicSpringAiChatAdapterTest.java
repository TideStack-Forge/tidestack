package com.ouroboros.ai.anthropic;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicSpringAiChatAdapterTest {

  @Test
  void mapsTypedPlatformRequestToSpringAiAnthropicNativePayload() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(AnthropicSpringAiTestSupport.jsonResponse(AnthropicSpringAiTestSupport.textResponseBody("done")));
      var factory = new AnthropicSpringAiTestSupport.RecordingAnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connection(server.url("/").toString(), "test-key")));
      var adapter = new AnthropicChatAdapter(factory,
          new AnthropicSpringAiTestSupport.SingleToolRegistry(AnthropicSpringAiTestSupport.searchTool(true)));

      var response = adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest());

      assertEquals("done", response.getText());
      assertEquals(5, response.getUsage().get("inputTokens"));
      assertEquals(2, response.getUsage().get("outputTokens"));
      assertEquals(7, response.getUsage().get("totalTokens"));
      assertEquals(1, factory.getModelCreations());

      var recordedRequest = server.takeRequest();
      var body = Json.toMap(recordedRequest.getBody().readUtf8());
      var messages = AnthropicSpringAiTestSupport.list(body.get("messages"));

      assertEquals("/v1/messages", recordedRequest.getPath());
      assertEquals("test-key", recordedRequest.getHeader("x-api-key"));
      assertEquals("claude-3-5-sonnet-20241022", body.get("model"));
      assertEquals(256, body.get("max_tokens"));
      assertEquals("provider system\n\nsystem rules\n\ndeveloper rules", body.get("system"));
      assertEquals("standard_only", body.get("service_tier"));
      assertEquals(3, body.get("top_k"));
      assertEquals("eu", body.get("inference_geo"));
      assertEquals(2, AnthropicSpringAiTestSupport.list(body.get("stop_sequences")).size());
      assertTrue(AnthropicSpringAiTestSupport.map(body.get("container")).containsKey("skills"));
      assertTrue(recordedRequest.getHeaders().values("anthropic-beta").stream().anyMatch(value -> value.contains("beta-array")));
      assertTrue(recordedRequest.getHeaders().values("anthropic-beta").stream().anyMatch(value -> value.contains("skills-")));

      var userMessage = AnthropicSpringAiTestSupport.map(messages.get(0));
      assertEquals("user", userMessage.get("role"));
      assertEquals("hello", userMessage.get("content"));

      var assistantToolUse = AnthropicSpringAiTestSupport.map(
          AnthropicSpringAiTestSupport.list(AnthropicSpringAiTestSupport.map(messages.get(1)).get("content")).get(0));
      assertEquals("tool_use", assistantToolUse.get("type"));
      assertEquals("call-123", assistantToolUse.get("id"));
      assertEquals("search", assistantToolUse.get("name"));
      assertEquals("hello", AnthropicSpringAiTestSupport.map(assistantToolUse.get("input")).get("query"));

      var toolResult = AnthropicSpringAiTestSupport.map(
          AnthropicSpringAiTestSupport.list(AnthropicSpringAiTestSupport.map(messages.get(2)).get("content")).get(0));
      assertEquals("tool_result", toolResult.get("type"));
      assertEquals("call-123", toolResult.get("tool_use_id"));
      assertEquals(3, Json.toMap(String.valueOf(toolResult.get("content"))).get("count"));
    }
  }

  @Test
  void mapsSpringAiAnthropicToolUseResponseToPlatformToolCalls() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(AnthropicSpringAiTestSupport.jsonResponse(AnthropicSpringAiTestSupport.toolUseResponseBody()));
      var adapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connection(server.url("/").toString(), "test-key"))),
          new AnthropicSpringAiTestSupport.SingleToolRegistry(AnthropicSpringAiTestSupport.searchTool(true)));

      var response = adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest());

      assertEquals("ok", response.getText());
      assertEquals("call-response", response.getToolCalls().get(0).get("callId"));
      assertEquals("search", response.getToolCalls().get(0).get("name"));
      assertEquals("hello", AnthropicSpringAiTestSupport.map(response.getToolCalls().get(0).get("arguments")).get("query"));
      assertTrue(response.getContent().stream().anyMatch(item -> "tool_use".equals(item.get("type"))));
    }
  }

  @Test
  void doesNotCacheSpringAiModelAcrossCredentialRotation() throws Exception {
    try (MockWebServer firstServer = new MockWebServer();
         MockWebServer secondServer = new MockWebServer()) {
      firstServer.enqueue(AnthropicSpringAiTestSupport.jsonResponse(AnthropicSpringAiTestSupport.textResponseBody("first")));
      secondServer.enqueue(AnthropicSpringAiTestSupport.jsonResponse(AnthropicSpringAiTestSupport.textResponseBody("second")));
      var resolver = new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
          AnthropicSpringAiTestSupport.connection(firstServer.url("/").toString(), "secret-a"),
          AnthropicSpringAiTestSupport.connection(secondServer.url("/").toString(), "secret-b"));
      var factory = new AnthropicSpringAiTestSupport.RecordingAnthropicAdapterFactory(ref -> "resolved-" + ref, resolver);
      var adapter = new AnthropicChatAdapter(factory);

      assertEquals("first", adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), minimalRequest("request-model-a")).getText());
      assertEquals("second", adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), minimalRequest("request-model-b")).getText());

      assertEquals(2, resolver.getResolveCount());
      assertEquals(2, factory.getModelCreations());
      assertEquals("resolved-secret-a", firstServer.takeRequest().getHeader("x-api-key"));
      assertEquals("resolved-secret-b", secondServer.takeRequest().getHeader("x-api-key"));
    }
  }

  private static com.ouroboros.ai.chat.ChatRequest minimalRequest(String modelId) {
    return com.ouroboros.ai.chat.ChatRequest.builder()
        .modelId(modelId)
        .message(AnthropicSpringAiTestSupport.textMessage(com.ouroboros.ai.chat.ChatMessageRole.USER, "hello"))
        .build();
  }
}
