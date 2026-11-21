package com.ouroboros.workflow.approval.web.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

public class StartFlowInstanceData {
  Map<String, Object> formData;
  Map<String, List<TaskAssignee>> taskAssignee;
  Map<String, Object> cc;
  private String businessRecordType;
  private String businessRecordId;
  private String idempotencyKey;

  public String getBusinessRecordType() { return businessRecordType; }
  public void setBusinessRecordType(String businessRecordType) { this.businessRecordType = businessRecordType; }
  public String getBusinessRecordId() { return businessRecordId; }
  public void setBusinessRecordId(String businessRecordId) { this.businessRecordId = businessRecordId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

  public Map<String, Object> getFormData() {
    return formData;
  }

  public void setFormData(Map<String, Object> formData) {
    this.formData = formData;
  }

  public Map<String, List<TaskAssignee>> getTaskAssignee() {
    return taskAssignee;
  }

  public void setTaskAssignee(Map<String, List<TaskAssignee>> taskAssignee) {
    this.taskAssignee = taskAssignee;
  }

  /**
   * 发起时抄送配置 {@code {"userIds": List<String>, "position": "START"|"FINISH"|"START_FINISH"}}（可空）。
   */
  public Map<String, Object> getCc() {
    return cc;
  }

  public void setCc(Map<String, Object> cc) {
    this.cc = cc;
  }

  public static class TaskAssignee {
    @JsonAlias(value = {"label", "name", "displayName"})
    private String label;

    @JsonAlias(value = {"value", "id", "userIdentity"})
    private String value;

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }
}
