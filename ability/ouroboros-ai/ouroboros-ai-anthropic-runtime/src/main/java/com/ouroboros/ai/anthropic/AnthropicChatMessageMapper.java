package com.ouroboros.ai.anthropic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.ouroboros.ai.chat.ChatContentPart;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.MediaContentPart;
import com.ouroboros.ai.chat.TextContentPart;
import com.ouroboros.ai.chat.ToolCallPart;
import com.ouroboros.ai.chat.ToolResultPart;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.util.Json;

final class AnthropicChatMessageMapper {

  private AnthropicChatMessageMapper() {
  }

  static List<Message> toMessages(List<ChatMessage> messages, String providerSystem) {
    List<Message> mapped = new ArrayList<>();
    if (hasText(providerSystem)) {
      mapped.add(SystemMessage.builder().text(providerSystem).build());
    }
    if (messages == null || messages.isEmpty()) {
      return mapped.isEmpty() ? Collections.emptyList() : mapped;
    }
    for (ChatMessage message : messages) {
      appendMessage(mapped, message);
    }
    return mapped;
  }

  private static void appendMessage(List<Message> mapped, ChatMessage message) {
    if (message == null) {
      return;
    }
    if (message.getRole() == ChatMessageRole.SYSTEM || message.getRole() == ChatMessageRole.DEVELOPER) {
      mapped.add(SystemMessage.builder().text(extractText(message, "Anthropic system message")).build());
      return;
    }
    if (message.getRole() == ChatMessageRole.TOOL) {
      mapped.add(toolResponseMessage(message));
      return;
    }
    var text = new StringBuilder();
    List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
    for (ChatContentPart part : message.getContentParts()) {
      if (part instanceof TextContentPart) {
        text.append(((TextContentPart) part).getText());
        continue;
      }
      if (part instanceof ToolCallPart) {
        toolCalls.add(toolCall(message.getRole(), (ToolCallPart) part));
        continue;
      }
      if (part instanceof ToolResultPart) {
        throw new AiProviderProtocolException("Anthropic tool result content is valid only for tool role");
      }
      if (part instanceof MediaContentPart) {
        throw unsupportedPart(part);
      }
      throw unsupportedPart(part);
    }
    if (message.getRole() == ChatMessageRole.ASSISTANT) {
      mapped.add(AssistantMessage.builder()
          .content(text.length() == 0 ? "" : text.toString())
          .toolCalls(toolCalls)
          .build());
      return;
    }
    mapped.add(UserMessage.builder().text(text.toString()).build());
  }

  private static ToolResponseMessage toolResponseMessage(ChatMessage message) {
    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
    for (ChatContentPart part : message.getContentParts()) {
      if (part instanceof ToolResultPart) {
        responses.add(toolResult((ToolResultPart) part));
        continue;
      }
      throw unsupportedPart(part);
    }
    return ToolResponseMessage.builder().responses(responses).build();
  }

  private static AssistantMessage.ToolCall toolCall(ChatMessageRole role, ToolCallPart part) {
    if (role != ChatMessageRole.ASSISTANT) {
      throw new AiProviderProtocolException("Anthropic tool call content is valid only for assistant role");
    }
    return new AssistantMessage.ToolCall(
        requireCallId(part.getCallId(), "call"),
        "function",
        requireText(part.getToolName(), "Anthropic tool call name must not be blank"),
        toJson("Anthropic tool call arguments", part.getArguments()));
  }

  private static ToolResponseMessage.ToolResponse toolResult(ToolResultPart part) {
    return new ToolResponseMessage.ToolResponse(
        requireCallId(part.getCallId(), "result"),
        requireText(part.getToolName(), "Anthropic tool result name must not be blank"),
        toJson("Anthropic tool result", part.getResult()));
  }

  private static String extractText(ChatMessage message, String label) {
    var text = new StringBuilder();
    for (ChatContentPart part : message.getContentParts()) {
      if (part instanceof TextContentPart) {
        text.append(((TextContentPart) part).getText());
        continue;
      }
      throw new AiProviderProtocolException(label + " supports only text content");
    }
    return text.toString();
  }

  private static String requireCallId(String callId, String kind) {
    return requireText(callId, "Anthropic tool " + kind + " callId must not be blank");
  }

  private static String requireText(String value, String message) {
    if (!hasText(value)) {
      throw new AiProviderProtocolException(message);
    }
    return value;
  }

  private static String toJson(String label, Object value) {
    return Json.tryToJsonString(value)
        .getOrElseThrow(exception -> new AiProviderProtocolException("Failed to serialize " + label, exception));
  }

  private static AiProviderProtocolException unsupportedPart(ChatContentPart part) {
    return new AiProviderProtocolException("Anthropic chat content part is not supported: "
        + (part == null ? "null" : part.getClass().getSimpleName()));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
