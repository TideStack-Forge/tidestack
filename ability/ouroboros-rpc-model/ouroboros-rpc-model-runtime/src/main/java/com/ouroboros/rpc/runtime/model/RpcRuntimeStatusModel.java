package com.ouroboros.rpc.runtime.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_sys_rpc_runtime_status", label = "RPC 运行时状态")
public class RpcRuntimeStatusModel implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey
  private String modelName;

  @Field(nullable = true)
  private String tenantId;

  @Field(nullable = true)
  private String availability;

  @Field(nullable = true)
  private LocalDateTime lastHealthCheckAt;

  @Field(nullable = true)
  private Boolean lastHealthCheckSuccess;

  @Field(nullable = true)
  private LocalDateTime lastInvokeAt;

  @Field(nullable = true)
  private LocalDateTime lastSuccessAt;

  @Field(nullable = true)
  private LocalDateTime lastFailureAt;

  @Field(nullable = true)
  private String lastFailureReason;

  @Field(nullable = true)
  private Long consecutiveFailureCount;

  @Field(nullable = true)
  private Long successCount;

  @Field(nullable = true)
  private Long failureCount;

  @Field(nullable = true)
  private Long avgLatencyMs;

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getAvailability() {
    return availability;
  }

  public void setAvailability(String availability) {
    this.availability = availability;
  }

  public LocalDateTime getLastHealthCheckAt() {
    return lastHealthCheckAt;
  }

  public void setLastHealthCheckAt(LocalDateTime lastHealthCheckAt) {
    this.lastHealthCheckAt = lastHealthCheckAt;
  }

  public Boolean getLastHealthCheckSuccess() {
    return lastHealthCheckSuccess;
  }

  public void setLastHealthCheckSuccess(Boolean lastHealthCheckSuccess) {
    this.lastHealthCheckSuccess = lastHealthCheckSuccess;
  }

  public LocalDateTime getLastInvokeAt() {
    return lastInvokeAt;
  }

  public void setLastInvokeAt(LocalDateTime lastInvokeAt) {
    this.lastInvokeAt = lastInvokeAt;
  }

  public LocalDateTime getLastSuccessAt() {
    return lastSuccessAt;
  }

  public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
    this.lastSuccessAt = lastSuccessAt;
  }

  public LocalDateTime getLastFailureAt() {
    return lastFailureAt;
  }

  public void setLastFailureAt(LocalDateTime lastFailureAt) {
    this.lastFailureAt = lastFailureAt;
  }

  public String getLastFailureReason() {
    return lastFailureReason;
  }

  public void setLastFailureReason(String lastFailureReason) {
    this.lastFailureReason = lastFailureReason;
  }

  public Long getConsecutiveFailureCount() {
    return consecutiveFailureCount;
  }

  public void setConsecutiveFailureCount(Long consecutiveFailureCount) {
    this.consecutiveFailureCount = consecutiveFailureCount;
  }

  public Long getSuccessCount() {
    return successCount;
  }

  public void setSuccessCount(Long successCount) {
    this.successCount = successCount;
  }

  public Long getFailureCount() {
    return failureCount;
  }

  public void setFailureCount(Long failureCount) {
    this.failureCount = failureCount;
  }

  public Long getAvgLatencyMs() {
    return avgLatencyMs;
  }

  public void setAvgLatencyMs(Long avgLatencyMs) {
    this.avgLatencyMs = avgLatencyMs;
  }
}
