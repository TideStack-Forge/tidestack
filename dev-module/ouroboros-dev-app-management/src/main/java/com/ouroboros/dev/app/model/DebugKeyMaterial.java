package com.ouroboros.dev.app.model;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.ouroboros.dev.runtime.model.DebugAccessStrategy;

public class DebugKeyMaterial {
  private String appName;
  private String userIdentity;
  private String profileId;
  private String debugKey;
  private DebugAccessStrategy runtimeStrategy;
  @JsonSerialize(using = InstantToStringSerializer.class)
  @JsonDeserialize(using = InstantFromStringDeserializer.class)
  private Instant createdAt;
  @JsonSerialize(using = InstantToStringSerializer.class)
  @JsonDeserialize(using = InstantFromStringDeserializer.class)
  private Instant expiresAt;
  private String description;
  @JsonSerialize(using = InstantToStringSerializer.class)
  @JsonDeserialize(using = InstantFromStringDeserializer.class)
  private Instant lastRotatedAt;
  private String createdBy;
  private String updatedBy;

  public DebugKeyMaterial() {
  }

  private DebugKeyMaterial(Builder builder) {
    this.appName = builder.appName;
    this.userIdentity = builder.userIdentity;
    this.profileId = builder.profileId;
    this.debugKey = builder.debugKey;
    this.runtimeStrategy = builder.runtimeStrategy;
    this.createdAt = builder.createdAt;
    this.expiresAt = builder.expiresAt;
    this.description = builder.description;
    this.lastRotatedAt = builder.lastRotatedAt;
    this.createdBy = builder.createdBy;
    this.updatedBy = builder.updatedBy;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getUserIdentity() {
    return userIdentity;
  }

  public void setUserIdentity(String userIdentity) {
    this.userIdentity = userIdentity;
  }

  public String getProfileId() {
    return profileId;
  }

  public void setProfileId(String profileId) {
    this.profileId = profileId;
  }

  public String getDebugKey() {
    return debugKey;
  }

  public void setDebugKey(String debugKey) {
    this.debugKey = debugKey;
  }

  public DebugAccessStrategy getRuntimeStrategy() {
    return runtimeStrategy;
  }

  public void setRuntimeStrategy(DebugAccessStrategy runtimeStrategy) {
    this.runtimeStrategy = runtimeStrategy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getLastRotatedAt() {
    return lastRotatedAt;
  }

  public void setLastRotatedAt(Instant lastRotatedAt) {
    this.lastRotatedAt = lastRotatedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String appName;
    private String userIdentity;
    private String profileId;
    private String debugKey;
    private DebugAccessStrategy runtimeStrategy;
    private Instant createdAt;
    private Instant expiresAt;
    private String description;
    private Instant lastRotatedAt;
    private String createdBy;
    private String updatedBy;

    private Builder() {
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder userIdentity(String userIdentity) {
      this.userIdentity = userIdentity;
      return this;
    }

    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    public Builder debugKey(String debugKey) {
      this.debugKey = debugKey;
      return this;
    }

    public Builder runtimeStrategy(DebugAccessStrategy runtimeStrategy) {
      this.runtimeStrategy = runtimeStrategy;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder lastRotatedAt(Instant lastRotatedAt) {
      this.lastRotatedAt = lastRotatedAt;
      return this;
    }

    public Builder createdBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder updatedBy(String updatedBy) {
      this.updatedBy = updatedBy;
      return this;
    }

    public DebugKeyMaterial build() {
      return new DebugKeyMaterial(this);
    }
  }

  static class InstantToStringSerializer extends JsonSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws java.io.IOException {
      if (value == null) {
        gen.writeNull();
        return;
      }
      gen.writeString(value.toString());
    }
  }

  static class InstantFromStringDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException, JsonProcessingException {
      if (p.currentToken() == JsonToken.VALUE_NULL) {
        return null;
      }
      String value = p.getValueAsString();
      if (value == null || value.trim().isEmpty()) {
        return null;
      }
      return Instant.parse(value.trim());
    }
  }
}
