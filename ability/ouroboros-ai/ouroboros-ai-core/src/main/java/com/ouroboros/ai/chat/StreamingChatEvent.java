package com.ouroboros.ai.chat;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class StreamingChatEvent {
  private final String type;
  private final String text;
  private final String delta;
  private final boolean done;
  private final Map<String, Object> usage;

  private StreamingChatEvent(Builder builder) {
    this.type = builder.type;
    this.text = builder.text;
    this.delta = builder.delta;
    this.done = builder.done;
    this.usage = AiModelCopies.copyMap(builder.usage);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getType() {
    return type;
  }

  public String getText() {
    return text;
  }

  public String getDelta() {
    return delta;
  }

  public boolean isDone() {
    return done;
  }

  public Map<String, Object> getUsage() {
    return usage;
  }

  public static final class Builder {
    private String type;
    private String text;
    private String delta;
    private boolean done;
    private Map<String, Object> usage;

    private Builder() {
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder text(String text) {
      this.text = text;
      return this;
    }

    public Builder delta(String delta) {
      this.delta = delta;
      return this;
    }

    public Builder done(boolean done) {
      this.done = done;
      return this;
    }

    public Builder usage(Map<String, Object> usage) {
      this.usage = usage;
      return this;
    }

    public StreamingChatEvent build() {
      return new StreamingChatEvent(this);
    }
  }
}
