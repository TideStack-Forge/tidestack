package com.ouroboros.ai.anthropic;

import java.util.List;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.options.AiOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicSpringAiToolPolicyTest {

  @Test
  void rejectsUnknownProviderOptionBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var request = ChatRequest.builder()
          .modelId("platform-anthropic-chat")
          .message(AnthropicSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .options(AiOptions.builder().providerOption("unknownAnthropicOption", "value").build())
          .build();

      var exception = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), request));

      assertEquals("Anthropic provider option is not supported: unknownAnthropicOption", exception.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void deniedToolThrowsPolicyViolationBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-key")))),
          new AnthropicSpringAiTestSupport.SingleToolRegistry(AnthropicSpringAiTestSupport.searchTool(false)));
      var request = ChatRequest.builder()
          .modelId("platform-anthropic-chat")
          .message(AnthropicSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .toolNames(List.of("search"))
          .build();

      var exception = assertThrows(AiPolicyViolationException.class,
          () -> adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), request));

      assertEquals("AI model invocation is not allowed for tool: search", exception.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void rejectsInvalidMessagePartsBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connection(server.url("/").toString(), "test-key"),
              AnthropicSpringAiTestSupport.connection(server.url("/").toString(), "test-key"))));

      var blankCallId = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.blankCallIdChat()));
      assertEquals("Anthropic tool result callId must not be blank", blankCallId.getMessage());

      var media = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.mediaChat()));
      assertTrue(media.getMessage().contains("MediaContentPart"));
      assertEquals(0, server.getRequestCount());
    }
  }
}
