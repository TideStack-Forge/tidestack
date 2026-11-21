package com.ouroboros.ai.chat;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class ToolResultPart implements ChatContentPart {
  private final String callId;
  private final String toolName;
  private final Map<String, Object> result;

  private ToolResultPart(Builder builder) {
    this.callId = builder.callId;
    this.toolName = builder.toolName;
    this.result = AiModelCopies.copyMap(builder.result);
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

  public Map<String, Object> getResult() {
    return result;
  }

  public static final class Builder {
    private String callId;
    private String toolName;
    private Map<String, Object> result;

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

    public Builder result(Map<String, Object> result) {
      this.result = result;
      return this;
    }

    public ToolResultPart build() {
      return new ToolResultPart(this);
    }
  }
}
