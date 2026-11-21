package com.ouroboros.dev.app.model;

import java.util.List;

public class ResolvedRuntimeLockDTO {
  private String runtimeKind;
  private String status;
  private String resolvedAt;
  private String savedAt;
  private String baseBomVersion;
  private List<String> artifacts;

  public String getRuntimeKind() {
    return runtimeKind;
  }

  public void setRuntimeKind(String runtimeKind) {
    this.runtimeKind = runtimeKind;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(String resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public String getSavedAt() {
    return savedAt;
  }

  public void setSavedAt(String savedAt) {
    this.savedAt = savedAt;
  }

  public String getBaseBomVersion() {
    return baseBomVersion;
  }

  public void setBaseBomVersion(String baseBomVersion) {
    this.baseBomVersion = baseBomVersion;
  }

  public List<String> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(List<String> artifacts) {
    this.artifacts = artifacts;
  }
}
