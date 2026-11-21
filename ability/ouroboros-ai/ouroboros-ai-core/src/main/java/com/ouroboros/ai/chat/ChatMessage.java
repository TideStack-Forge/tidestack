package com.ouroboros.ai.chat;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.ai.support.AiModelCopies;

public final class ChatMessage {
  private final ChatMessageRole role;
  private final List<ChatContentPart> contentParts;

  private ChatMessage(Builder builder) {
    this.role = builder.role;
    this.contentParts = AiModelCopies.copyList(builder.contentParts);
  }

  public static Builder builder() {
    return new Builder();
  }

  public ChatMessageRole getRole() {
    return role;
  }

  public List<ChatContentPart> getContentParts() {
    return contentParts;
  }

  public static final class Builder {
    private ChatMessageRole role;
    private List<ChatContentPart> contentParts;

    private Builder() {
    }

    public Builder role(ChatMessageRole role) {
      this.role = role;
      return this;
    }

    public Builder contentParts(List<ChatContentPart> contentParts) {
      this.contentParts = contentParts;
      return this;
    }

    public Builder addContentPart(ChatContentPart contentPart) {
      if (this.contentParts == null) {
        this.contentParts = new ArrayList<>();
      }
      this.contentParts.add(contentPart);
      return this;
    }

    public ChatMessage build() {
      return new ChatMessage(this);
    }
  }
}
