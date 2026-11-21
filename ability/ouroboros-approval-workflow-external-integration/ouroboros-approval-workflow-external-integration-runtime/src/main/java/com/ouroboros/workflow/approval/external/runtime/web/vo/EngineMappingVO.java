package com.ouroboros.workflow.approval.external.runtime.web.vo;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;

/**
 * 流程映射管理视图对象（设计文档 §流程映射管理字段清单；不含任何凭据）。
 */
public class EngineMappingVO {

  private String id;
  private String scope;
  private String flowKey;
  private String adapterId;
  private String connectionId;
  private String externalDefinitionId;
  private String externalDefinitionVersion;
  private String externalDefinitionSchemaHash;
  private LocalDateTime externalDefinitionValidatedAt;
  private String authority;
  /** 派生值（只读）：由适配器端口能力决定，不是可提交字段。 */
  private String integrationMode;
  private String resultBindingJson;
  /** 发起需求声明（Phase 4 M22；透传 startOptionsJson，结构见 ExternalStartRequirementsParser javadoc）。 */
  private String startOptionsJson;
  private String status;
  private Integer version;
  private String actionMappingsJson;
  private String resultMappingsJson;
  private String callbackJson;
  private String validationMessage;
  private String createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static EngineMappingVO fromEntity(
      com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping mapping,
      com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry registry) {
    EngineMappingVO vo = fromEntity(mapping);
    if (registry != null && mapping != null && mapping.getAdapterId() != null) {
      vo.setIntegrationMode(registry.integrationModeOf(mapping.getAdapterId()));
    }
    return vo;
  }

  public static EngineMappingVO fromEntity(WorkflowEngineMapping mapping) {
    EngineMappingVO vo = new EngineMappingVO();
    vo.setId(mapping.getId());
    vo.setScope(mapping.getScope());
    vo.setFlowKey(mapping.getFlowKey());
    vo.setAdapterId(mapping.getAdapterId());
    vo.setConnectionId(mapping.getConnectionId());
    vo.setExternalDefinitionId(mapping.getExternalDefinitionId());
    vo.setExternalDefinitionVersion(mapping.getExternalDefinitionVersion());
    vo.setExternalDefinitionSchemaHash(mapping.getExternalDefinitionSchemaHash());
    vo.setExternalDefinitionValidatedAt(mapping.getExternalDefinitionValidatedAt());
    vo.setAuthority(mapping.getAuthority());
    vo.setResultBindingJson(mapping.getResultBindingJson());
    vo.setStartOptionsJson(mapping.getStartOptionsJson());
    vo.setStatus(mapping.getStatus());
    vo.setVersion(mapping.getVersion());
    vo.setActionMappingsJson(mapping.getActionMappingsJson());
    vo.setResultMappingsJson(mapping.getResultMappingsJson());
    vo.setCallbackJson(mapping.getCallbackJson());
    vo.setValidationMessage(mapping.getValidationMessage());
    vo.setCreatedBy(mapping.getCreatedBy());
    vo.setCreatedAt(mapping.getCreatedAt());
    vo.setUpdatedAt(mapping.getUpdatedAt());
    return vo;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getAdapterId() {
    return adapterId;
  }

  public void setAdapterId(String adapterId) {
    this.adapterId = adapterId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
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

  public LocalDateTime getExternalDefinitionValidatedAt() {
    return externalDefinitionValidatedAt;
  }

  public void setExternalDefinitionValidatedAt(LocalDateTime externalDefinitionValidatedAt) {
    this.externalDefinitionValidatedAt = externalDefinitionValidatedAt;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public String getIntegrationMode() {
    return integrationMode;
  }

  public void setIntegrationMode(String integrationMode) {
    this.integrationMode = integrationMode;
  }


  public String getStartOptionsJson() {
    return startOptionsJson;
  }

  public void setStartOptionsJson(String startOptionsJson) {
    this.startOptionsJson = startOptionsJson;
  }

  public String getResultBindingJson() {
    return resultBindingJson;
  }

  public void setResultBindingJson(String resultBindingJson) {
    this.resultBindingJson = resultBindingJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getActionMappingsJson() {
    return actionMappingsJson;
  }

  public void setActionMappingsJson(String actionMappingsJson) {
    this.actionMappingsJson = actionMappingsJson;
  }

  public String getResultMappingsJson() {
    return resultMappingsJson;
  }

  public void setResultMappingsJson(String resultMappingsJson) {
    this.resultMappingsJson = resultMappingsJson;
  }

  public String getCallbackJson() {
    return callbackJson;
  }

  public void setCallbackJson(String callbackJson) {
    this.callbackJson = callbackJson;
  }

  public String getValidationMessage() {
    return validationMessage;
  }

  public void setValidationMessage(String validationMessage) {
    this.validationMessage = validationMessage;
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

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
