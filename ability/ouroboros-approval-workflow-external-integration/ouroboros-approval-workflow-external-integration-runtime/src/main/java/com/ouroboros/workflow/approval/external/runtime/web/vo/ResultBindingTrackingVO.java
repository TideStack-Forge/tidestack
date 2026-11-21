package com.ouroboros.workflow.approval.external.runtime.web.vo;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;

/**
 * 结果绑定跟踪视图（设计文档 §结果跟踪：外部实例引用、当前状态、最终结果、最近同步时间和外部详情地址）。
 */
public class ResultBindingTrackingVO {

  private String trackingId;
  private String businessRecordType;
  private String businessRecordId;
  private String status;
  private String outcome;
  private String externalInstanceId;
  private String externalUrl;
  private String externalRevision;
  private LocalDateTime externalEventTime;
  private LocalDateTime finalResultAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ResultBindingTrackingVO fromEntity(ExternalResultBinding binding) {
    ResultBindingTrackingVO vo = new ResultBindingTrackingVO();
    vo.setTrackingId(binding.getTrackingId());
    vo.setBusinessRecordType(binding.getBusinessRecordType());
    vo.setBusinessRecordId(binding.getBusinessRecordId());
    vo.setStatus(binding.getStatus());
    vo.setOutcome(binding.getOutcome());
    vo.setExternalInstanceId(binding.getExternalInstanceId());
    vo.setExternalUrl(binding.getExternalUrl());
    vo.setExternalRevision(binding.getExternalRevision());
    vo.setExternalEventTime(binding.getExternalEventTime());
    vo.setFinalResultAt(binding.getFinalResultAt());
    vo.setCreatedAt(binding.getCreatedAt());
    vo.setUpdatedAt(binding.getUpdatedAt());
    return vo;
  }

  public String getTrackingId() {
    return trackingId;
  }

  public void setTrackingId(String trackingId) {
    this.trackingId = trackingId;
  }

  public String getBusinessRecordType() {
    return businessRecordType;
  }

  public void setBusinessRecordType(String businessRecordType) {
    this.businessRecordType = businessRecordType;
  }

  public String getBusinessRecordId() {
    return businessRecordId;
  }

  public void setBusinessRecordId(String businessRecordId) {
    this.businessRecordId = businessRecordId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getExternalInstanceId() {
    return externalInstanceId;
  }

  public void setExternalInstanceId(String externalInstanceId) {
    this.externalInstanceId = externalInstanceId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  public String getExternalRevision() {
    return externalRevision;
  }

  public void setExternalRevision(String externalRevision) {
    this.externalRevision = externalRevision;
  }

  public LocalDateTime getExternalEventTime() {
    return externalEventTime;
  }

  public void setExternalEventTime(LocalDateTime externalEventTime) {
    this.externalEventTime = externalEventTime;
  }

  public LocalDateTime getFinalResultAt() {
    return finalResultAt;
  }

  public void setFinalResultAt(LocalDateTime finalResultAt) {
    this.finalResultAt = finalResultAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
