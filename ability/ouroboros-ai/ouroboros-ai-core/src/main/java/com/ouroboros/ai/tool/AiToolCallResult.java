package com.ouroboros.ai.tool;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiToolCallResult {
  private final String toolName;
  private final boolean success;
  private final Map<String, Object> output;
  private final Object rawResult;
  private final String errorMessage;

  private AiToolCallResult(Builder builder) {
    this.toolName = builder.toolName;
    this.success = builder.success;
    this.output = AiModelCopies.copyMap(builder.output);
    this.rawResult = builder.rawResult;
    this.errorMessage = builder.errorMessage;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getToolName() {
    return toolName;
  }

  public boolean isSuccess() {
    return success;
  }

  public Map<String, Object> getOutput() {
    return output;
  }

  public Object getRawResult() {
    return rawResult;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public static final class Builder {
    private String toolName;
    private boolean success;
    private Map<String, Object> output;
    private Object rawResult;
    private String errorMessage;

    private Builder() {
    }

    public Builder toolName(String toolName) {
      this.toolName = toolName;
      return this;
    }

    public Builder success(boolean success) {
      this.success = success;
      return this;
    }

    public Builder output(Map<String, Object> output) {
      this.output = output;
      return this;
    }

    public Builder rawResult(Object rawResult) {
      this.rawResult = rawResult;
      return this;
    }

    public Builder errorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public AiToolCallResult build() {
      return new AiToolCallResult(this);
    }
  }
}
