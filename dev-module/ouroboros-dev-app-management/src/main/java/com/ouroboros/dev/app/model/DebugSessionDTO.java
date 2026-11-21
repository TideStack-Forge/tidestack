package com.ouroboros.dev.app.model;

import java.time.Instant;
import java.util.Map;

public class DebugSessionDTO {
  private String sessionId;
  private String appName;
  private String profile;
  private String runtimeKind;
  private String backendType;
  private String targetType;
  private String transportMode;
  private String runtimeIdAtOpen;
  private Long runtimeGeneration;
  private String uploadBatchId;
  private String preparationSessionId;
  private String ownerUserId;
  private String ownerUserName;
  private String status;
  private String debugAccessStrategy;
  private String endpoint;
  private String host;
  private Integer port;
  private String bindingScope;
  private String transportType;
  private String clientAccessHint;
  private String reason;
  private Instant requestedAt;
  private Instant openedAt;
  private Instant firstConnectedAt;
  private Instant lastActiveAt;
  private Instant expiresAt;
  private Instant closedAt;
  private String closeReason;
  private Map<String, String> metadata;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getProfile() {
    return profile;
  }

  public void setProfile(String profile) {
    this.profile = profile;
  }

  public String getRuntimeKind() {
    return runtimeKind;
  }

  public void setRuntimeKind(String runtimeKind) {
    this.runtimeKind = runtimeKind;
  }

  public String getBackendType() {
    return backendType;
  }

  public void setBackendType(String backendType) {
    this.backendType = backendType;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public String getTransportMode() {
    return transportMode;
  }

  public void setTransportMode(String transportMode) {
    this.transportMode = transportMode;
  }

  public String getRuntimeIdAtOpen() {
    return runtimeIdAtOpen;
  }

  public void setRuntimeIdAtOpen(String runtimeIdAtOpen) {
    this.runtimeIdAtOpen = runtimeIdAtOpen;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public void setRuntimeGeneration(Long runtimeGeneration) {
    this.runtimeGeneration = runtimeGeneration;
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public void setUploadBatchId(String uploadBatchId) {
    this.uploadBatchId = uploadBatchId;
  }

  public String getPreparationSessionId() {
    return preparationSessionId;
  }

  public void setPreparationSessionId(String preparationSessionId) {
    this.preparationSessionId = preparationSessionId;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public String getOwnerUserName() {
    return ownerUserName;
  }

  public void setOwnerUserName(String ownerUserName) {
    this.ownerUserName = ownerUserName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDebugAccessStrategy() {
    return debugAccessStrategy;
  }

  public void setDebugAccessStrategy(String debugAccessStrategy) {
    this.debugAccessStrategy = debugAccessStrategy;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public String getBindingScope() {
    return bindingScope;
  }

  public void setBindingScope(String bindingScope) {
    this.bindingScope = bindingScope;
  }

  public String getTransportType() {
    return transportType;
  }

  public void setTransportType(String transportType) {
    this.transportType = transportType;
  }

  public String getClientAccessHint() {
    return clientAccessHint;
  }

  public void setClientAccessHint(String clientAccessHint) {
    this.clientAccessHint = clientAccessHint;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(Instant requestedAt) {
    this.requestedAt = requestedAt;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public void setOpenedAt(Instant openedAt) {
    this.openedAt = openedAt;
  }

  public Instant getFirstConnectedAt() {
    return firstConnectedAt;
  }

  public void setFirstConnectedAt(Instant firstConnectedAt) {
    this.firstConnectedAt = firstConnectedAt;
  }

  public Instant getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(Instant lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public String getCloseReason() {
    return closeReason;
  }

  public void setCloseReason(String closeReason) {
    this.closeReason = closeReason;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }
}
