package com.ouroboros.workflow.approval.web.vo;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

public class WorkflowModelResult extends ApprovalWorkflowDefinitionMetadata {
  private String category;
  private String icon;
  private String status;
  private String statusName;
  private String processDefinitionId;
  private String source;
  private Boolean adopted;
  private Boolean canReset;
  private String creator;
  private LocalDateTime saveAt;

  public WorkflowModelResult(WorkflowModel model, ApprovalWorkflowDefinitionMetadata definition) {
    if (definition == null) {
      copyModelSummary(model);
    } else {
      copyDefinition(definition);
    }
    this.category = model.getCategory();
    this.icon = model.getIcon();
    this.status = model.getStatus();
    this.statusName = statusName(model.getStatus());
    this.processDefinitionId = model.getProcessDefinitionId();
    this.source = model.getSource();
    this.adopted = model.getAdopted();
    this.canReset = WorkflowModel.SOURCE_METADATA_LAYER.equals(model.getSource()) && Boolean.TRUE.equals(model.getAdopted());
    this.creator = model.getCreator();
    this.saveAt = model.getSaveAt();
  }

  private void copyModelSummary(WorkflowModel model) {
    if (model == null) {
      return;
    }
    setFormatVersion(ApprovalWorkflowDefinitionMetadata.FORMAT_VERSION);
    setName(model.getId());
    setTitle(model.getName());
    setDescription(model.getDescription());
    setVersion(model.getVersion());
  }

  private void copyDefinition(ApprovalWorkflowDefinitionMetadata definition) {
    if (definition == null) {
      return;
    }
    setFormatVersion(definition.getFormatVersion());
    setName(definition.getName());
    setTitle(definition.getTitle());
    setDescription(definition.getDescription());
    setVersion(definition.getVersion());
    setEnabled(definition.getEnabled());
    setForm(definition.getForm());
    setNotification(definition.getNotification());
    setEditorLayout(definition.getEditorLayout());
    setNodes(definition.getNodes());
    setEdges(definition.getEdges());
  }

  private String statusName(String value) {
    if (WorkflowModel.STATUS_DRAFT.equals(value)) {
      return "草稿";
    }
    if (WorkflowModel.STATUS_DEPLOYED.equals(value)) {
      return "已部署";
    }
    if (WorkflowModel.STATUS_UPDATED.equals(value)) {
      return "已更新";
    }
    if (WorkflowModel.STATUS_OFFLINE.equals(value)) {
      return "已下线";
    }
    return "未知";
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatusName() {
    return statusName;
  }

  public void setStatusName(String statusName) {
    this.statusName = statusName;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Boolean getAdopted() {
    return adopted;
  }

  public void setAdopted(Boolean adopted) {
    this.adopted = adopted;
  }

  public Boolean getCanReset() {
    return canReset;
  }

  public void setCanReset(Boolean canReset) {
    this.canReset = canReset;
  }

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public LocalDateTime getSaveAt() {
    return saveAt;
  }

  public void setSaveAt(LocalDateTime saveAt) {
    this.saveAt = saveAt;
  }
}
