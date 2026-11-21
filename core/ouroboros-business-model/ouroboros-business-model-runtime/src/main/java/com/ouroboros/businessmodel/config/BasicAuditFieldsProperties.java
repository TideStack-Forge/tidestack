package com.ouroboros.businessmodel.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ouroboros.business-model.basic-audit-fields")
public class BasicAuditFieldsProperties implements BasicAuditFieldsConfiguration {

  private FieldSettingImpl createdAt = new FieldSettingImpl("created_at");
  private FieldSettingImpl createdBy = new FieldSettingImpl("created_by");
  private FieldSettingImpl updatedAt = new FieldSettingImpl("updated_at");
  private FieldSettingImpl updatedBy = new FieldSettingImpl("updated_by");
  private FieldSettingImpl deletedAt = new FieldSettingImpl("deleted_at");
  private FieldSettingImpl deletedBy = new FieldSettingImpl("deleted_by");
  private FieldSettingImpl isDeleted = new FieldSettingImpl("is_deleted");

  public static class FieldSettingImpl implements FieldSetting {
    private String rawName;
    private boolean disabled = false;

    public FieldSettingImpl() {
    }  // 需要无参构造用于绑定

    public FieldSettingImpl(String rawName) {
      this.rawName = rawName;
    }

    public void setRawName(String rawName) {
      this.rawName = rawName;
    }

    @Override
    public String getRawName() {
      return rawName;
    }

    public void setDisabled(boolean disabled) {
      this.disabled = disabled;
    }

    @Override
    public boolean isDisabled() {
      return disabled;
    }
  }

  // getters 和 setters（用于 Spring 绑定）
  @Override
  public FieldSetting getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(FieldSettingImpl createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public FieldSetting getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(FieldSettingImpl createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public FieldSetting getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(FieldSettingImpl updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public FieldSetting getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(FieldSettingImpl updatedBy) {
    this.updatedBy = updatedBy;
  }

  @Override
  public FieldSetting getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(FieldSettingImpl deletedAt) {
    this.deletedAt = deletedAt;
  }

  @Override
  public FieldSetting getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(FieldSettingImpl deletedBy) {
    this.deletedBy = deletedBy;
  }

  @Override
  public FieldSetting getIsDeleted() {
    return isDeleted;
  }

  public void setIsDeleted(FieldSettingImpl isDeleted) {
    this.isDeleted = isDeleted;
  }
}
