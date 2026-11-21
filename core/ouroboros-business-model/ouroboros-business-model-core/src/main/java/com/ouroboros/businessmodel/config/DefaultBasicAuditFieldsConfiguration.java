package com.ouroboros.businessmodel.config;

public class DefaultBasicAuditFieldsConfiguration implements BasicAuditFieldsConfiguration {
  private final FieldSetting createdAt = new DefaultFieldSetting("created_at");
  private final FieldSetting createdBy = new DefaultFieldSetting("created_by");
  private final FieldSetting updatedAt = new DefaultFieldSetting("updated_at");
  private final FieldSetting updatedBy = new DefaultFieldSetting("updated_by");
  private final FieldSetting deletedAt = new DefaultFieldSetting("deleted_at");
  private final FieldSetting deletedBy = new DefaultFieldSetting("deleted_by");
  private final FieldSetting isDeleted = new DefaultFieldSetting("is_deleted");

  static class DefaultFieldSetting implements FieldSetting {
    private final String rawName;

    private DefaultFieldSetting(String rawName) {
      this.rawName = rawName;
    }

    @Override
    public String getRawName() {
      return rawName;
    }
  }

  @Override
  public FieldSetting getCreatedAt() {
    return createdAt;
  }

  @Override
  public FieldSetting getCreatedBy() {
    return createdBy;
  }

  @Override
  public FieldSetting getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public FieldSetting getUpdatedBy() {
    return updatedBy;
  }

  @Override
  public FieldSetting getDeletedAt() {
    return deletedAt;
  }

  @Override
  public FieldSetting getDeletedBy() {
    return deletedBy;
  }

  @Override
  public FieldSetting getIsDeleted() {
    return isDeleted;
  }
}
