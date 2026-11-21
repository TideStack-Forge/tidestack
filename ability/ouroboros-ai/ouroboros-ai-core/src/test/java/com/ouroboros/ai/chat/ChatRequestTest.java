package com.ouroboros.ai.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatRequestTest {

  @Test
  void chatRequest_keepsTypedMessagesImmutable() {
    var request = ChatRequest.builder()
        .modelId("gpt-4o")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.USER)
            .addContentPart(TextContentPart.of("hello"))
            .build())
        .build();

    assertEquals(1, request.getMessageList().size());
    assertEquals(ChatMessageRole.USER, request.getMessageList().get(0).getRole());
    assertEquals("hello", ((TextContentPart) request.getMessageList().get(0).getContentParts().get(0)).getText());
  }
}
