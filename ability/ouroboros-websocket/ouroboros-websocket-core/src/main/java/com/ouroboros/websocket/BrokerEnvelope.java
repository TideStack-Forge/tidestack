package com.ouroboros.websocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrokerEnvelope {
  private final BrokerEnvelopeType type;
  private final String destination;
  private final String user;
  private final String sessionId;
  private final Object payload;
  private final Map<String, Object> headers;
  private final String messageId;
  private final String sourceInstanceId;

  private BrokerEnvelope(Builder builder) {
    this.type = builder.type;
    this.destination = builder.destination;
    this.user = builder.user;
    this.sessionId = builder.sessionId;
    this.payload = builder.payload;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    this.messageId = builder.messageId;
    this.sourceInstanceId = builder.sourceInstanceId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public BrokerEnvelopeType getType() {
    return type;
  }

  public String getDestination() {
    return destination;
  }

  public String getUser() {
    return user;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Object getPayload() {
    return payload;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getSourceInstanceId() {
    return sourceInstanceId;
  }

  public static final class Builder {
    private BrokerEnvelopeType type;
    private String destination;
    private String user;
    private String sessionId;
    private Object payload;
    private Map<String, Object> headers = new LinkedHashMap<>();
    private String messageId;
    private String sourceInstanceId;

    private Builder() {
    }

    public Builder type(BrokerEnvelopeType type) {
      this.type = type;
      return this;
    }

    public Builder destination(String destination) {
      this.destination = destination;
      return this;
    }

    public Builder user(String user) {
      this.user = user;
      return this;
    }

    public Builder sessionId(String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    public Builder payload(Object payload) {
      this.payload = payload;
      return this;
    }

    public Builder headers(Map<String, Object> headers) {
      this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
      return this;
    }

    public Builder putHeader(String name, Object value) {
      this.headers.put(name, value);
      return this;
    }

    public Builder messageId(String messageId) {
      this.messageId = messageId;
      return this;
    }

    public Builder sourceInstanceId(String sourceInstanceId) {
      this.sourceInstanceId = sourceInstanceId;
      return this;
    }

    public BrokerEnvelope build() {
      return new BrokerEnvelope(this);
    }
  }
}
