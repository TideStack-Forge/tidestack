package com.ouroboros.businesslog.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

/**
 * @author liansz
 **/
@Model(rawName = "t_sys_business_operation_template", label = "业务日志模板")
@AuditFields
@LogicalDelete
public class BusinessOperationTemplateModel implements BusinessOperationTemplate {

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * HTTP 方法
   */
  @Field
  private String httpMethod;

  /**
   * URL
   */
  @Field
  private String url;

  /**
   * 模块名
   */
  @Field
  private String moduleName;

  /**
   * 操作名
   */
  @Field
  private String operation;

  /**
   * 日志级别
   */
  @Field
  private String level;

  /**
   * 业务键
   */
  @Field(nullable = true)
  private String businessKeyName;

  /**
   * 摘要模板
   */
  @Field(nullable = true)
  private String summaryTemplate;

  /**
   * 脱敏字段
   */
  @Field(nullable = true)
  private List<String> sensitiveFields;

  /**
   * 是否保存响应体内容
   */
  @Field(nullable = true)
  private Boolean shouldSaveResponseBody;

  /**
   * 创建者
   */
  @Field(nullable = true)
  private String createdBy;

  /**
   * 创建时间
   */
  @Field(nullable = true)
  private LocalDateTime createdAt;

  /**
   * 更新者
   */
  @Field(nullable = true)
  private String updatedBy;

  /**
   * 更新时间
   */
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  /**
   * 删除者
   */
  @Field(nullable = true)
  private String deletedBy;

  /**
   * 删除时间
   */
  @Field(nullable = true)
  private LocalDateTime deletedAt;

  /**
   * 软删除状态
   */
  @JsonProperty("isDeleted")
  @Field(nullable = true, type = "Boolean")
  private boolean isDeleted;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  @Override
  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  @Override
  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  @Override
  public String getBusinessKeyName() {
    return businessKeyName;
  }

  public void setBusinessKeyName(String businessKeyName) {
    this.businessKeyName = businessKeyName;
  }

  @Override
  public String getSummaryTemplate() {
    return summaryTemplate;
  }

  public void setSummaryTemplate(String summaryTemplate) {
    this.summaryTemplate = summaryTemplate;
  }

  @Override
  public List<String> getSensitiveFields() {
    return sensitiveFields;
  }

  public void setSensitiveFields(List<String> sensitiveFields) {
    this.sensitiveFields = sensitiveFields;
  }

  @Override
  public Boolean isShouldSaveResponseBody() {
    return shouldSaveResponseBody;
  }

  public void setShouldSaveResponseBody(Boolean shouldSaveResponseBody) {
    this.shouldSaveResponseBody = shouldSaveResponseBody;
  }

  public Boolean getShouldSaveResponseBody() {
    return shouldSaveResponseBody;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(String deletedBy) {
    this.deletedBy = deletedBy;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }
}
