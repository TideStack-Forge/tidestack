package com.ouroboros.businessmodel.config;

import com.ouroboros.util.SPI;

public interface BasicAuditFieldsConfiguration {
  interface FieldSetting {
    String getRawName();

    default boolean isDisabled() {
      return false;
    }
  }

  FieldSetting getCreatedAt();

  FieldSetting getCreatedBy();

  FieldSetting getUpdatedAt();

  FieldSetting getUpdatedBy();

  FieldSetting getDeletedAt();

  FieldSetting getDeletedBy();

  FieldSetting getIsDeleted();

  static BasicAuditFieldsConfiguration getConfiguration() {
    BasicAuditFieldsConfiguration config = SPI.getCachedPrimaryService(BasicAuditFieldsConfiguration.class);
    return config == null ? new DefaultBasicAuditFieldsConfiguration() : config;
  }
}
