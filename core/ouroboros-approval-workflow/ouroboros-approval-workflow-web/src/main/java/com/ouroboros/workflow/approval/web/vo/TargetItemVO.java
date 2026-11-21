package com.ouroboros.workflow.approval.web.vo;

public class TargetItemVO {
  String id;
  String label;
  String type;
  Boolean disabled;
  String disabledReason;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getDisabled() {
    return disabled;
  }

  public void setDisabled(Boolean disabled) {
    this.disabled = disabled;
  }

  public String getDisabledReason() {
    return disabledReason;
  }

  public void setDisabledReason(String disabledReason) {
    this.disabledReason = disabledReason;
  }
}
