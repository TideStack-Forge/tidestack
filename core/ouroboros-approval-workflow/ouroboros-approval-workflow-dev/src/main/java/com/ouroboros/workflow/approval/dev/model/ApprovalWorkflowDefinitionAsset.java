package com.ouroboros.workflow.approval.dev.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata.FormBinding;

public class ApprovalWorkflowDefinitionAsset {
  private String name;
  private String title;
  private String description = "";
  private Integer version;
  private Boolean enabled = true;
  private FormBinding form = new FormBinding();
  private JsonNode flowDefinition;

  public String getId() {
    return name;
  }

  public void setId(String id) {
    this.name = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getCategory() {
    return "";
  }

  public void setCategory(String category) {
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public FormBinding getForm() {
    return form;
  }

  public void setForm(FormBinding form) {
    this.form = form == null ? new FormBinding() : form;
  }

  public JsonNode getFlowDefinition() {
    return flowDefinition;
  }

  public void setFlowDefinition(JsonNode flowDefinition) {
    this.flowDefinition = flowDefinition;
  }

  public String getFlowKey() {
    return name;
  }
}
