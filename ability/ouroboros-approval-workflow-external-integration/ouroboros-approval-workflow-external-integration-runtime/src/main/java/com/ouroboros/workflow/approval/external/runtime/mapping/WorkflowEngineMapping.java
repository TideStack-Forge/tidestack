package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 外部流程引擎映射聚合根：把一个系统流程标识绑定到一个外部流程定义，并声明动作、结果和执行模式。
 *
 * <p>流程真相源只用 {@code authority}（EXTERNAL_ENGINE / OUROBOROS）表达；没有 integrationDirection 字段。
 * 编辑 ACTIVE 映射生成新草稿版本，激活后新流程使用新版本，运行中的外部实例仍绑定旧版本。
 */
@Model(rawName = "t_approval_workflow_engine_mapping", label = "审批流外部引擎映射")
@UniqueConstraint(name = "uk_awf_engine_mapping_active_route", fields = {"activeRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class WorkflowEngineMapping {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_VALIDATING = "VALIDATING";
  public static final String STATUS_VALIDATED = "VALIDATED";
  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_PAUSED = "PAUSED";
  public static final String STATUS_RETIRED = "RETIRED";
  public static final String STATUS_INVALID = "INVALID";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String scope;

  @Field
  private String flowKey;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  /** 外部定义命名空间（租户/企业标识，可空）。 */
  @Field
  private String externalDefinitionNamespace;

  @Field
  private String externalDefinitionId;

  @Field
  private String externalDefinitionVersion;

  @Field
  private String externalDefinitionSchemaHash;

  @Field(nullable = true)
  private LocalDateTime externalDefinitionValidatedAt;

  @Field
  private String authority;

  // 接入模式不再持久化：它由适配器实际具备的端口能力派生（WorkflowEngineAdapterRegistry#integrationModeOf）。
  // executionMode 亦已删除：全仓无运行时消费者，属纯配置噪音。

  @Field(rawType = "longtext")
  private String resultBindingJson;

  /** 发起需求声明（Phase 4 M22：JSON，结构见 ExternalStartRequirementsParser javadoc；可空）。 */
  @Field(rawType = "longtext", nullable = true)
  private String startOptionsJson;

  @Field
  private String status;

  /** ACTIVE 映射唯一事实键：ACTIVE 占用 scope + flowKey，其余状态使用条目私有键释放业务唯一占用。 */
  @Field(size = 96)
  private String activeRouteKey;

  /** 映射版本；同一映射编辑生成新版本草稿。 */
  @Field
  private Integer version;

  /**
   * 绑定引用保护 epoch。cleanup 与 binding create 通过该字段的 CAS 更新取得同一
   * mapping 行锁，避免在“检查无引用”与删除之间插入迟到的 binding。
   */
  @Field(nullable = true)
  private Long bindingReferenceEpoch;

  @Field(rawType = "longtext")
  private String actionMappingsJson;

  @Field(rawType = "longtext")
  private String resultMappingsJson;

  @Field(rawType = "longtext")
  private String callbackJson;

  @Field(rawType = "longtext")
  private String validationMessage;

  @Field
  private String createdBy;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public WorkflowEngineMapping() {
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

  public String getExternalDefinitionNamespace() {
    return externalDefinitionNamespace;
  }

  public void setExternalDefinitionNamespace(String externalDefinitionNamespace) {
    this.externalDefinitionNamespace = externalDefinitionNamespace;
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

  public String getResultBindingJson() {
    return resultBindingJson;
  }

  public void setResultBindingJson(String resultBindingJson) {
    this.resultBindingJson = resultBindingJson;
  }

  public String getStartOptionsJson() {
    return startOptionsJson;
  }

  public void setStartOptionsJson(String startOptionsJson) {
    this.startOptionsJson = startOptionsJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getActiveRouteKey() {
    return activeRouteKey;
  }

  public void setActiveRouteKey(String activeRouteKey) {
    this.activeRouteKey = activeRouteKey;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Long getBindingReferenceEpoch() {
    return bindingReferenceEpoch;
  }

  public void setBindingReferenceEpoch(Long bindingReferenceEpoch) {
    this.bindingReferenceEpoch = bindingReferenceEpoch;
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
