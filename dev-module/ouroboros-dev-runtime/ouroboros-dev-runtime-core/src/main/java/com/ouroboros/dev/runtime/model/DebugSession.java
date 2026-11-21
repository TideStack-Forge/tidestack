package com.ouroboros.dev.runtime.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebugSession {
  private final String sessionId;
  private final RuntimeIdentityKey runtimeKey;
  private final DebugTunnelTargetType targetType;
  private final TransportMode transportMode;
  private final String runtimeIdAtOpen;
  private final Long runtimeGeneration;
  private final String uploadBatchId;
  private final String preparationSessionId;
  private final String ownerUserId;
  private final String ownerUserName;
  private final DebugSessionStatus status;
  private final DebugAccessStrategy debugAccessStrategy;
  private final DebugSessionBinding binding;
  private final Instant requestedAt;
  private final Instant openedAt;
  private final Instant firstConnectedAt;
  private final Instant lastActiveAt;
  private final Instant expiresAt;
  private final Instant closedAt;
  private final Integer idleTimeoutSeconds;
  private final Integer maxLifetimeSeconds;
  private final String reason;
  private final DebugSessionCloseReason closeReason;
  private final Map<String, String> metadata;

  private DebugSession(Builder builder) {
    this.sessionId = builder.sessionId;
    this.runtimeKey = builder.runtimeKey;
    this.targetType = resolveTargetType(builder.targetType, builder.metadata);
    this.transportMode = resolveTransportMode(builder.transportMode, this.targetType, builder.metadata);
    this.runtimeIdAtOpen = builder.runtimeIdAtOpen;
    this.runtimeGeneration = builder.runtimeGeneration;
    this.uploadBatchId = builder.uploadBatchId;
    this.preparationSessionId = builder.preparationSessionId;
    this.ownerUserId = builder.ownerUserId;
    this.ownerUserName = builder.ownerUserName;
    this.status = builder.status;
    this.debugAccessStrategy = builder.debugAccessStrategy;
    this.binding = builder.binding;
    this.requestedAt = builder.requestedAt;
    this.openedAt = builder.openedAt;
    this.firstConnectedAt = builder.firstConnectedAt;
    this.lastActiveAt = builder.lastActiveAt;
    this.expiresAt = builder.expiresAt;
    this.closedAt = builder.closedAt;
    this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
    this.maxLifetimeSeconds = builder.maxLifetimeSeconds;
    this.reason = builder.reason;
    this.closeReason = builder.closeReason;
    this.metadata = immutableCopy(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getSessionId() {
    return sessionId;
  }

  public RuntimeIdentityKey getRuntimeKey() {
    return runtimeKey;
  }

  public DebugTunnelTargetType getTargetType() {
    return targetType;
  }

  public TransportMode getTransportMode() {
    return transportMode;
  }

  public String getRuntimeIdAtOpen() {
    return runtimeIdAtOpen;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public String getPreparationSessionId() {
    return preparationSessionId;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public String getOwnerUserName() {
    return ownerUserName;
  }

  public DebugSessionStatus getStatus() {
    return status;
  }

  public DebugAccessStrategy getDebugAccessStrategy() {
    return debugAccessStrategy;
  }

  public DebugSessionBinding getBinding() {
    return binding;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getFirstConnectedAt() {
    return firstConnectedAt;
  }

  public Instant getLastActiveAt() {
    return lastActiveAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public Integer getIdleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public Integer getMaxLifetimeSeconds() {
    return maxLifetimeSeconds;
  }

  public String getReason() {
    return reason;
  }

  public DebugSessionCloseReason getCloseReason() {
    return closeReason;
  }

  public Map<String, String> getMetadata() {
    return metadata;
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

  private static TransportMode resolveTransportMode(
      TransportMode transportMode,
      DebugTunnelTargetType targetType,
      Map<String, String> metadata
  ) {
    if (transportMode != null) {
      return transportMode;
    }
    TransportMode metadataTransportMode = TransportMode.fromMetadata(metadata, "transportMode");
    if (metadataTransportMode != null) {
      return metadataTransportMode;
    }
    DebugTunnelTargetType resolvedTargetType = targetType == null ? DebugTunnelTargetType.defaultType() : targetType;
    return resolvedTargetType.getTransportMode();
  }

  public static class Builder {
    private String sessionId;
    private RuntimeIdentityKey runtimeKey;
    private DebugTunnelTargetType targetType;
    private TransportMode transportMode;
    private String runtimeIdAtOpen;
    private Long runtimeGeneration;
    private String uploadBatchId;
    private String preparationSessionId;
    private String ownerUserId;
    private String ownerUserName;
    private DebugSessionStatus status;
    private DebugAccessStrategy debugAccessStrategy;
    private DebugSessionBinding binding;
    private Instant requestedAt;
    private Instant openedAt;
    private Instant firstConnectedAt;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private Instant closedAt;
    private Integer idleTimeoutSeconds;
    private Integer maxLifetimeSeconds;
    private String reason;
    private DebugSessionCloseReason closeReason;
    private Map<String, String> metadata;

    private Builder() {
    }

    public Builder sessionId(String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    public Builder runtimeKey(RuntimeIdentityKey runtimeKey) {
      this.runtimeKey = runtimeKey;
      return this;
    }

    public Builder targetType(DebugTunnelTargetType targetType) {
      this.targetType = targetType;
      return this;
    }

    public Builder transportMode(TransportMode transportMode) {
      this.transportMode = transportMode;
      return this;
    }

    public Builder runtimeIdAtOpen(String runtimeIdAtOpen) {
      this.runtimeIdAtOpen = runtimeIdAtOpen;
      return this;
    }

    public Builder runtimeGeneration(Long runtimeGeneration) {
      this.runtimeGeneration = runtimeGeneration;
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

    public Builder ownerUserId(String ownerUserId) {
      this.ownerUserId = ownerUserId;
      return this;
    }

    public Builder ownerUserName(String ownerUserName) {
      this.ownerUserName = ownerUserName;
      return this;
    }

    public Builder status(DebugSessionStatus status) {
      this.status = status;
      return this;
    }

    public Builder debugAccessStrategy(DebugAccessStrategy debugAccessStrategy) {
      this.debugAccessStrategy = debugAccessStrategy;
      return this;
    }

    public Builder binding(DebugSessionBinding binding) {
      this.binding = binding;
      return this;
    }

    public Builder requestedAt(Instant requestedAt) {
      this.requestedAt = requestedAt;
      return this;
    }

    public Builder openedAt(Instant openedAt) {
      this.openedAt = openedAt;
      return this;
    }

    public Builder firstConnectedAt(Instant firstConnectedAt) {
      this.firstConnectedAt = firstConnectedAt;
      return this;
    }

    public Builder lastActiveAt(Instant lastActiveAt) {
      this.lastActiveAt = lastActiveAt;
      return this;
    }

    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public Builder closedAt(Instant closedAt) {
      this.closedAt = closedAt;
      return this;
    }

    public Builder idleTimeoutSeconds(Integer idleTimeoutSeconds) {
      this.idleTimeoutSeconds = idleTimeoutSeconds;
      return this;
    }

    public Builder maxLifetimeSeconds(Integer maxLifetimeSeconds) {
      this.maxLifetimeSeconds = maxLifetimeSeconds;
      return this;
    }

    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    public Builder closeReason(DebugSessionCloseReason closeReason) {
      this.closeReason = closeReason;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public DebugSession build() {
      return new DebugSession(this);
    }
  }
}
