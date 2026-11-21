package com.ouroboros.data.initialization.runtime.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(fullName = "DataInitializationLedgerRecord", rawName = "sys_data_initialization_ledger", label = "数据初始化账本")
public class DataInitializationLedgerRecord {
  @Field
  @PrimaryKey
  private String id;

  @Field
  private String planId;

  @Field
  private String blockId;

  @Field
  private String sourceKind;

  @Field(size = 1024)
  private String sourceLocation;

  @Field
  private String targetModel;

  @Field
  private String assetVersion;

  @Field(size = 128)
  private String contentHash;

  @Field
  private String status;

  @Field(nullable = true)
  private LocalDateTime startedAt;

  @Field(nullable = true)
  private LocalDateTime finishedAt;

  @Field(nullable = true)
  private Map<String, Object> summary;

  @Field(nullable = true, size = 2048)
  private String errorMessage;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public String getBlockId() {
    return blockId;
  }

  public void setBlockId(String blockId) {
    this.blockId = blockId;
  }

  public String getSourceKind() {
    return sourceKind;
  }

  public void setSourceKind(String sourceKind) {
    this.sourceKind = sourceKind;
  }

  public String getSourceLocation() {
    return sourceLocation;
  }

  public void setSourceLocation(String sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  public String getTargetModel() {
    return targetModel;
  }

  public void setTargetModel(String targetModel) {
    this.targetModel = targetModel;
  }

  public String getAssetVersion() {
    return assetVersion;
  }

  public void setAssetVersion(String assetVersion) {
    this.assetVersion = assetVersion;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(LocalDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Map<String, Object> getSummary() {
    return summary;
  }

  public void setSummary(Map<String, Object> summary) {
    this.summary = summary;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
