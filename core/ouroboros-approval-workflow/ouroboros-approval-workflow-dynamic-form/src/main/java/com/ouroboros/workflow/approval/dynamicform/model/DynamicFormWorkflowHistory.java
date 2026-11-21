package com.ouroboros.workflow.approval.dynamicform.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_approval_dynamic_form_history", label = "审批流动态表单历史")
public class DynamicFormWorkflowHistory {
  public static final String ACTION_START = "START";
  public static final String ACTION_UPDATE = "UPDATE";

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field(size = 100)
  private String bindingId;

  @Field(size = 100)
  private String instanceId;

  @Field(size = 100, nullable = true)
  private String taskId;

  @Field(size = 100)
  private String formKey;

  @Field(size = 100)
  private String formId;

  @Field(size = 20)
  private String action;

  @Field(type = "Json", nullable = true)
  private Map<String, Object> beforeData;

  @Field(type = "Json", nullable = true)
  private Map<String, Object> afterData;

  @Field(type = "Json", nullable = true)
  private Map<String, Object> changedData;

  @Field
  private LocalDateTime createdAt;

  @Field(size = 50)
  private String createdBy;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBindingId() {
    return bindingId;
  }

  public void setBindingId(String bindingId) {
    this.bindingId = bindingId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getFormKey() {
    return formKey;
  }

  public void setFormKey(String formKey) {
    this.formKey = formKey;
  }

  public String getFormId() {
    return formId;
  }

  public void setFormId(String formId) {
    this.formId = formId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public Map<String, Object> getBeforeData() {
    return beforeData;
  }

  public void setBeforeData(Map<String, Object> beforeData) {
    this.beforeData = beforeData;
  }

  public Map<String, Object> getAfterData() {
    return afterData;
  }

  public void setAfterData(Map<String, Object> afterData) {
    this.afterData = afterData;
  }

  public Map<String, Object> getChangedData() {
    return changedData;
  }

  public void setChangedData(Map<String, Object> changedData) {
    this.changedData = changedData;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }
}
