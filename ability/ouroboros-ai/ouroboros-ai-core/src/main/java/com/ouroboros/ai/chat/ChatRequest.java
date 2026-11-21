package com.ouroboros.ai.chat;

import java.util.List;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.support.AiModelCopies;

public final class ChatRequest {
  private final String modelId;
  private final List<ChatMessage> messageList;
  private final AiOptions options;
  private final List<String> toolNames;
  private final List<String> knowledgeBaseIds;
  private final List<String> mcpEndpointIds;
  private final boolean stream;
  private final AiInvocationContext invocationContext;

  private ChatRequest(Builder builder) {
    this.modelId = builder.modelId;
    this.messageList = AiModelCopies.copyList(builder.messageList);
    this.options = builder.options;
    this.toolNames = AiModelCopies.copyStringList(builder.toolNames);
    this.knowledgeBaseIds = AiModelCopies.copyStringList(builder.knowledgeBaseIds);
    this.mcpEndpointIds = AiModelCopies.copyStringList(builder.mcpEndpointIds);
    this.stream = builder.stream;
    this.invocationContext = builder.invocationContext;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getModelId() {
    return modelId;
  }

  public List<ChatMessage> getMessageList() {
    return messageList;
  }

  public AiOptions getOptions() {
    return options;
  }

  public List<String> getToolNames() {
    return toolNames;
  }

  public List<String> getKnowledgeBaseIds() {
    return knowledgeBaseIds;
  }

  public List<String> getMcpEndpointIds() {
    return mcpEndpointIds;
  }

  public boolean isStream() {
    return stream;
  }

  public AiInvocationContext getInvocationContext() {
    return invocationContext;
  }

  public static final class Builder {
    private String modelId;
    private List<ChatMessage> messageList;
    private AiOptions options;
    private List<String> toolNames;
    private List<String> knowledgeBaseIds;
    private List<String> mcpEndpointIds;
    private boolean stream;
    private AiInvocationContext invocationContext;

    private Builder() {
    }

    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    public Builder message(ChatMessage message) {
      if (message == null) {
        return this;
      }
      if (this.messageList == null) {
        this.messageList = new java.util.ArrayList<>();
      }
      this.messageList.add(message);
      return this;
    }

    public Builder options(AiOptions options) {
      this.options = options;
      return this;
    }

    public Builder toolNames(List<String> toolNames) {
      this.toolNames = toolNames;
      return this;
    }

    public Builder knowledgeBaseIds(List<String> knowledgeBaseIds) {
      this.knowledgeBaseIds = knowledgeBaseIds;
      return this;
    }

    public Builder mcpEndpointIds(List<String> mcpEndpointIds) {
      this.mcpEndpointIds = mcpEndpointIds;
      return this;
    }

    public Builder stream(boolean stream) {
      this.stream = stream;
      return this;
    }

    public Builder invocationContext(AiInvocationContext invocationContext) {
      this.invocationContext = invocationContext;
      return this;
    }

    public ChatRequest build() {
      return new ChatRequest(this);
    }
  }
}
