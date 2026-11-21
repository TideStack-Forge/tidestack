package com.ouroboros.ai.openai;

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

class OpenAiSpringAiToolPolicyTest {

  @Test
  void rejectsUnknownProviderOptionBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var request = ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .options(AiOptions.builder().providerOption("unknownOpenAiOption", "value").build())
          .build();

      var exception = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), request));

      assertEquals("OpenAI provider option is not supported: unknownOpenAiOption", exception.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void deniedToolThrowsPolicyViolationBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))),
          new OpenAiSpringAiTestSupport.SingleToolRegistry(OpenAiSpringAiTestSupport.searchTool(false)));
      var request = ChatRequest.builder()
          .modelId("platform-openai-chat")
          .message(OpenAiSpringAiTestSupport.textMessage(ChatMessageRole.USER, "hello"))
          .toolNames(List.of("search"))
          .build();

      var exception = assertThrows(AiPolicyViolationException.class,
          () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), request));

      assertEquals("AI model invocation is not allowed for tool: search", exception.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }
}
