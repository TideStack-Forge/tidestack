package com.ouroboros.ai.openai;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.MediaContentPart;
import com.ouroboros.ai.chat.ToolCallPart;
import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatAdapterTest {

  @Test
  void chatAdapter_rejectsBlankToolCallIdBeforeProviderRequest() {
    var adapter = OpenAiSpringAiTestSupport.chatAdapter("http://127.0.0.1:9/");

    var exception = assertThrows(AiProviderProtocolException.class,
        () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), blankCallIdChat()));

    assertEquals("OpenAI tool call callId must not be blank", exception.getMessage());
  }

  @Test
  void chatAdapter_rejectsUnsupportedMediaPartBeforeProviderRequest() {
    var adapter = OpenAiSpringAiTestSupport.chatAdapter("http://127.0.0.1:9/");

    var exception = assertThrows(AiProviderProtocolException.class,
        () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), mediaChat()));

    assertTrue(exception.getMessage().contains("MediaContentPart"));
  }

  @Test
  void chatAdapter_propagatesAiInvocationExceptionFromFactory() {
    var expected = new AiInvocationException("Missing OpenAI provider connection for model: platform-openai-chat");
    var adapter = new OpenAiChatAdapter(new ThrowingOpenAiAdapterFactory(expected));

    var thrown = assertThrows(AiInvocationException.class,
        () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));

    assertSame(expected, thrown);
  }

  private static ChatRequest blankCallIdChat() {
    return ChatRequest.builder()
        .modelId("platform-openai-chat")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.ASSISTANT)
            .addContentPart(ToolCallPart.builder()
                .callId(" ")
                .toolName("search")
                .arguments(Collections.emptyMap())
                .build())
            .build())
        .build();
  }

  private static ChatRequest mediaChat() {
    return ChatRequest.builder()
        .modelId("platform-openai-chat")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.USER)
            .addContentPart(MediaContentPart.builder().mediaType("image/png").uri("file://image.png").build())
            .build())
        .build();
  }

  private static final class ThrowingOpenAiAdapterFactory extends OpenAiAdapterFactory {
    private final AiInvocationException exception;

    private ThrowingOpenAiAdapterFactory(AiInvocationException exception) {
      super(value -> value, (modelDescriptor, scope) -> Optional.empty());
      this.exception = exception;
    }

    @Override
    public OpenAiInvocation createInvocation(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
      throw exception;
    }
  }
}
