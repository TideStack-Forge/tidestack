package com.ouroboros.dev.app.model;

import java.util.Map;

public class OpenDebugSessionRequest {
  private String appName;
  private String profile;
  private String runtimeKind;
  private String backendType;
  private Long expectedGeneration;
  private Integer maxLifetimeSeconds;
  private Integer idleTimeoutSeconds;
  private String reason;
  private String uploadBatchId;
  private Map<String, String> metadata;

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

  public Long getExpectedGeneration() {
    return expectedGeneration;
  }

  public void setExpectedGeneration(Long expectedGeneration) {
    this.expectedGeneration = expectedGeneration;
  }

  public Integer getMaxLifetimeSeconds() {
    return maxLifetimeSeconds;
  }

  public void setMaxLifetimeSeconds(Integer maxLifetimeSeconds) {
    this.maxLifetimeSeconds = maxLifetimeSeconds;
  }

  public Integer getIdleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public void setIdleTimeoutSeconds(Integer idleTimeoutSeconds) {
    this.idleTimeoutSeconds = idleTimeoutSeconds;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public void setUploadBatchId(String uploadBatchId) {
    this.uploadBatchId = uploadBatchId;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }
}
