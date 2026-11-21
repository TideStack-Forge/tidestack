package com.ouroboros.rpc.runtime.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.util.JsonBag;

@Model(rawName = "t_sys_rpc_runtime_policy", label = "RPC 运行时策略")
public class RpcRuntimePolicyModel implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey
  private String modelName;

  @Field(nullable = true)
  private String tenantId;

  @Field(nullable = true)
  private Boolean enabled;

  @Field(nullable = true)
  private Boolean loggingEnabled;

  @Field(nullable = true)
  private Boolean logRequestBody;

  @Field(nullable = true)
  private Boolean logResponseBody;

  @Field(nullable = true)
  private JsonBag maskHeaders;

  @Field(nullable = true)
  private JsonBag maskFields;

  @Field(nullable = true)
  private Boolean metricsEnabled;

  @Field(nullable = true)
  private Boolean availabilityEnabled;

  @Field(nullable = true)
  private String forcedAvailabilityStatus;

  @Field(nullable = true)
  private Long timeoutOverrideMs;

  @Field(nullable = true)
  private Integer retryOverride;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private String updatedBy;

  private List<String> normalizationWarnings = Collections.emptyList();

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

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getLoggingEnabled() {
    return loggingEnabled;
  }

  public void setLoggingEnabled(Boolean loggingEnabled) {
    this.loggingEnabled = loggingEnabled;
  }

  public Boolean getLogRequestBody() {
    return logRequestBody;
  }

  public void setLogRequestBody(Boolean logRequestBody) {
    this.logRequestBody = logRequestBody;
  }

  public Boolean getLogResponseBody() {
    return logResponseBody;
  }

  public void setLogResponseBody(Boolean logResponseBody) {
    this.logResponseBody = logResponseBody;
  }

  public JsonBag getMaskHeaders() {
    return maskHeaders;
  }

  public void setMaskHeaders(JsonBag maskHeaders) {
    this.maskHeaders = maskHeaders;
  }

  public JsonBag getMaskFields() {
    return maskFields;
  }

  public void setMaskFields(JsonBag maskFields) {
    this.maskFields = maskFields;
  }

  public Boolean getMetricsEnabled() {
    return metricsEnabled;
  }

  public void setMetricsEnabled(Boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
  }

  public Boolean getAvailabilityEnabled() {
    return availabilityEnabled;
  }

  public void setAvailabilityEnabled(Boolean availabilityEnabled) {
    this.availabilityEnabled = availabilityEnabled;
  }

  public String getForcedAvailabilityStatus() {
    return forcedAvailabilityStatus;
  }

  public void setForcedAvailabilityStatus(String forcedAvailabilityStatus) {
    this.forcedAvailabilityStatus = forcedAvailabilityStatus;
  }

  public Long getTimeoutOverrideMs() {
    return timeoutOverrideMs;
  }

  public void setTimeoutOverrideMs(Long timeoutOverrideMs) {
    this.timeoutOverrideMs = timeoutOverrideMs;
  }

  public Integer getRetryOverride() {
    return retryOverride;
  }

  public void setRetryOverride(Integer retryOverride) {
    this.retryOverride = retryOverride;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public List<String> getNormalizationWarnings() {
    return normalizationWarnings == null ? Collections.emptyList() : normalizationWarnings;
  }

  public void setNormalizationWarnings(List<String> normalizationWarnings) {
    this.normalizationWarnings = normalizationWarnings == null ? Collections.emptyList() : normalizationWarnings;
  }

  public Boolean getHasNormalizationWarnings() {
    return !getNormalizationWarnings().isEmpty();
  }
}
