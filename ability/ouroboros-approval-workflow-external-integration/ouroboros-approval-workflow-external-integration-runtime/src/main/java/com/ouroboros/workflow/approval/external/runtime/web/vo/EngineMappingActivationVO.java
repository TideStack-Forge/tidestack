package com.ouroboros.workflow.approval.external.runtime.web.vo;

/**
 * 映射激活结果视图（设计文档 §流程映射管理：激活接口必须返回映射版本、验证摘要、适配器和外部定义摘要）。
 */
public class EngineMappingActivationVO {

  private String id;
  private Integer version;
  private String validationMessage;
  private String adapterId;
  private String adapterDisplayName;
  private String externalDefinitionId;
  private String externalDefinitionVersion;
  private String externalDefinitionSchemaHash;
  /**
   * 执行归属提示：{@code authority=OUROBOROS} 时非空（"本地内置引擎仍负责执行"）。
   *
   * <p>字段名此前是 {@code mirrorNotice}——镜像投影（`ExternalTaskCenterMirror` / mirror outbox /
   * `MIRROR_*` 能力）已于 2026-09-11 整体删除，该命名会让调用方以为存在镜像同步。
   * 保留提示本身是因为它表达的是"谁执行"这一仍然有效的事实，与已删除的投影机制无关。
   */
  private String executionNotice;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getValidationMessage() {
    return validationMessage;
  }

  public void setValidationMessage(String validationMessage) {
    this.validationMessage = validationMessage;
  }

  public String getAdapterId() {
    return adapterId;
  }

  public void setAdapterId(String adapterId) {
    this.adapterId = adapterId;
  }

  public String getAdapterDisplayName() {
    return adapterDisplayName;
  }

  public void setAdapterDisplayName(String adapterDisplayName) {
    this.adapterDisplayName = adapterDisplayName;
  }

  public String getExternalDefinitionId() {
    return externalDefinitionId;
  }

  public void setExternalDefinitionId(String externalDefinitionId) {
    this.externalDefinitionId = externalDefinitionId;
  }

  public String getExternalDefinitionVersion() {
    return externalDefinitionVersion;
  }

  public void setExternalDefinitionVersion(String externalDefinitionVersion) {
    this.externalDefinitionVersion = externalDefinitionVersion;
  }

  public String getExternalDefinitionSchemaHash() {
    return externalDefinitionSchemaHash;
  }

  public void setExternalDefinitionSchemaHash(String externalDefinitionSchemaHash) {
    this.externalDefinitionSchemaHash = externalDefinitionSchemaHash;
  }

  public String getExecutionNotice() {
    return executionNotice;
  }

  public void setExecutionNotice(String executionNotice) {
    this.executionNotice = executionNotice;
  }
}
