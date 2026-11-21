package com.ouroboros.dev.app.model;

import java.time.Instant;

public class DevKeyMaterialDTO {
  private String appName;
  private String userIdentity;
  private String profileId;
  private String devKey;
  private String runtimeStrategy;
  private String createdAt;
  private String expiresAt;
  private String description;
  private String lastRotatedAt;
  private String createdBy;
  private String updatedBy;

  public static DevKeyMaterialDTO from(String appName, DebugKeyMaterial material) {
    if (material == null) {
      return null;
    }
    DevKeyMaterialDTO dto = new DevKeyMaterialDTO();
    dto.setAppName(appName);
    dto.setUserIdentity(material.getUserIdentity());
    dto.setProfileId(material.getProfileId());
    dto.setDevKey(material.getDebugKey());
    dto.setRuntimeStrategy(material.getRuntimeStrategy() == null ? null : material.getRuntimeStrategy().name());
    dto.setCreatedAt(formatInstant(material.getCreatedAt()));
    dto.setExpiresAt(formatInstant(material.getExpiresAt()));
    dto.setDescription(material.getDescription());
    dto.setLastRotatedAt(formatInstant(material.getLastRotatedAt()));
    dto.setCreatedBy(material.getCreatedBy());
    dto.setUpdatedBy(material.getUpdatedBy());
    return dto;
  }

  private static String formatInstant(Instant value) {
    return value == null ? null : value.toString();
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

  public String getDevKey() {
    return devKey;
  }

  public void setDevKey(String devKey) {
    this.devKey = devKey;
  }

  public String getRuntimeStrategy() {
    return runtimeStrategy;
  }

  public void setRuntimeStrategy(String runtimeStrategy) {
    this.runtimeStrategy = runtimeStrategy;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLastRotatedAt() {
    return lastRotatedAt;
  }

  public void setLastRotatedAt(String lastRotatedAt) {
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
}
