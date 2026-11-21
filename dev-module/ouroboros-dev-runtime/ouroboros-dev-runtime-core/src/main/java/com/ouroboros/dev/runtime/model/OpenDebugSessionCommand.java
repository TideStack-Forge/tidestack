package com.ouroboros.dev.runtime.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenDebugSessionCommand {
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final DebugTunnelTargetType targetType;
  private final Long expectedGeneration;
  private final String ownerUserId;
  private final String ownerUserName;
  private final Integer maxLifetimeSeconds;
  private final Integer idleTimeoutSeconds;
  private final String reason;
  private final Map<String, String> metadata;
  private final String requestedSessionId;
  private final String uploadBatchId;
  private final String preparationSessionId;

  private OpenDebugSessionCommand(Builder builder) {
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.targetType = resolveTargetType(builder.targetType, builder.metadata);
    this.expectedGeneration = builder.expectedGeneration;
    this.ownerUserId = builder.ownerUserId;
    this.ownerUserName = builder.ownerUserName;
    this.maxLifetimeSeconds = builder.maxLifetimeSeconds;
    this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
    this.reason = builder.reason;
    this.metadata = immutableCopy(builder.metadata);
    this.requestedSessionId = builder.requestedSessionId;
    this.uploadBatchId = builder.uploadBatchId;
    this.preparationSessionId = builder.preparationSessionId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAppName() {
    return appName;
  }

  public String getProfile() {
    return profile;
  }

  public RuntimeKind getRuntimeKind() {
    return runtimeKind;
  }

  public BackendType getBackendType() {
    return backendType;
  }

  public DebugTunnelTargetType getTargetType() {
    return targetType;
  }

  public Long getExpectedGeneration() {
    return expectedGeneration;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public String getOwnerUserName() {
    return ownerUserName;
  }

  public Integer getMaxLifetimeSeconds() {
    return maxLifetimeSeconds;
  }

  public Integer getIdleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public String getReason() {
    return reason;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public String getRequestedSessionId() {
    return requestedSessionId;
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public String getPreparationSessionId() {
    return preparationSessionId;
  }

  public RuntimeIdentityKey getRuntimeKey() {
    return RuntimeIdentityKey.of(appName, profile, runtimeKind, backendType);
  }

  private static Map<String, String> immutableCopy(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
  }

  private static DebugTunnelTargetType resolveTargetType(DebugTunnelTargetType targetType, Map<String, String> metadata) {
    DebugTunnelTargetType metadataTargetType = DebugTunnelTargetType.fromMetadata(metadata);
    if (targetType != null) {
      return targetType;
    }
    return metadataTargetType == null ? DebugTunnelTargetType.defaultType() : metadataTargetType;
  }

  public static class Builder {
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private DebugTunnelTargetType targetType;
    private Long expectedGeneration;
    private String ownerUserId;
    private String ownerUserName;
    private Integer maxLifetimeSeconds;
    private Integer idleTimeoutSeconds;
    private String reason;
    private Map<String, String> metadata;
    private String requestedSessionId;
    private String uploadBatchId;
    private String preparationSessionId;

    private Builder() {
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder profile(String profile) {
      this.profile = profile;
      return this;
    }

    public Builder runtimeKind(RuntimeKind runtimeKind) {
      this.runtimeKind = runtimeKind;
      return this;
    }

    public Builder backendType(BackendType backendType) {
      this.backendType = backendType;
      return this;
    }

    public Builder targetType(DebugTunnelTargetType targetType) {
      this.targetType = targetType;
      return this;
    }

    public Builder expectedGeneration(Long expectedGeneration) {
      this.expectedGeneration = expectedGeneration;
      return this;
    }

    public Builder ownerUserId(String ownerUserId) {
      this.ownerUserId = ownerUserId;
      return this;
    }

    public Builder ownerUserName(String ownerUserName) {
      this.ownerUserName = ownerUserName;
      return this;
    }

    public Builder maxLifetimeSeconds(Integer maxLifetimeSeconds) {
      this.maxLifetimeSeconds = maxLifetimeSeconds;
      return this;
    }

    public Builder idleTimeoutSeconds(Integer idleTimeoutSeconds) {
      this.idleTimeoutSeconds = idleTimeoutSeconds;
      return this;
    }

    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder requestedSessionId(String requestedSessionId) {
      this.requestedSessionId = requestedSessionId;
      return this;
    }

    public Builder uploadBatchId(String uploadBatchId) {
      this.uploadBatchId = uploadBatchId;
      return this;
    }

    public Builder preparationSessionId(String preparationSessionId) {
      this.preparationSessionId = preparationSessionId;
      return this;
    }

    public OpenDebugSessionCommand build() {
      return new OpenDebugSessionCommand(this);
    }
  }
}
