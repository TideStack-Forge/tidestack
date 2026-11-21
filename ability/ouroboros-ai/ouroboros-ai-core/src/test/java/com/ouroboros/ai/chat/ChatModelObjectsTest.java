package com.ouroboros.ai.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatModelObjectsTest {

  @Test
  void chatMessageCopiesContentPartsAndSupportsMultiplePartTypes() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("mime", "image/png");
    Map<String, Object> arguments = new LinkedHashMap<>();
    arguments.put("limit", 10);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("count", 3);

    ChatMessage message = ChatMessage.builder()
        .role(ChatMessageRole.ASSISTANT)
        .contentParts(new ArrayList<>(Arrays.<ChatContentPart>asList(
            TextContentPart.of("hello"),
            MediaContentPart.builder().mediaType("image/png").uri("file://image.png").metadata(metadata).build(),
            ToolCallPart.builder().callId("call-123").toolName("search").arguments(arguments).build(),
            ToolResultPart.builder().callId("call-123").toolName("search").result(result).build())))
        .build();

    assertEquals(ChatMessageRole.ASSISTANT, message.getRole());
    assertEquals("hello", ((TextContentPart) message.getContentParts().get(0)).getText());
    assertEquals("image/png", ((MediaContentPart) message.getContentParts().get(1)).getMediaType());
    assertEquals("call-123", ((ToolCallPart) message.getContentParts().get(2)).getCallId());
    assertEquals("search", ((ToolCallPart) message.getContentParts().get(2)).getToolName());
    assertEquals("call-123", ((ToolResultPart) message.getContentParts().get(3)).getCallId());
    assertEquals(3, ((ToolResultPart) message.getContentParts().get(3)).getResult().get("count"));
    assertThrows(UnsupportedOperationException.class, () -> message.getContentParts().add(TextContentPart.of("x")));
    assertThrows(UnsupportedOperationException.class, () -> ((ToolCallPart) message.getContentParts().get(2)).getArguments().put("offset", 20));
    assertThrows(UnsupportedOperationException.class, () -> ((ToolResultPart) message.getContentParts().get(3)).getResult().put("next", true));
  }

  @Test
  void chatResponseAndStreamingEventKeepUsageAndToolCallsImmutable() {
    Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("inputTokens", 12);
    Map<String, Object> contentPart = new LinkedHashMap<>();
    contentPart.put("type", "text");
    contentPart.put("text", "answer");
    Map<String, Object> toolCall = new LinkedHashMap<>();
    toolCall.put("name", "search");

    ChatResponse response = ChatResponse.builder()
        .modelId("gpt-4o")
        .text("answer")
        .content(Collections.singletonList(contentPart))
        .toolCalls(Collections.singletonList(toolCall))
        .usage(usage)
        .build();

    StreamingChatEvent event = StreamingChatEvent.builder()
        .type("delta")
        .text("partial")
        .delta("par")
        .done(true)
        .usage(usage)
        .build();

    assertEquals("gpt-4o", response.getModelId());
    assertEquals("answer", response.getText());
    assertEquals("search", response.getToolCalls().get(0).get("name"));
    assertEquals(12, response.getUsage().get("inputTokens"));
    assertEquals("delta", event.getType());
    assertFalse(event.getUsage().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> response.getContent().add(contentPart));
    assertThrows(UnsupportedOperationException.class, () -> event.getUsage().put("extra", 1));
  }
}
