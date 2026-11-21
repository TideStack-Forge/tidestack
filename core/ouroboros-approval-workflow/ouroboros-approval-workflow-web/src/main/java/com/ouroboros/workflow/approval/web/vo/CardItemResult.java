package com.ouroboros.workflow.approval.web.vo;

public class CardItemResult {
  private String id;
  private String category;
  private String name;
  private String description;
  private String icon;
  private String processDefinitionId;

  public CardItemResult() {
  }

  public CardItemResult(String id, String category, String name, String description, String icon, String processDefinitionId) {
    this.id = id;
    this.category = category;
    this.name = name;
    this.description = description;
    this.icon = icon;
    this.processDefinitionId = processDefinitionId;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }
}
