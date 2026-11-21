package com.ouroboros.ai.chat;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class ToolCallPart implements ChatContentPart {
  private final String callId;
  private final String toolName;
  private final Map<String, Object> arguments;

  private ToolCallPart(Builder builder) {
    this.callId = builder.callId;
    this.toolName = builder.toolName;
    this.arguments = AiModelCopies.copyMap(builder.arguments);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getCallId() {
    return callId;
  }

  public String getToolName() {
    return toolName;
  }

  public Map<String, Object> getArguments() {
    return arguments;
  }

  public static final class Builder {
    private String callId;
    private String toolName;
    private Map<String, Object> arguments;

    private Builder() {
    }

    public Builder callId(String callId) {
      this.callId = callId;
      return this;
    }

    public Builder toolName(String toolName) {
      this.toolName = toolName;
      return this;
    }

    public Builder arguments(Map<String, Object> arguments) {
      this.arguments = arguments;
      return this;
    }

    public ToolCallPart build() {
      return new ToolCallPart(this);
    }
  }
}
