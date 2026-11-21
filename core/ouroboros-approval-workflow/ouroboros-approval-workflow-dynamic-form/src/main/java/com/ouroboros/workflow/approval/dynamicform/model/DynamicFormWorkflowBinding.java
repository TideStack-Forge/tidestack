package com.ouroboros.workflow.approval.dynamicform.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

@Model(rawName = "t_approval_dynamic_form_binding", label = "审批流动态表单绑定")
@UniqueConstraint(name = "uk_awf_dynamic_form_binding", fields = {"instanceId", "formKey", "formId"})
@DataPlugin(name = "DuplicateDataChecker")
public class DynamicFormWorkflowBinding {
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field(size = 100)
  private String instanceId;

  @Field(size = 100)
  private String formKey;

  @Field(size = 100)
  private String formId;

  @Field
  private LocalDateTime createdAt;

  @Field(size = 50)
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(size = 50, nullable = true)
  private String updatedBy;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
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
}
