package com.ouroboros.documentmanagement.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档管理模块配置属性
 */
@ConfigurationProperties(prefix = "ouroboros.document-management")
public class DocumentManagementProperties {

  /** 是否启用文档管理模块 */
  private boolean enabled = true;

  /** 存储类型：filesystem（本地文件系统）、s3（AWS S3）、oss（阿里云OSS） */
  private String storageType = "filesystem";

  /** 持久化类型：typed-model（TypedDataModel）、jpa（JPA） */
  private String persistenceType = "typed-model";

  /** 是否启用 REST API */
  private boolean enableWebApi = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getStorageType() {
    return storageType;
  }

  public void setStorageType(String storageType) {
    this.storageType = storageType;
  }

  public String getPersistenceType() {
    return persistenceType;
  }

  public void setPersistenceType(String persistenceType) {
    this.persistenceType = persistenceType;
  }

  public boolean isEnableWebApi() {
    return enableWebApi;
  }

  public void setEnableWebApi(boolean enableWebApi) {
    this.enableWebApi = enableWebApi;
  }
}
