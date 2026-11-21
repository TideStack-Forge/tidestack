package com.ouroboros.ai.chat;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class ChatResponse {
  private final String modelId;
  private final String text;
  private final List<Map<String, Object>> content;
  private final List<Map<String, Object>> toolCalls;
  private final Map<String, Object> usage;

  private ChatResponse(Builder builder) {
    this.modelId = builder.modelId;
    this.text = builder.text;
    this.content = AiModelCopies.copyList(builder.content);
    this.toolCalls = AiModelCopies.copyList(builder.toolCalls);
    this.usage = AiModelCopies.copyMap(builder.usage);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getModelId() {
    return modelId;
  }

  public String getText() {
    return text;
  }

  public List<Map<String, Object>> getContent() {
    return content;
  }

  public List<Map<String, Object>> getToolCalls() {
    return toolCalls;
  }

  public Map<String, Object> getUsage() {
    return usage;
  }

  public static final class Builder {
    private String modelId;
    private String text;
    private List<Map<String, Object>> content;
    private List<Map<String, Object>> toolCalls;
    private Map<String, Object> usage;

    private Builder() {
    }

    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    public Builder text(String text) {
      this.text = text;
      return this;
    }

    public Builder content(List<Map<String, Object>> content) {
      this.content = content;
      return this;
    }

    public Builder toolCalls(List<Map<String, Object>> toolCalls) {
      this.toolCalls = toolCalls;
      return this;
    }

    public Builder usage(Map<String, Object> usage) {
      this.usage = usage;
      return this;
    }

    public ChatResponse build() {
      return new ChatResponse(this);
    }
  }
}
