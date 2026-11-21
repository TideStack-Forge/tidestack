package com.ouroboros.workflow.approval.manage.entity;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_approval_workflow_model", label = "审批流程模型")
public class WorkflowModel {
  public static final String SOURCE_INSTANCE = "instance";
  public static final String SOURCE_METADATA_LAYER = "metadata-layer";
  public static final String STATUS_DRAFT = "draft";
  public static final String STATUS_DEPLOYED = "deployed";
  public static final String STATUS_UPDATED = "updated";
  public static final String STATUS_OFFLINE = "offline";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String name;

  @Field
  private String status;

  @Field
  private String icon;

  @Field
  private String category;

  @Field(rawType = "longtext")
  private String json;

  @Field
  private String creator;

  @Field
  private LocalDateTime saveAt;

  @Field(nullable = true)
  private String processDefinitionId;

  @Field
  private String description;

  @Field
  private String source = SOURCE_INSTANCE;

  @Field
  private Integer version;

  @Field
  private Boolean adopted = false;

  /** 运行时执行模式：BUILT_IN / EXTERNAL。 */
  @Field(nullable = true)
  private String executionMode;

  /** 外部集成模式：RESULT_ONLY / FULL_PROCESS；镜像时为空。 */
  @Field(nullable = true)
  private String integrationMode;

  @Field(nullable = true)
  private String engineAdapterId;

  @Field(nullable = true)
  private String engineMappingId;

  @Field(nullable = true)
  private Integer engineMappingVersion;

  @Field(nullable = true)
  private String externalDefinitionRef;

  public WorkflowModel() {
  }

  public WorkflowModel(String id, String name, String status, String icon, String category, String json, String creator, LocalDateTime saveAt, String processDefinitionId, String description) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.icon = icon;
    this.category = category;
    this.json = json;
    this.creator = creator;
    this.saveAt = saveAt;
    this.processDefinitionId = processDefinitionId;
    this.description = description;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getJson() {
    return json;
  }

  public void setJson(String json) {
    this.json = json;
  }

  public LocalDateTime getSaveAt() {
    return saveAt;
  }

  public void setSaveAt(LocalDateTime saveAt) {
    this.saveAt = saveAt;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSource() {
    return source == null ? SOURCE_INSTANCE : source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Boolean getAdopted() {
    return adopted == null ? false : adopted;
  }

  public void setAdopted(Boolean adopted) {
    this.adopted = adopted;
  }

  public String getExecutionMode() {
    return executionMode;
  }

  public void setExecutionMode(String executionMode) {
    this.executionMode = executionMode;
  }

  public String getIntegrationMode() {
    return integrationMode;
  }

  public void setIntegrationMode(String integrationMode) {
    this.integrationMode = integrationMode;
  }

  public String getEngineAdapterId() {
    return engineAdapterId;
  }

  public void setEngineAdapterId(String engineAdapterId) {
    this.engineAdapterId = engineAdapterId;
  }

  public String getEngineMappingId() {
    return engineMappingId;
  }

  public void setEngineMappingId(String engineMappingId) {
    this.engineMappingId = engineMappingId;
  }

  public Integer getEngineMappingVersion() {
    return engineMappingVersion;
  }

  public void setEngineMappingVersion(Integer engineMappingVersion) {
    this.engineMappingVersion = engineMappingVersion;
  }

  public String getExternalDefinitionRef() {
    return externalDefinitionRef;
  }

  public void setExternalDefinitionRef(String externalDefinitionRef) {
    this.externalDefinitionRef = externalDefinitionRef;
  }
}
